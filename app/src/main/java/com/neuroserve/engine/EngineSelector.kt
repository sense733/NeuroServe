package com.neuroserve.engine

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngineSelector @Inject constructor(
    engines: Set<@JvmSuppressWildcards InferenceEngine>
) {

    private val engines: List<InferenceEngine> = engines.sortedBy { it.priority }

    var currentEngine: InferenceEngine? = null
        private set

    fun selectEngineForModel(meta: ModelMeta): InferenceEngine {
        val engine = when (meta.format) {
            ModelFormat.LITERTLM -> engines.first { it is LiteRtEngine }
            ModelFormat.GGUF, ModelFormat.NEXA -> engines.first { it is NexaEngine }
        }
        currentEngine = engine
        return engine
    }

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
