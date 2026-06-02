package com.neuroserve.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngineManager @Inject constructor(
    private val engineSelector: EngineSelector
) {
    private var activeEngine: InferenceEngine? = null

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()
    
    private val _inferenceSpeed = MutableStateFlow("")
    val inferenceSpeed: StateFlow<String> = _inferenceSpeed.asStateFlow()

    suspend fun loadModel(meta: ModelMeta) {
        try {
            activeEngine?.let { if (it.isLoaded()) it.unloadModel() }
            
            val engine = engineSelector.selectEngineForModel(meta)
            engine.loadModel(meta)
            activeEngine = engine
            
            _isModelLoaded.value = true
            _activeModelName.value = meta.displayName
            Log.i("EngineManager", "Model loaded via ${engine.name}: ${meta.displayName}")
        } catch (e: Exception) {
            Log.e("EngineManager", "Failed to load model: ${meta.displayName}", e)
            throw e
        }
    }
    
    fun getActiveEngine(): InferenceEngine? {
        return if (_isModelLoaded.value) activeEngine else null
    }
    
    fun updateInferenceSpeed(speed: String) {
        _inferenceSpeed.value = speed
    }
}
