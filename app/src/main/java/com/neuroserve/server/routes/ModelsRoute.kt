package com.neuroserve.server.routes

import com.neuroserve.engine.EngineSelector
import com.neuroserve.engine.LiteRTEngine
import com.neuroserve.server.dto.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** GET /v1/models */
fun Route.modelsRoute() {
    get("/v1/models") {
        val models = listOf(
            ModelInfo(id = "qwen2.5-3b", created = 1700000000, accelerator = "npu"),
            ModelInfo(id = "qwen2.5-7b", created = 1700000000, accelerator = "npu")
        )
        call.respond(ModelsListResponse(data = models))
    }
}

/** GET /health */
fun Route.healthRoute(engineSelector: EngineSelector, liteRTEngine: LiteRTEngine) {
    get("/health") {
        call.respond(
            HealthResponse(
                status = "ok",
                version = "0.1.0",
                engine = engineSelector.currentEngine?.name ?: "none",
                accelerator = liteRTEngine.getCurrentAccelerator().name.lowercase(),
                modelLoaded = liteRTEngine.isLoaded()
            )
        )
    }
}
