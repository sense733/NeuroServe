package com.neuroserve.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** PSS 内存信息 (包含 Native Heap - 模型加载后的真实占用) */
    data class AppMemoryInfo(
        val pssKb: Long,
        val nativeHeapKb: Long
    ) {
        val pssMb: Float get() = pssKb / 1024f
        val pssGb: Float get() = pssKb / (1024f * 1024f)
        
        val displayText: String
            get() = when {
                pssKb >= 1024 * 1024 -> "%.1f GB".format(pssGb)
                pssKb >= 1024 -> "%.0f MB".format(pssMb)
                else -> "$pssKb KB"
            }
    }

    private val _appMemoryInfo = MutableStateFlow(AppMemoryInfo(0, 0))
    val appMemoryInfo: StateFlow<AppMemoryInfo> = _appMemoryInfo.asStateFlow()

    private val _batteryTemperature = MutableStateFlow(0f)
    val batteryTemperature: StateFlow<Float> = _batteryTemperature.asStateFlow()

    private var batteryReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        registerBatteryReceiver()
        startMemoryMonitoring()
    }

    private fun startMemoryMonitoring() {
        scope.launch {
            while (true) {
                refreshAppMemoryInfo()
                delay(2000)
            }
        }
    }

    /** Debug.getMemoryInfo() 是较重操作，不要高频调用 */
    private fun refreshAppMemoryInfo() {
        try {
            val memoryInfo = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(memoryInfo)
            
            _appMemoryInfo.value = AppMemoryInfo(
                pssKb = memoryInfo.totalPss.toLong(),
                nativeHeapKb = memoryInfo.nativePss.toLong()
            )
        } catch (e: Exception) {
            android.util.Log.e("SystemMonitor", "Failed to get app memory info", e)
        }
    }

    private fun registerBatteryReceiver() {
        if (isReceiverRegistered) return

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    _batteryTemperature.value = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                }
            }
        }

        try {
            context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            isReceiverRegistered = true
        } catch (e: Exception) {
            android.util.Log.e("SystemMonitor", "Failed to register battery receiver", e)
        }
    }
}
