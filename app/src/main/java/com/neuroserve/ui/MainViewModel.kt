package com.neuroserve.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroserve.engine.InferenceEngine
import com.neuroserve.server.ApiServer
import com.neuroserve.utils.SystemMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import com.neuroserve.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class UiMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val isStreaming: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val engine: InferenceEngine,
    private val apiServer: ApiServer,
    val systemMonitor: SystemMonitor
) : ViewModel() {

    private val _messages = mutableStateListOf<UiMessage>()
    val messages: List<UiMessage> = _messages

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _inferenceSpeed = MutableStateFlow("")
    val inferenceSpeed: StateFlow<String> = _inferenceSpeed.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    
    private val _uptime = MutableStateFlow("00:00:00")
    val uptime: StateFlow<String> = _uptime.asStateFlow()
    
    private val _lanIp = MutableStateFlow("N/A")
    val lanIp: StateFlow<String> = _lanIp.asStateFlow()
    
    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()
    
    private var serviceStartTime: Long = 0L

    // Model Hub State
    data class ModelInfo(
        val name: String,
        val file: File,
        val size: String
    )
    
    private val _modelList = MutableStateFlow<List<ModelInfo>>(emptyList())
    val modelList: StateFlow<List<ModelInfo>> = _modelList.asStateFlow()

    init {
        // 初始化时检查服务状态
        updateServiceStatus()
        
        viewModelScope.launch {
            // Uptime update loop
            launch {
                while (true) {
                    // Update uptime only when service is running
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
    
    /**
     * 初始化系统监控，需要在 Activity/Composable 中调用
     */
    fun initSystemMonitor(context: Context) {
        systemMonitor.registerBatteryReceiver(context)
        // Start periodic refresh of app memory (every 2 seconds, on IO thread)
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                systemMonitor.refreshAppMemoryInfo()
                delay(2000) // Refresh every 2 seconds
            }
        }
    }
    
    /**
     * 清理系统监控资源
     */
    fun cleanupSystemMonitor(context: Context) {
        systemMonitor.unregisterBatteryReceiver(context)
    }
    
    // ... existing functions ...
    
    fun onInputChange(text: String) {
        _inputText.value = text
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
        // Method 1: Try WifiManager (works on older Android)
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
        
        // Method 2: Fallback to NetworkInterface (works on newer Android)
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

    fun refreshModelList(filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val files = filesDir.listFiles { _, name -> name.endsWith(".tflite") } ?: emptyArray()
            val models = files.map { file ->
                val size = "%.1f MB".format(file.length() / (1024.0 * 1024.0))
                ModelInfo(file.name, file, size)
            }
            _modelList.value = models
        }
    }
    
    fun loadModel(model: ModelInfo) {
        viewModelScope.launch {
            try {
                engine.loadModel(model.file.absolutePath)
                _isModelLoaded.value = true
                _activeModelName.value = model.name
                android.util.Log.i("MainViewModel", "Model loaded: ${model.name}")
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to load model: ${model.name}", e)
            }
        }
    }

    /**
     * 导入模型文件
     */
    fun importModel(context: Context, uri: Uri) {
        if (_isImporting.value) return
        
        viewModelScope.launch {
            _isImporting.value = true
            
            try {
                // Attempt to get filename
                var fileName = "model_${System.currentTimeMillis()}.tflite"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
                
                // Ensure .tflite extension
                if (!fileName.endsWith(".tflite")) {
                    fileName += ".tflite"
                }

                val modelFile = withContext(Dispatchers.IO) {
                    // 目标文件路径
                    val destFile = File(context.filesDir, fileName)
                    
                    // 从 ContentResolver 打开输入流
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw Exception("Cannot open file")
                    
                    android.util.Log.i("MainViewModel", "Model copied to: ${destFile.absolutePath}")
                    destFile
                }
                
                // Refresh list
                refreshModelList(context.filesDir)

                // Load the newly imported model
                engine.loadModel(modelFile.absolutePath)
                _isModelLoaded.value = true
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_import_success, fileName), Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to import model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun sendMessage() {
        val prompt = _inputText.value.trim()
        if (prompt.isEmpty() || _isGenerating.value) return

        // 1. 添加用户消息
        _messages.add(UiMessage(role = "user", content = prompt))
        _inputText.value = ""
        _isGenerating.value = true

        viewModelScope.launch {
            // 2. 添加 Assistant 占位消息
            val assistantMsgIndex = _messages.size
            _messages.add(UiMessage(role = "assistant", content = "", isStreaming = true))

            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            val fullContent = StringBuilder()

            engine.generate(prompt)
                .onStart {
                    // 开始生成
                }
                .catch { e ->
                    // 错误处理
                    updateMessageContent(assistantMsgIndex, "Error: ${e.message}")
                    _messages[assistantMsgIndex] = _messages[assistantMsgIndex].copy(isStreaming = false)
                }
                .onCompletion {
                    val duration = (System.currentTimeMillis() - startTime) / 1000f
                    if (duration > 0) {
                        val tps = tokenCount / duration
                        _inferenceSpeed.value = String.format("%.1f", tps)
                    }
                    _isGenerating.value = false
                    if (assistantMsgIndex < _messages.size) {
                         _messages[assistantMsgIndex] = _messages[assistantMsgIndex].copy(isStreaming = false)
                    }
                }
                .collect { token ->
                    tokenCount++
                    fullContent.append(token)
                    // 实时更新最后一条消息
                    updateMessageContent(assistantMsgIndex, fullContent.toString())
                }
        }
    }

    private fun updateMessageContent(index: Int, content: String) {
        if (index in _messages.indices) {
            val current = _messages[index]
            _messages[index] = current.copy(content = content)
        }
    }
}
