package com.neuroserve.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** MLC LLM Vulkan 引擎 - GPU 备选方案 */
@Singleton
class MLCEngine @Inject constructor() : InferenceEngine {

    override val name: String = "MLC-Vulkan"
    override val priority: Int = 2

    override suspend fun isAvailable(): Boolean = false  // TODO: Check Vulkan support

    override suspend fun loadModel(modelPath: String) { /* TODO */ }

    override suspend fun unloadModel() { /* TODO */ }

    override suspend fun generate(prompt: String): Flow<String> = flow {
        emit("[MLC Engine Placeholder]")
    }

    override fun stopGeneration() { /* TODO */ }
}
