package com.neuroserve.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "neuroserve_settings")

data class SettingsData(
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
    val keepCpuAwake: Boolean = false
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    val settingsFlow: Flow<SettingsData> = dataStore.data.map { prefs ->
        SettingsData(
            serverName = prefs[SettingsKeys.SERVER_NAME] ?: "NeuroServe-Home",
            language = prefs[SettingsKeys.LANGUAGE] ?: "system",
            httpPort = prefs[SettingsKeys.HTTP_PORT] ?: 8080,
            allowLanAccess = prefs[SettingsKeys.ALLOW_LAN_ACCESS] ?: false,
            apiAuthEnabled = prefs[SettingsKeys.API_AUTH_ENABLED] ?: false,
            apiKey = prefs[SettingsKeys.API_KEY] ?: "",
            hardwareAccel = prefs[SettingsKeys.HARDWARE_ACCEL]?.let { 
                runCatching { HardwareAccel.valueOf(it) }.getOrDefault(HardwareAccel.NPU) 
            } ?: HardwareAccel.NPU,
            cpuThreads = prefs[SettingsKeys.CPU_THREADS] ?: 4,
            contextWindow = prefs[SettingsKeys.CONTEXT_WINDOW] ?: 4096,
            defaultModelPath = prefs[SettingsKeys.DEFAULT_MODEL_PATH],
            temperature = prefs[SettingsKeys.TEMPERATURE] ?: 0.8f,
            topK = prefs[SettingsKeys.TOP_K] ?: 40,
            keepCpuAwake = prefs[SettingsKeys.KEEP_CPU_AWAKE] ?: false
        )
    }

    suspend fun setServerName(name: String) = dataStore.edit { it[SettingsKeys.SERVER_NAME] = name }
    suspend fun setLanguage(lang: String) = dataStore.edit { it[SettingsKeys.LANGUAGE] = lang }

    suspend fun setHttpPort(port: Int) = dataStore.edit { it[SettingsKeys.HTTP_PORT] = port }
    suspend fun setAllowLanAccess(enabled: Boolean) = dataStore.edit { it[SettingsKeys.ALLOW_LAN_ACCESS] = enabled }
    suspend fun setApiAuthEnabled(enabled: Boolean) = dataStore.edit { it[SettingsKeys.API_AUTH_ENABLED] = enabled }
    suspend fun setApiKey(key: String) = dataStore.edit { it[SettingsKeys.API_KEY] = key }

    suspend fun setHardwareAccel(accel: HardwareAccel) = dataStore.edit { it[SettingsKeys.HARDWARE_ACCEL] = accel.name }
    suspend fun setCpuThreads(threads: Int) = dataStore.edit { it[SettingsKeys.CPU_THREADS] = threads }
    suspend fun setContextWindow(size: Int) = dataStore.edit { it[SettingsKeys.CONTEXT_WINDOW] = size }
    suspend fun setDefaultModelPath(path: String?) = dataStore.edit { 
        if (path != null) it[SettingsKeys.DEFAULT_MODEL_PATH] = path 
        else it.remove(SettingsKeys.DEFAULT_MODEL_PATH)
    }

    suspend fun setTemperature(temp: Float) = dataStore.edit { it[SettingsKeys.TEMPERATURE] = temp }
    suspend fun setTopK(k: Int) = dataStore.edit { it[SettingsKeys.TOP_K] = k }

    suspend fun setKeepCpuAwake(enabled: Boolean) = dataStore.edit { it[SettingsKeys.KEEP_CPU_AWAKE] = enabled }

    companion object {
        private val secureRandom = SecureRandom()
        private const val API_KEY_LENGTH = 32
        private val CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        fun generateApiKey(): String {
            val sb = StringBuilder("sk-")
            repeat(API_KEY_LENGTH) {
                sb.append(CHARSET[secureRandom.nextInt(CHARSET.length)])
            }
            return sb.toString()
        }
    }
}
