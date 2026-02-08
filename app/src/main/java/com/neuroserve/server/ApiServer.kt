package com.neuroserve.server

import com.neuroserve.engine.EngineSelector
import com.neuroserve.engine.LiteRTEngine
import com.neuroserve.server.routes.chatCompletionsRoute
import com.neuroserve.server.routes.healthRoute
import com.neuroserve.server.routes.modelsRoute
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ktor 嵌入式 API 服务器。提供 OpenAI 兼容 API (0.0.0.0:8000)。
 */
@Singleton
class ApiServer @Inject constructor(
    private val engineSelector: EngineSelector,
    private val liteRTEngine: LiteRTEngine
) {

    companion object {
        private const val HOST = "0.0.0.0"
        private const val PORT = 8000
        private const val TAG = "ApiServer"
    }

    private val json = Json {
        prettyPrint = false
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var serverJob: Job? = null

    fun start() {
        if (server != null) {
            android.util.Log.w(TAG, "Server already running")
            return
        }
        
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(Netty, PORT, HOST) { configureServer() }
                android.util.Log.i(TAG, "Starting API server at http://$HOST:$PORT")
                server?.start(wait = false)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start API server", e)
            }
        }
    }

    fun stop() {
        try {
            server?.stop(1000, 3000)
            android.util.Log.i(TAG, "API server stopped")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping server", e)
        } finally {
            serverJob?.cancel()
            server = null
            serverJob = null
        }
    }

    fun isRunning(): Boolean = server != null
    fun getServerUrl(): String = "http://$HOST:$PORT"

    private fun Application.configureServer() {
        install(ContentNegotiation) { json(json) }
        install(SSE)
        
        install(CORS) {
            allowHost("localhost", schemes = listOf("http"))
            allowHost("127.0.0.1", schemes = listOf("http"))
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Options)
        }
        
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                android.util.Log.e(TAG, "Unhandled exception", cause)
                call.respondText(
                    """{"error":{"message":"${cause.message?.replace("\"", "\\\"")}","type":"server_error"}}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }
        
        routing {
            get("/") {
                call.respondText(
                    """{"service":"NeuroServe","version":"0.1.0","status":"running"}""",
                    ContentType.Application.Json
                )
            }
            chatCompletionsRoute(liteRTEngine, json)
            modelsRoute()
            healthRoute(engineSelector, liteRTEngine)
        }
    }
}
