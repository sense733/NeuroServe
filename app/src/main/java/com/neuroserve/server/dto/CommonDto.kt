package com.neuroserve.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelsListResponse(
    @SerialName("object") val objectType: String = "list",
    val data: List<ModelInfo>
)

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("object") val objectType: String = "model",
    val created: Long,
    @SerialName("owned_by") val ownedBy: String = "neuroserve",
    val accelerator: String? = null
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val engine: String,
    val accelerator: String,
    @SerialName("model_loaded") val modelLoaded: Boolean
)

@Serializable
data class ErrorResponse(val error: ErrorDetail)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String,
    val param: String? = null,
    val code: String? = null
)
