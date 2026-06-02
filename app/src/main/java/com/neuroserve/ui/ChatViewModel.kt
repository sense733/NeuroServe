package com.neuroserve.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroserve.engine.EngineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import com.neuroserve.server.dto.ChatMessage

@Serializable
data class UiMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val isStreaming: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val engineManager: EngineManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _messages = mutableStateListOf<UiMessage>()
    val messages: List<UiMessage> = _messages

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val isModelLoaded: StateFlow<Boolean> = engineManager.isModelLoaded

    private val historyFile = File(context.filesDir, "chat_history.json")

    init {
        if (historyFile.exists()) {
            try {
                val jsonStr = historyFile.readText()
                val history = Json.decodeFromString<List<UiMessage>>(jsonStr)
                _messages.addAll(history.map { it.copy(isStreaming = false) })
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    private fun saveHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonStr = Json.encodeToString(_messages.toList())
                historyFile.writeText(jsonStr)
            } catch (e: Exception) {
                // Ignore save errors
            }
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val prompt = _inputText.value.trim()
        if (prompt.isEmpty() || _isGenerating.value || !isModelLoaded.value) return

        _messages.add(UiMessage(role = "user", content = prompt))
        _inputText.value = ""
        _isGenerating.value = true

        viewModelScope.launch {
            val assistantMsgIndex = _messages.size
            _messages.add(UiMessage(role = "assistant", content = "", isStreaming = true))

            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            val fullContent = StringBuilder()

            val engine = engineManager.getActiveEngine()
            if (engine == null) {
                updateMessageContent(assistantMsgIndex, "Error: Engine not loaded")
                _isGenerating.value = false
                _messages[assistantMsgIndex] = _messages[assistantMsgIndex].copy(isStreaming = false)
                saveHistory()
                return@launch
            }

            val chatMessages = _messages.subList(0, assistantMsgIndex).map {
                ChatMessage(role = it.role, content = it.content)
            }

            engine.generate(chatMessages)
                .onStart { }
                .catch { e ->
                    updateMessageContent(assistantMsgIndex, "Error: ${e.message}")
                    _messages[assistantMsgIndex] = _messages[assistantMsgIndex].copy(isStreaming = false)
                    saveHistory()
                }
                .onCompletion {
                    val duration = (System.currentTimeMillis() - startTime) / 1000f
                    if (duration > 0) {
                        val tps = tokenCount / duration
                        engineManager.updateInferenceSpeed(String.format("%.1f", tps))
                    }
                    _isGenerating.value = false
                    if (assistantMsgIndex < _messages.size) {
                        _messages[assistantMsgIndex] = _messages[assistantMsgIndex].copy(isStreaming = false)
                    }
                    saveHistory()
                }
                .collect { token ->
                    tokenCount++
                    fullContent.append(token)
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
