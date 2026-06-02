package com.neuroserve.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroserve.R
import com.neuroserve.data.SettingsRepository
import com.neuroserve.engine.EngineManager
import com.neuroserve.engine.ModelFormat
import com.neuroserve.engine.ModelMeta
import com.neuroserve.engine.discoverModels
import com.neuroserve.server.ApiServer
import com.neuroserve.utils.SystemMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val apiServer: ApiServer,
    private val settingsRepository: SettingsRepository,
    val systemMonitor: SystemMonitor,
    private val engineManager: EngineManager
) : ViewModel() {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _uptime = MutableStateFlow("00:00:00")
    val uptime: StateFlow<String> = _uptime.asStateFlow()
    
    private val _lanIp = MutableStateFlow("N/A")
    val lanIp: StateFlow<String> = _lanIp.asStateFlow()
    
    val activeModelName: StateFlow<String?> = engineManager.activeModelName
    val inferenceSpeed: StateFlow<String> = engineManager.inferenceSpeed
    val activeClients: StateFlow<Int> = apiServer.activeClients
    
    private var serviceStartTime: Long = 0L

    init {
        updateServiceStatus()
        
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            settings.defaultModelPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val searchDir = if (file.isDirectory) file.parentFile ?: file else file.parentFile ?: file
                    val discovered = discoverModels(searchDir)
                    val meta = discovered.find { it.modelPath == file.absolutePath }
                        ?: ModelMeta(
                            format = ModelFormat.GGUF,
                            modelPath = file.absolutePath,
                            modelName = file.nameWithoutExtension,
                            modelType = "llm",
                            pluginId = "cpu_gpu",
                            displayName = file.name,
                            sizeBytes = file.length()
                        )
                    engineManager.loadModel(meta)
                }
            }

            launch {
                while (true) {
                    if (_isServiceRunning.value && serviceStartTime > 0) {
                        val elapsed = (System.currentTimeMillis() - serviceStartTime) / 1000
                        val hours = elapsed / 3600
                        val minutes = (elapsed % 3600) / 60
                        val seconds = elapsed % 60
                        _uptime.value = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    } else if (!_isServiceRunning.value) {
                        _uptime.value = "--:--:--"
                    }
                    delay(1000)
                }
            }
        }
    }
    

    
    fun toggleService(enable: Boolean) {
        _isServiceRunning.value = enable
        if (enable) {
            serviceStartTime = System.currentTimeMillis()
        } else {
            serviceStartTime = 0L
            _uptime.value = "--:--:--"
        }
    }
    
    fun updateServiceStatus() {
        _isServiceRunning.value = apiServer.isRunning()
    }
    
    fun updateLanIp(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = getDeviceIpAddress(context)
            _lanIp.value = ip ?: context.getString(R.string.initial_ip)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun getDeviceIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "WifiManager IP failed", e)
        }
        
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement()
                if (netInterface.isLoopback || !netInterface.isUp) continue
                
                val addresses = netInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "NetworkInterface IP failed", e)
        }
        return null
    }
}
