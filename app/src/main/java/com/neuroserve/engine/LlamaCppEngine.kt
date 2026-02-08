package com.neuroserve.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** llama.cpp CPU 引擎 - 最终兜底方案 */
@Singleton
class LlamaCppEngine @Inject constructor() : InferenceEngine {

    override val name: String = "llama.cpp"
    override val priority: Int = 3

    override suspend fun isAvailable(): Boolean = true  // CPU always available

    override suspend fun loadModel(modelPath: String) { /* TODO: JNI integration */ }

    override suspend fun unloadModel() { /* TODO */ }

    override suspend fun generate(prompt: String): Flow<String> = flow {
        emit("[llama.cpp Engine Placeholder]")
    }

    override fun stopGeneration() { /* TODO */ }
}
