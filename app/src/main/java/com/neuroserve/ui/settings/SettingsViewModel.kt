package com.neuroserve.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroserve.data.HardwareAccel
import com.neuroserve.data.SettingsData
import com.neuroserve.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    // General
    val serverName: String = "NeuroServe-Home",
    val language: String = "system",
    // Network
    val httpPort: Int = 8080,
    val allowLanAccess: Boolean = false,
    val apiAuthEnabled: Boolean = false,
    val apiKey: String = "",
    // Inference
    val hardwareAccel: HardwareAccel = HardwareAccel.NPU,
    val cpuThreads: Int = 4,
    val contextWindow: Int = 4096,
    val defaultModelPath: String? = null,
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    // System
    val keepCpuAwake: Boolean = false,
    // UI State
    val portError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val maxSupportedThreads: Int = Runtime.getRuntime().availableProcessors()

    val uiState: StateFlow<SettingsUiState> = repository.settingsFlow
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        ensureApiKeyExists()
    }



    private fun ensureApiKeyExists() {
        viewModelScope.launch {
            val settings = repository.settingsFlow.first()
            if (settings.apiKey.isEmpty()) {
                repository.setApiKey(SettingsRepository.generateApiKey())
            }
        }
    }

    fun setServerName(name: String) = viewModelScope.launch { repository.setServerName(name) }
    fun setLanguage(lang: String) = viewModelScope.launch { repository.setLanguage(lang) }
    
    suspend fun setLanguageSync(lang: String) = repository.setLanguage(lang)

    fun setHttpPort(port: Int) = viewModelScope.launch {
        if (validatePort(port)) {
            repository.setHttpPort(port)
        }
    }
    
    fun validatePort(port: Int): Boolean = port in 1024..65535

    fun toggleLanAccess(enabled: Boolean) = viewModelScope.launch {
        repository.setAllowLanAccess(enabled)
    }

    fun toggleApiAuth(enabled: Boolean) = viewModelScope.launch {
        repository.setApiAuthEnabled(enabled)
    }

    fun regenerateApiKey() = viewModelScope.launch {
        repository.setApiKey(SettingsRepository.generateApiKey())
    }

    fun setHardwareAccel(accel: HardwareAccel) = viewModelScope.launch {
        repository.setHardwareAccel(accel)
    }

    fun setCpuThreads(threads: Int) = viewModelScope.launch {
        val clamped = threads.coerceIn(1, maxSupportedThreads)
        repository.setCpuThreads(clamped)
    }

    fun setContextWindow(size: Int) = viewModelScope.launch {
        repository.setContextWindow(size)
    }

    fun setDefaultModelPath(path: String?) = viewModelScope.launch {
        repository.setDefaultModelPath(path)
    }

    fun setTemperature(temp: Float) = viewModelScope.launch {
        repository.setTemperature(temp)
    }

    fun setTopK(k: Int) = viewModelScope.launch {
        repository.setTopK(k)
    }

    fun toggleKeepCpuAwake(enabled: Boolean) = viewModelScope.launch {
        repository.setKeepCpuAwake(enabled)
    }

    /** 返回请求忽略电池优化的 Intent，如果已忽略或 API 版本过低则返回 null */
    fun requestIgnoreBatteryOptimization(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return null
        
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun SettingsData.toUiState() = SettingsUiState(
        serverName = serverName,
        language = language,
        httpPort = httpPort,
        allowLanAccess = allowLanAccess,
        apiAuthEnabled = apiAuthEnabled,
        apiKey = apiKey,
        hardwareAccel = hardwareAccel,
        cpuThreads = cpuThreads,
        contextWindow = contextWindow,
        defaultModelPath = defaultModelPath,
        temperature = temperature,
        topK = topK,
        keepCpuAwake = keepCpuAwake
    )
}
