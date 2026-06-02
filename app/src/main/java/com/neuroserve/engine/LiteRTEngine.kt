package com.neuroserve.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton
import com.neuroserve.server.dto.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LiteRtEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: com.neuroserve.data.SettingsRepository
) : InferenceEngine, Closeable {

    override val name: String = "LiteRT-LM"
    override val priority: Int = 0

    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    private var activeHistory: List<ChatMessage> = emptyList()
    private val engineMutex = Mutex()
    @Volatile private var isModelLoaded = false
    private var currentMeta: ModelMeta? = null
    private val stopRequested = AtomicBoolean(false)
    private var generationJob: Job? = null

    companion object {
        private const val TAG = "LiteRtEngine"
    }

    override suspend fun isAvailable(): Boolean = true

    override suspend fun loadModel(meta: ModelMeta): Unit = withContext(Dispatchers.IO) {
        if (isModelLoaded) {
            Log.w(TAG, "Model already loaded, unloading first")
            unloadModel()
        }

        Log.i(TAG, "Loading LiteRT-LM model: ${meta.displayName}")
        Log.i(TAG, "Model path: ${meta.modelPath}")

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        Log.i(TAG, "Native lib dir: $nativeLibDir")

        val settings = settingsRepository.settingsFlow.first()
        currentAccelerator = when (settings.hardwareAccel) {
            com.neuroserve.data.HardwareAccel.NPU -> AcceleratorType.NPU
            com.neuroserve.data.HardwareAccel.GPU -> AcceleratorType.GPU
            com.neuroserve.data.HardwareAccel.CPU -> AcceleratorType.CPU
        }
        val engineBackend = when (settings.hardwareAccel) {
            com.neuroserve.data.HardwareAccel.NPU -> {
                try {
                    // We do not force load QNN libraries via System.loadLibrary anymore.
                    // The LiteRtDispatch_Qualcomm.so will dlopen them from the nativeLibraryDir automatically.
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set up NPU environment or load QNN libraries", e)
                }
                Backend.NPU(nativeLibraryDir = nativeLibDir)
            }
            com.neuroserve.data.HardwareAccel.GPU -> Backend.GPU()
            com.neuroserve.data.HardwareAccel.CPU -> Backend.CPU()
        }

        val config = EngineConfig(
            modelPath = meta.modelPath,
            backend = engineBackend
        )

        val newEngine = Engine(config)
        newEngine.initialize()

        engine = newEngine
        isModelLoaded = true
        currentMeta = meta
        Log.i(TAG, "Model loaded: ${meta.displayName}")
    }

    override suspend fun unloadModel(): Unit = withContext(Dispatchers.IO) {
        performUnload()
    }

    private fun performUnload() {
        try {
            activeConversation?.close()
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LiteRT-LM engine", e)
        } finally {
            activeConversation = null
            engine = null
            isModelLoaded = false
            currentMeta = null
            activeHistory = emptyList()
        }
    }

    override suspend fun generate(messages: List<ChatMessage>, config: InferenceConfig?): Flow<String> {
        val eng = engine
            ?: throw IllegalStateException("Model not loaded")

        stopRequested.set(false)

        if (config != null) {
            Log.d(TAG, "LiteRT-LM backend does not support dynamic inference parameters; config ignored")
        }

        return flow {
            engineMutex.withLock {
                val isContinuation = messages.size > 1 && 
                    messages.subList(0, messages.size - 1) == activeHistory
                
                val promptToSend = if (isContinuation) {
                    messages.last().content
                } else {
                    activeConversation?.close()
                    activeConversation = eng.createConversation()
                    buildPromptFromMessages(messages)
                }
                
                val conv = activeConversation ?: throw IllegalStateException("Conversation is null")
                val assistantReply = StringBuilder()
                
                conv.sendMessageAsync(promptToSend).collect { token ->
                    if (stopRequested.get()) return@collect
                    val tokenStr = token.toString()
                    assistantReply.append(tokenStr)
                    emit(tokenStr)
                }
                
                activeHistory = messages + ChatMessage(role = "assistant", content = assistantReply.toString())
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun buildPromptFromMessages(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            sb.append("<|im_start|>").append(msg.role).append("\n")
            sb.append(msg.content).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    override fun stopGeneration() {
        stopRequested.set(true)
    }

    override fun isLoaded(): Boolean = isModelLoaded

    private var currentAccelerator: AcceleratorType = AcceleratorType.GPU

    override fun getCurrentAccelerator(): AcceleratorType = currentAccelerator

    override fun getCurrentModelMeta(): ModelMeta? = currentMeta

    override fun close() {
        runBlocking(Dispatchers.IO) {
            performUnload()
        }
    }
}
