package com.neuroserve.server.routes

import com.neuroserve.engine.EngineSelector
import com.neuroserve.engine.InferenceConfig
import com.neuroserve.engine.InferenceEngine
import com.neuroserve.server.dto.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.sse.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import com.neuroserve.data.SettingsRepository
import com.neuroserve.server.checkBearerAuth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlinx.coroutines.flow.first

/** POST /v1/chat/completions - OpenAI 兼容 API，支持流式 (SSE) */
fun Route.chatCompletionsRoute(engineSelector: EngineSelector, json: Json, settingsRepository: SettingsRepository) {
    post("/v1/chat/completions") {
        val settings = settingsRepository.settingsFlow.first()
        if (!call.checkBearerAuth(settings)) return@post

        try {
            val request = call.receive<ChatCompletionRequest>()
            val messages = request.messages
            if (messages.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("Messages list cannot be empty", "invalid_request_error")))
                return@post
            }

            val engine = engineSelector.currentEngine
            if (engine == null || !engine.isLoaded()) {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(ErrorDetail("No model loaded", "model_not_loaded")))
                return@post
            }
            
            val completionId = "chatcmpl-${UUID.randomUUID().toString().replace("-", "").take(24)}"
            val created = System.currentTimeMillis() / 1000
            
            val inferenceConfig = InferenceConfig(
                temperature = request.temperature ?: settings.temperature,
                maxTokens = request.maxTokens,
                topK = settings.topK,
                topP = request.topP
            )
            
            if (request.stream) {
                handleStreamingResponse(call, engine, json, messages, completionId, created, request.model, inferenceConfig)
            } else {
                handleNonStreamingResponse(call, engine, messages, completionId, created, request.model, inferenceConfig)
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail(e.message ?: "Unknown error", "invalid_request_error"))
            )
        }
    }
}

private suspend fun handleStreamingResponse(
    call: io.ktor.server.application.ApplicationCall,
    engine: InferenceEngine,
    json: Json,
    messages: List<ChatMessage>,
    completionId: String,
    created: Long,
    model: String,
    config: InferenceConfig
) {
    call.respondSse { send ->
        var isFirstChunk = true
        
        try {
            engine.generate(messages, config)
                .onEach { tokenText ->
                    val chunk = ChatCompletionChunk(
                        id = completionId,
                        created = created,
                        model = model,
                        choices = listOf(
                            ChunkChoice(
                                index = 0,
                                delta = if (isFirstChunk) {
                                    isFirstChunk = false
                                    DeltaContent(role = "assistant", content = tokenText)
                                } else {
                                    DeltaContent(content = tokenText)
                                },
                                finishReason = null
                            )
                        )
                    )
                    send(ServerSentEvent(data = json.encodeToString(chunk)))
                }
                .onCompletion { cause ->
                    if (cause == null) {
                        val finalChunk = ChatCompletionChunk(
                            id = completionId,
                            created = created,
                            model = model,
                            choices = listOf(ChunkChoice(index = 0, delta = DeltaContent(), finishReason = "stop"))
                        )
                        send(ServerSentEvent(data = json.encodeToString(finalChunk)))
                        send(ServerSentEvent(data = "[DONE]"))
                    }
                }
                .catch { e ->
                    val errorJson = json.encodeToString(ErrorResponse(ErrorDetail("Generation error: ${e.message}", "server_error")))
                    send(ServerSentEvent(data = errorJson))
                    send(ServerSentEvent(data = "[DONE]"))
                }
                .collect()
                
        } catch (e: Exception) {
            val errorJson = json.encodeToString(ErrorResponse(ErrorDetail("Stream error: ${e.message}", "server_error")))
            send(ServerSentEvent(data = errorJson))
            send(ServerSentEvent(data = "[DONE]"))
        }
    }
}

private suspend fun handleNonStreamingResponse(
    call: io.ktor.server.application.ApplicationCall,
    engine: InferenceEngine,
    messages: List<ChatMessage>,
    completionId: String,
    created: Long,
    model: String,
    config: InferenceConfig
) {
    try {
        val fullContent = StringBuilder()
        var tokenCount = 0

        engine.generate(messages, config)
            .collect { token ->
                fullContent.append(token)
                tokenCount++
            }

        call.respond(
            ChatCompletionResponse(
                id = completionId,
                created = created,
                model = model,
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = fullContent.toString()),
                        finishReason = "stop"
                    )
                ),
                usage = UsageInfo(
                    promptTokens = 0,
                    completionTokens = tokenCount,
                    totalTokens = tokenCount
                )
            )
        )
    } catch (e: Exception) {
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse(ErrorDetail("Generation error: ${e.message}", "server_error"))
        )
    }
}


/** 手动实现 SSE 格式，避免 ServerSSESession 接口兼容性问题 */
private suspend fun io.ktor.server.application.ApplicationCall.respondSse(
    block: suspend (send: suspend (ServerSentEvent) -> Unit) -> Unit
) {
    respondTextWriter(contentType = ContentType.Text.EventStream) {
        val sendEvent: suspend (ServerSentEvent) -> Unit = { event ->
            event.event?.let { write("event: $it\n") }
            event.id?.let { write("id: $it\n") }
            event.retry?.let { write("retry: $it\n") }
            event.data?.lines()?.forEach { line -> write("data: $line\n") }
            write("\n")
            flush()
        }
        block(sendEvent)
    }
}
