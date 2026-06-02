package com.neuroserve.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.neuroserve.engine.ModelFormat
import com.neuroserve.engine.ModelMeta
import com.neuroserve.engine.discoverModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ModelInfo(
    val name: String,
    val file: File,
    val size: String,
    val meta: ModelMeta,
    val formatLabel: String = when (meta.format) {
        ModelFormat.NEXA -> "NPU"
        ModelFormat.LITERTLM -> "LiteRT"
        ModelFormat.GGUF -> "GGUF"
    }
)

@Singleton
class ModelRepository @Inject constructor() {
    private val _modelList = MutableStateFlow<List<ModelInfo>>(emptyList())
    val modelList: StateFlow<List<ModelInfo>> = _modelList.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(-1f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    suspend fun refreshModelList(filesDir: File) = withContext(Dispatchers.IO) {
        val models = discoverModels(filesDir).map { meta ->
            ModelInfo(
                name = meta.displayName,
                file = File(meta.modelPath),
                size = formatSize(meta.sizeBytes),
                meta = meta
            )
        }
        _modelList.value = models
    }

    suspend fun importModel(context: Context, uri: Uri): String {
        if (_isImporting.value) throw IllegalStateException("Already importing")
        _isImporting.value = true
        _importProgress.value = -1f

        try {
            var fileName = "model_${System.currentTimeMillis()}.gguf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            val isZip = fileName.endsWith(".zip", ignoreCase = true)

            withContext(Dispatchers.IO) {
                if (isZip) {
                    extractZipModel(context, uri, fileName)
                } else {
                    importSingleFile(context, uri, fileName)
                }
            }

            refreshModelList(context.filesDir)
            return fileName
        } finally {
            _isImporting.value = false
        }
    }

    private fun importSingleFile(context: Context, uri: Uri, rawName: String) {
        var fileName = rawName
        val knownExtensions = listOf(".gguf", ".litertlm")
        if (knownExtensions.none { fileName.endsWith(it, ignoreCase = true) }) {
            fileName += ".gguf"
        }
        val destFile = File(context.filesDir, fileName)
        var totalSize = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    totalSize = cursor.getLong(sizeIndex)
                }
            }
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> 
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        _importProgress.value = totalRead.toFloat() / totalSize
                    }
                }
            }
        } ?: throw Exception("Cannot open file")
        Log.i("ModelRepository", "Model copied to: ${destFile.absolutePath}")
    }

    private fun extractZipModel(context: Context, uri: Uri, zipName: String) {
        val dirName = zipName.removeSuffix(".zip").removeSuffix(".ZIP")
        val destDir = File(context.filesDir, dirName)
        destDir.mkdirs()

        var totalSize = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    totalSize = cursor.getLong(sizeIndex)
                }
            }
        }

        context.contentResolver.openInputStream(uri)?.use { raw ->
            val progressRaw = ProgressInputStream(raw, totalSize) { progress ->
                _importProgress.value = progress
            }
            ZipInputStream(progressRaw).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                        throw SecurityException("Zip path traversal: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (zis.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw Exception("Cannot open ZIP")
        Log.i("ModelRepository", "ZIP extracted to: ${destDir.absolutePath}")
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
    }

    private class ProgressInputStream(
        inStream: InputStream,
        private val totalSize: Long,
        private val onProgress: (Float) -> Unit
    ) : FilterInputStream(inStream) {
        private var totalRead = 0L
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = super.read(b, off, len)
            if (read != -1) {
                totalRead += read
                if (totalSize > 0) onProgress((totalRead.toFloat() / totalSize).coerceAtMost(1f))
            }
            return read
        }
    }
}
