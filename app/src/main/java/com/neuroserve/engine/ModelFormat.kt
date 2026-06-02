package com.neuroserve.engine

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

enum class ModelFormat { GGUF, NEXA, LITERTLM }

data class ModelMeta(
    val format: ModelFormat,
    val modelPath: String,
    val modelName: String,
    val modelType: String,
    val pluginId: String,
    val tokenizerPath: String? = null,
    val displayName: String,
    val sizeBytes: Long
)

@Serializable
data class NexaManifest(
    val ModelName: String? = null,
    val ModelType: String? = null,
    val PluginId: String? = null
)

private const val TAG = "ModelDiscovery"
private val manifestJson = Json { ignoreUnknownKeys = true }

fun parseNexaManifest(dir: File): NexaManifest? {
    val manifestFile = File(dir, "nexa.manifest")
    if (!manifestFile.exists()) return null
    return try {
        manifestJson.decodeFromString<NexaManifest>(manifestFile.readText())
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse nexa.manifest in ${dir.name}", e)
        null
    }
}

private fun isLiteRtModel(name: String): Boolean =
    name.endsWith(".litertlm", ignoreCase = true)

fun discoverModels(modelsDir: File): List<ModelMeta> {
    if (!modelsDir.exists()) return emptyList()
    val models = mutableListOf<ModelMeta>()

    // 1. LiteRT-LM 单文件
    modelsDir.listFiles { _, name -> isLiteRtModel(name) }?.forEach { file ->
        models.add(
            ModelMeta(
                format = ModelFormat.LITERTLM,
                modelPath = file.absolutePath,
                modelName = file.nameWithoutExtension,
                modelType = "llm",
                pluginId = "litertlm",
                displayName = file.name,
                sizeBytes = file.length()
            )
        )
    }

    // 2. GGUF 单文件（排除 .litertlm 后缀的伪 GGUF）
    modelsDir.listFiles { _, name ->
        name.endsWith(".gguf", ignoreCase = true) && !name.contains(".litertlm.", ignoreCase = true)
    }?.forEach { file ->
        models.add(
            ModelMeta(
                format = ModelFormat.GGUF,
                modelPath = file.absolutePath,
                modelName = file.nameWithoutExtension,
                modelType = "llm",
                pluginId = "cpu_gpu",
                displayName = file.name,
                sizeBytes = file.length()
            )
        )
    }

    // 3. Nexa 目录（含 nexa.manifest）
    modelsDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
        val manifest = parseNexaManifest(dir) ?: return@forEach
        val totalSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        models.add(
            ModelMeta(
                format = ModelFormat.NEXA,
                modelPath = dir.absolutePath,
                modelName = manifest.ModelName ?: dir.name,
                modelType = manifest.ModelType ?: "llm",
                pluginId = manifest.PluginId ?: "npu",
                displayName = manifest.ModelName ?: dir.name,
                sizeBytes = totalSize
            )
        )
    }

    return models.sortedBy { it.displayName.lowercase() }
}
