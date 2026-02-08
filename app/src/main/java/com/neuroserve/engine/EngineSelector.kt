package com.neuroserve.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 推理引擎选择器。Fallback 策略：NPU → GPU → CPU
 */
@Singleton
class EngineSelector @Inject constructor(
    private val liteRTEngine: LiteRTEngine,
    private val mlcEngine: MLCEngine,
    private val llamaCppEngine: LlamaCppEngine
) {

    private val engines: List<InferenceEngine> = listOf(
        liteRTEngine, mlcEngine, llamaCppEngine
    ).sortedBy { it.priority }

    var currentEngine: InferenceEngine? = null
        private set

    suspend fun selectBestEngine(): InferenceEngine {
        for (engine in engines) {
            if (engine.isAvailable()) {
                currentEngine = engine
                return engine
            }
        }
        throw IllegalStateException("No inference engine available")
    }

    fun getEngineStatus() = EngineStatus(
        currentEngineName = currentEngine?.name ?: "None",
        priority = currentEngine?.priority ?: -1,
        availableEngines = engines.map { it.name }
    )
}

data class EngineStatus(
    val currentEngineName: String,
    val priority: Int,
    val availableEngines: List<String>
)
