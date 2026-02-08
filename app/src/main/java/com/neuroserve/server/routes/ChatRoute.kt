package com.neuroserve.server.routes

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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** POST /v1/chat/completions - OpenAI 兼容 API，支持流式 (SSE) */
fun Route.chatCompletionsRoute(engine: InferenceEngine, json: Json) {
    post("/v1/chat/completions") {
        try {
            val request = call.receive<ChatCompletionRequest>()
            val prompt = buildPromptFromMessages(request.messages)
            val completionId = "chatcmpl-${UUID.randomUUID().toString().replace("-", "").take(24)}"
            val created = System.currentTimeMillis() / 1000
            
            if (request.stream) {
                handleStreamingResponse(call, engine, json, prompt, completionId, created, request.model)
            } else {
                // TODO: Implement non-streaming response
                call.respond(
                    HttpStatusCode.NotImplemented,
                    ErrorResponse(ErrorDetail("Non-streaming mode not implemented. Use stream=true", "not_implemented_error", code = "not_implemented"))
                )
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
    prompt: String,
    completionId: String,
    created: Long,
    model: String
) {
    call.respondSse { send ->
        var isFirstChunk = true
        
        try {
            engine.generate(prompt)
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

/** 简单 prompt 模板，后续可根据模型需求调整 */
private fun buildPromptFromMessages(messages: List<ChatMessage>): String {
    return buildString {
        messages.forEach { msg ->
            when (msg.role) {
                "system" -> append("System: ${msg.content}\n\n")
                "user" -> append("User: ${msg.content}\n\n")
                "assistant" -> append("Assistant: ${msg.content}\n\n")
            }
        }
        append("Assistant: ")
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
