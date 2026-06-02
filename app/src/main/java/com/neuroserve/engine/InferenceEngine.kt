package com.neuroserve.engine

import kotlinx.coroutines.flow.Flow
import com.neuroserve.server.dto.ChatMessage

data class InferenceConfig(
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topK: Int? = null,
    val topP: Float? = null
)

/** 加速器类型 */
enum class AcceleratorType { NPU, GPU, CPU }

/**
 * 推理引擎核心契约。所有后端（Nexa/MLC/llama.cpp）必须实现此接口。
 */
interface InferenceEngine {

    val name: String

    /** 优先级：1=NPU, 2=GPU, 3=CPU */
    val priority: Int

    suspend fun isAvailable(): Boolean

    suspend fun loadModel(meta: ModelMeta)

    suspend fun unloadModel()

    suspend fun generate(messages: List<ChatMessage>, config: InferenceConfig? = null): Flow<String>

    fun stopGeneration()

    fun isLoaded(): Boolean

    fun getCurrentAccelerator(): AcceleratorType

    fun getCurrentModelMeta(): ModelMeta?
}
