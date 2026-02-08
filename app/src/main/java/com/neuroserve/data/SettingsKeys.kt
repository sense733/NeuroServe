package com.neuroserve.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    // General
    val SERVER_NAME = stringPreferencesKey("server_name")
    val LANGUAGE = stringPreferencesKey("language")

    // Network
    val HTTP_PORT = intPreferencesKey("http_port")
    val ALLOW_LAN_ACCESS = booleanPreferencesKey("allow_lan_access")
    val API_AUTH_ENABLED = booleanPreferencesKey("api_auth_enabled")
    val API_KEY = stringPreferencesKey("api_key")

    // Inference
    val HARDWARE_ACCEL = stringPreferencesKey("hardware_accel")
    val CPU_THREADS = intPreferencesKey("cpu_threads")
    val CONTEXT_WINDOW = intPreferencesKey("context_window")
    val DEFAULT_MODEL_PATH = stringPreferencesKey("default_model_path")

    // System
    val KEEP_CPU_AWAKE = booleanPreferencesKey("keep_cpu_awake")
}

enum class HardwareAccel { CPU, GPU, NPU }
