package com.neuroserve.server

import com.neuroserve.data.SettingsData
import com.neuroserve.data.SettingsRepository
import com.neuroserve.engine.EngineSelector
import com.neuroserve.engine.InferenceEngine
import com.neuroserve.server.dto.ErrorDetail
import com.neuroserve.server.dto.ErrorResponse
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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiServer @Inject constructor(
    private val engineSelector: EngineSelector,
    private val settingsRepository: SettingsRepository
) {

    companion object {
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
    private var currentPort: Int = 8080

    private val _activeClients = MutableStateFlow(0)
    val activeClients = _activeClients.asStateFlow()

    suspend fun start() {
        if (server != null) {
            android.util.Log.w(TAG, "Server already running")
            return
        }

        val settings = settingsRepository.settingsFlow.first()
        val host = if (settings.allowLanAccess) "0.0.0.0" else "127.0.0.1"
        val port = settings.httpPort
        currentPort = port

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (engineSelector.currentEngine == null) {
                    try {
                        engineSelector.selectBestEngine()
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "No engine available during startup", e)
                    }
                }
                server = embeddedServer(Netty, port, host) { configureServer(settingsRepository) }
                android.util.Log.i(TAG, "Starting API server at http://$host:$port")
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
    fun getServerUrl(): String = "http://0.0.0.0:$currentPort"

    private fun Application.configureServer(settingsRepository: SettingsRepository) {
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
            intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {
                _activeClients.value++
                try {
                    proceed()
                } finally {
                    _activeClients.value--
                }
            }
            get("/") {
                call.respondText(
                    """{"service":"NeuroServe","version":"0.1.0","status":"running"}""",
                    ContentType.Application.Json
                )
            }
            chatCompletionsRoute(this@ApiServer.engineSelector, json, settingsRepository)
            modelsRoute(this@ApiServer.engineSelector, settingsRepository)
            healthRoute(this@ApiServer.engineSelector)
        }
    }
}

/** Bearer Token 鉴权。鉴权未启用时直接放行返回 true。 */
suspend fun ApplicationCall.checkBearerAuth(settings: SettingsData): Boolean {
    if (!settings.apiAuthEnabled || settings.apiKey.isEmpty()) return true
    val authHeader = request.header(HttpHeaders.Authorization)
    if (authHeader != "Bearer ${settings.apiKey}") {
        respond(
            HttpStatusCode.Unauthorized,
            ErrorResponse(ErrorDetail("Invalid or missing API key", "authentication_error", code = "invalid_api_key"))
        )
        return false
    }
    return true
}
