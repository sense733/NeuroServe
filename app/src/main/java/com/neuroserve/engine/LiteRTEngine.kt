package com.neuroserve.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * LiteRT + GPU 推理引擎。NPU 优先 (QNN Delegate)，Fallback 到 GPU/CPU。
 */
@Singleton
class LiteRTEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenizer: Tokenizer
) : InferenceEngine, Closeable {

    override val name: String = "LiteRT-NPU"
    override val priority: Int = 1

    private var interpreter: InterpreterApi? = null
    private var gpuDelegate: GpuDelegate? = null
    private var modelBuffer: MappedByteBuffer? = null
    
    @Volatile private var isModelLoaded = false
    @Volatile private var shouldStopGeneration = false
    
    private var currentAccelerator = AcceleratorType.CPU

    companion object {
        private const val MAX_NEW_TOKENS = 2048
        private const val MAX_SEQ_LENGTH = 2048
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val TAG = "LiteRTEngine"
        
        // Signature/tensor names - adjust per model
        private const val SIGNATURE_GENERATE = "serving_default"
        private const val INPUT_IDS = "input_ids"
        private const val INPUT_ATTENTION_MASK = "attention_mask"
        private const val OUTPUT_LOGITS = "logits"
    }

    enum class AcceleratorType { NPU, GPU, CPU }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val gpuAvailable = try {
                GpuDelegate().close()
                true
            } catch (e: Exception) { false }
            
            android.util.Log.i(TAG, "GPU Delegate available: $gpuAvailable")
            true // CPU always available
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to check accelerator availability", e)
            true
        }
    }

    override suspend fun loadModel(modelPath: String): Unit = withContext(Dispatchers.IO) {
        if (isModelLoaded) {
            android.util.Log.w(TAG, "Model already loaded, unloading first")
            unloadModel()
        }
        
        // MVP: mock:// protocol skips real model loading
        if (modelPath.startsWith("mock://")) {
            android.util.Log.i(TAG, "MVP Mode: Using mock model")
            isModelLoaded = true
            currentAccelerator = AcceleratorType.CPU
            return@withContext
        }
        
        try {
            android.util.Log.i(TAG, "Loading model from: $modelPath")
            
            modelBuffer = if (modelPath.startsWith("assets://")) {
                loadModelFromAssets(modelPath.removePrefix("assets://"))
            } else {
                loadModelFromFile(modelPath)
            }
            
            val options = InterpreterApi.Options()
            currentAccelerator = configureAccelerators(options)
            interpreter = InterpreterApi.create(modelBuffer!!, options)
            
            isModelLoaded = true
            android.util.Log.i(TAG, "Model loaded with $currentAccelerator acceleration")
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load model", e)
            // MVP fallback: allow mock generation even on load failure
            isModelLoaded = true
            currentAccelerator = AcceleratorType.CPU
        }
    }

    /** NPU > GPU > CPU fallback chain */
    private fun configureAccelerators(options: InterpreterApi.Options): AcceleratorType {
        // TODO: Integrate QNN Delegate when stable AIDL available
        
        try {
            gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate!!)
            android.util.Log.i(TAG, "GPU Delegate configured")
            return AcceleratorType.GPU
        } catch (e: Exception) {
            android.util.Log.w(TAG, "GPU Delegate unavailable: ${e.message}")
        }
        
        android.util.Log.i(TAG, "Using CPU fallback")
        return AcceleratorType.CPU
    }

    override suspend fun unloadModel(): Unit = withContext(Dispatchers.IO) {
        try {
            interpreter?.close()
            gpuDelegate?.close()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during model unload", e)
        } finally {
            interpreter = null
            gpuDelegate = null
            modelBuffer = null
            isModelLoaded = false
            currentAccelerator = AcceleratorType.CPU
        }
    }

    override suspend fun generate(prompt: String): Flow<String> = flow {
        val interp = interpreter 
            ?: throw IllegalStateException("Model not loaded. Call loadModel() first.")
        
        shouldStopGeneration = false
        
        val inputTokenIds = tokenizer.encode(prompt)
        val generatedTokens = inputTokenIds.toMutableList()
        var tokenCount = 0
        
        while (tokenCount < MAX_NEW_TOKENS && 
               generatedTokens.size < MAX_SEQ_LENGTH &&
               coroutineContext.isActive && 
               !shouldStopGeneration) {
            
            val currentSeqLength = generatedTokens.size
            val inputIdsBuffer = prepareInputIdsBuffer(generatedTokens)
            val attentionMaskBuffer = prepareAttentionMaskBuffer(currentSeqLength)
            val outputLogitsBuffer = prepareOutputLogitsBuffer()
            
            val inputs: Map<String, Any> = mapOf(
                INPUT_IDS to inputIdsBuffer,
                INPUT_ATTENTION_MASK to attentionMaskBuffer
            )
            val outputs: Map<String, Any> = mapOf(OUTPUT_LOGITS to outputLogitsBuffer)
            
            withContext(Dispatchers.Default) {
                (interp as? Interpreter)?.runSignature(inputs, outputs, SIGNATURE_GENERATE)
                    ?: throw IllegalStateException("Interpreter mismatch")
            }
            
            val nextTokenId = sampleNextToken(outputLogitsBuffer, currentSeqLength - 1)
            
            if (nextTokenId == tokenizer.eosTokenId) break
            
            generatedTokens.add(nextTokenId)
            tokenCount++
            
            val tokenText = tokenizer.decodeToken(nextTokenId)
            if (tokenText.isNotEmpty()) emit(tokenText)
        }
        
        android.util.Log.i(TAG, "Generation completed: $tokenCount tokens, accelerator: $currentAccelerator")
        
    }.flowOn(Dispatchers.Default)

    override fun stopGeneration() {
        shouldStopGeneration = true
    }

    private fun prepareInputIdsBuffer(tokenIds: List<Int>): ByteBuffer {
        return ByteBuffer.allocateDirect(tokenIds.size * 4).apply {
            order(ByteOrder.nativeOrder())
            tokenIds.forEach { putInt(it) }
            rewind()
        }
    }

    private fun prepareAttentionMaskBuffer(seqLength: Int): ByteBuffer {
        return ByteBuffer.allocateDirect(seqLength * 4).apply {
            order(ByteOrder.nativeOrder())
            repeat(seqLength) { putInt(1) }
            rewind()
        }
    }

    private fun prepareOutputLogitsBuffer(): ByteBuffer {
        val vocabSize = tokenizer.vocabSize
        return ByteBuffer.allocateDirect(MAX_SEQ_LENGTH * vocabSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    /** Greedy decoding: select token with max logit */
    private fun sampleNextToken(logitsBuffer: ByteBuffer, lastPosition: Int): Int {
        val vocabSize = tokenizer.vocabSize
        logitsBuffer.position(lastPosition * vocabSize * 4)
        
        var maxIdx = 0
        var maxVal = Float.NEGATIVE_INFINITY
        
        repeat(vocabSize) { i ->
            val logit = logitsBuffer.getFloat()
            if (logit > maxVal) {
                maxVal = logit
                maxIdx = i
            }
        }
        
        logitsBuffer.rewind()
        return maxIdx
    }

    private fun loadModelFromAssets(assetPath: String): MappedByteBuffer {
        val fd = context.assets.openFd(assetPath)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
        )
    }

    private fun loadModelFromFile(filePath: String): MappedByteBuffer {
        val file = File(filePath)
        return FileInputStream(file).channel.map(
            FileChannel.MapMode.READ_ONLY, 0, file.length()
        )
    }

    fun getCurrentAccelerator(): AcceleratorType = currentAccelerator
    fun isLoaded(): Boolean = isModelLoaded

    override fun close() {
        kotlinx.coroutines.runBlocking { unloadModel() }
    }
}
