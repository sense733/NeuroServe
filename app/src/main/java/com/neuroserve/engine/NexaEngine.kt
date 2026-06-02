package com.neuroserve.engine

import android.content.Context
import android.util.Log
import com.nexa.sdk.LlmWrapper
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.bean.GenerationConfig
import com.nexa.sdk.bean.LlmCreateInput
import com.nexa.sdk.bean.LlmStreamResult
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import com.neuroserve.server.dto.ChatMessage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

@Singleton
class NexaEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine, Closeable {

    override val name: String = "Nexa-NPU"
    override val priority: Int = 1

    private var llmWrapper: LlmWrapper? = null
    @Volatile private var isModelLoaded = false
    private val stopRequested = AtomicBoolean(false)
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> 
        Log.e(TAG, "Coroutine exception in engine scope", e)
    })

    companion object {
        private const val TAG = "NexaEngine"
    }

    override suspend fun isAvailable(): Boolean = true

    private var currentMeta: ModelMeta? = null

    override suspend fun loadModel(meta: ModelMeta): Unit = withContext(Dispatchers.IO) {
        if (isModelLoaded) {
            Log.w(TAG, "Model already loaded, unloading first")
            unloadModel()
        }

        val initLatch = CountDownLatch(1)
        var initError: String? = null
        NexaSdk.getInstance().init(context, object : NexaSdk.InitCallback {
            override fun onSuccess() {
                Log.i(TAG, "NexaSdk initialized")
                initLatch.countDown()
            }
            override fun onFailure(reason: String) {
                Log.e(TAG, "NexaSdk init failed: $reason")
                initError = reason
                initLatch.countDown()
            }
        })
        val success = initLatch.await(10, TimeUnit.SECONDS)
        if (!success) {
            throw RuntimeException("NexaSdk init failed: timeout after 10 seconds")
        }
        initError?.let { throw RuntimeException("NexaSdk init failed: $it") }

        val config = when (meta.format) {
            ModelFormat.GGUF -> {
                Log.i(TAG, "Initializing GGUF model with GPU acceleration (nGpuLayers = 99)")
                ModelConfig(
                    nCtx = 4096,
                    nGpuLayers = 99
                )
            }
            ModelFormat.NEXA -> {
                Log.i(TAG, "Initializing NEXA model with NPU acceleration")
                ModelConfig(
                    nCtx = 4096,
                    nGpuLayers = 0,
                    npu_lib_folder_path = context.applicationInfo.nativeLibraryDir,
                    npu_model_folder_path = meta.modelPath
                )
            }
            ModelFormat.LITERTLM -> throw IllegalArgumentException(
                "LiteRT-LM models must be loaded via LiteRtEngine"
            )
        }

        val modelFilePath = when (meta.format) {
            ModelFormat.GGUF -> meta.modelPath
            ModelFormat.NEXA -> {
                val dir = java.io.File(meta.modelPath)
                dir.listFiles { _, name -> name.endsWith(".nexa", ignoreCase = true) }
                    ?.firstOrNull()?.absolutePath
                    ?: throw IllegalStateException("No .nexa file found in ${meta.modelPath}")
            }
            ModelFormat.LITERTLM -> throw IllegalArgumentException(
                "LiteRT-LM models must be loaded via LiteRtEngine"
            )
        }

        LlmWrapper.builder()
            .llmCreateInput(
                LlmCreateInput(
                    model_name = meta.modelName,
                    model_path = modelFilePath,
                    tokenizer_path = meta.tokenizerPath,
                    config = config,
                    plugin_id = meta.pluginId
                )
            )
            .build()
            .onSuccess {
                llmWrapper = it
                isModelLoaded = true
                currentMeta = meta
                Log.i(TAG, "Model loaded [${meta.format}]: ${meta.displayName}")
                Log.i(TAG, "Current accelerator: ${getCurrentAccelerator()}")
            }
            .onFailure { e ->
                Log.e(TAG, "Failed to load model", e)
                throw e
            }
    }

    override suspend fun unloadModel(): Unit = withContext(Dispatchers.IO) {
        performUnload()
    }

    private fun performUnload() {
        try {
            llmWrapper?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying LlmWrapper", e)
        } finally {
            llmWrapper = null
            isModelLoaded = false
            currentMeta = null
        }
    }

    override suspend fun generate(messages: List<ChatMessage>, config: InferenceConfig?): Flow<String> {
        val wrapper = llmWrapper
            ?: throw IllegalStateException("Model not loaded")

        stopRequested.set(false)
        val prompt = buildPromptFromMessages(messages)

        return flow {
            val nexaConfig = GenerationConfig().apply {
                config?.maxTokens?.let { maxTokens = it }
                samplerConfig = SamplerConfig().apply {
                    config?.temperature?.let { temperature = it }
                    config?.topK?.let { topK = it }
                    config?.topP?.let { topP = it }
                }
            }
            
            wrapper.generateStreamFlow(prompt, nexaConfig)
                .collect { result ->
                    if (stopRequested.get()) return@collect
                    when (result) {
                        is LlmStreamResult.Token -> emit(result.text)
                        is LlmStreamResult.Completed -> return@collect
                        is LlmStreamResult.Error -> throw result.throwable
                    }
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
        engineScope.launch {
            try {
                llmWrapper?.stopStream()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping stream", e)
            }
        }
    }

    override fun isLoaded(): Boolean = isModelLoaded

    override fun getCurrentAccelerator(): AcceleratorType = when (currentMeta?.format) {
        ModelFormat.NEXA -> AcceleratorType.NPU
        ModelFormat.GGUF -> AcceleratorType.GPU
        else -> AcceleratorType.CPU
    }

    override fun getCurrentModelMeta(): ModelMeta? = currentMeta

    override fun close() {
        runBlocking(Dispatchers.IO) {
            performUnload()
        }
        engineScope.cancel()
    }
}
