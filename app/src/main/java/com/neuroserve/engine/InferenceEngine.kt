package com.neuroserve.engine

import kotlinx.coroutines.flow.Flow

/**
 * 推理引擎核心契约。所有后端（LiteRT/MLC/llama.cpp）必须实现此接口。
 */
interface InferenceEngine {

    val name: String

    /** 优先级：1=NPU, 2=GPU, 3=CPU */
    val priority: Int

    suspend fun isAvailable(): Boolean

    suspend fun loadModel(modelPath: String)

    suspend fun unloadModel()

    suspend fun generate(prompt: String): Flow<String>

    fun stopGeneration()
}
