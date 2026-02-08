package com.neuroserve.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    val stop: List<String>? = null,
    val user: String? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: UsageInfo? = null,
    @SerialName("system_fingerprint") val systemFingerprint: String? = null
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class UsageInfo(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class ChatCompletionChunk(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>,
    @SerialName("system_fingerprint") val systemFingerprint: String? = null
)

@Serializable
data class ChunkChoice(
    val index: Int,
    val delta: DeltaContent,
    @SerialName("finish_reason") val finishReason: String? = null
)

/** 流式响应中，首个 chunk 包含 role，后续 chunk 只包含 content */
@Serializable
data class DeltaContent(
    val role: String? = null,
    val content: String? = null
)
