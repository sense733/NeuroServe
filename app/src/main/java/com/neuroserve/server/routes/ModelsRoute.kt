package com.neuroserve.server.routes

import com.neuroserve.engine.EngineSelector
import com.neuroserve.engine.InferenceEngine
import com.neuroserve.data.SettingsRepository
import com.neuroserve.server.checkBearerAuth
import com.neuroserve.server.dto.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.first

/** GET /v1/models */
fun Route.modelsRoute(engineSelector: EngineSelector, settingsRepository: SettingsRepository) {
    get("/v1/models") {
        val settings = settingsRepository.settingsFlow.first()
        if (!call.checkBearerAuth(settings)) return@get

        val engine = engineSelector.currentEngine
        val currentMeta = engine?.getCurrentModelMeta()
        
        val models = if (currentMeta != null && engine != null) {
            listOf(
                ModelInfo(
                    id = currentMeta.modelName,
                    created = 1700000000,
                    accelerator = engine.getCurrentAccelerator().name.lowercase()
                )
            )
        } else {
            emptyList()
        }
        
        call.respond(ModelsListResponse(data = models))
    }
}

/** GET /health */
fun Route.healthRoute(engineSelector: EngineSelector) {
    get("/health") {
        val engine = engineSelector.currentEngine
        call.respond(
            HealthResponse(
                status = "ok",
                version = "0.1.0",
                engine = engine?.name ?: "none",
                accelerator = engine?.getCurrentAccelerator()?.name?.lowercase() ?: "none",
                modelLoaded = engine?.isLoaded() ?: false
            )
        )
    }
}
