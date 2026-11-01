package com.offlinevault.app.service.ai

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class ModelDownloadState {
    data object Idle : ModelDownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedMbPerSec: Float
    ) : ModelDownloadState()
    data class Ready(val file: File, val sizeMb: Long) : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

class AiModelManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("vault_ai_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val activeDownloadJobs = mutableMapOf<String, Job>()
    val modelStates = mutableStateMapOf<String, ModelDownloadState>()

    var activeModelId by mutableStateOf(prefs.getString("active_model", "smollm2-135m") ?: "smollm2-135m")
        private set

    val modelsDir: File by lazy {
        val dir = File(context.filesDir, "ai_models")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    init {
        refreshInstalledModels()
    }

    fun refreshInstalledModels() {
        for (model in AiModelCatalog.curatedModels) {
            val file = File(modelsDir, model.fileName)
            if (file.exists() && file.length() > 1_000_000L) {
                val sizeMb = file.length() / (1024 * 1024)
                modelStates[model.id] = ModelDownloadState.Ready(file, sizeMb)
            } else if (modelStates[model.id] !is ModelDownloadState.Downloading) {
                modelStates[model.id] = ModelDownloadState.Idle
            }
        }
    }

    fun setActiveModel(modelId: String) {
        activeModelId = modelId
        prefs.edit().putString("active_model", modelId).apply()
    }

    fun getActiveModelInfo(): LocalModelInfo? {
        return AiModelCatalog.getById(activeModelId)
    }

    fun isModelReady(modelId: String): Boolean {
        return modelStates[modelId] is ModelDownloadState.Ready
    }

    fun getModelFile(modelId: String): File? {
        val model = AiModelCatalog.getById(modelId) ?: return null
        val file = File(modelsDir, model.fileName)
        return if (file.exists() && file.length() > 1_000_000L) file else null
    }

    fun startDownload(model: LocalModelInfo) {
        if (activeDownloadJobs.containsKey(model.id)) return

        val job = scope.launch {
            val destFile = File(modelsDir, model.fileName)
            val partFile = File(modelsDir, "${model.fileName}.part")

            try {
                modelStates[model.id] = ModelDownloadState.Downloading(0f, 0L, model.fileSizeMb * 1024 * 1024, 0f)

                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "OfflineVault-Android/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    modelStates[model.id] = ModelDownloadState.Error("HTTP ${response.code}: ${response.message}")
                    return@launch
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val totalBytes = if (body.contentLength() > 0) body.contentLength() else model.fileSizeMb * 1024 * 1024

                var downloadedBytes = 0L
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (!isActive) {
                                partFile.delete()
                                return@launch
                            }
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            bytesSinceLastTime += read

                            val now = System.currentTimeMillis()
                            val delta = now - lastTime
                            if (delta >= 400) {
                                val speedMb = (bytesSinceLastTime.toFloat() / (1024f * 1024f)) / (delta.toFloat() / 1000f)
                                val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) {
                                    modelStates[model.id] = ModelDownloadState.Downloading(
                                        progress = progress,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        speedMbPerSec = (speedMb * 10).toInt() / 10f
                                    )
                                }
                                lastTime = now
                                bytesSinceLastTime = 0L
                            }
                        }
                    }
                }

                if (destFile.exists()) destFile.delete()
                partFile.renameTo(destFile)

                val sizeMb = destFile.length() / (1024 * 1024)
                withContext(Dispatchers.Main) {
                    modelStates[model.id] = ModelDownloadState.Ready(destFile, sizeMb)
                    setActiveModel(model.id)
                }
            } catch (e: Exception) {
                if (partFile.exists()) partFile.delete()
                withContext(Dispatchers.Main) {
                    modelStates[model.id] = ModelDownloadState.Error("Download failed: ${e.localizedMessage}")
                }
            } finally {
                activeDownloadJobs.remove(model.id)
            }
        }

        activeDownloadJobs[model.id] = job
    }

    fun cancelDownload(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
        val model = AiModelCatalog.getById(modelId)
        if (model != null) {
            val partFile = File(modelsDir, "${model.fileName}.part")
            if (partFile.exists()) partFile.delete()
        }
        modelStates[modelId] = ModelDownloadState.Idle
    }

    fun deleteModel(modelId: String) {
        cancelDownload(modelId)
        val model = AiModelCatalog.getById(modelId)
        if (model != null) {
            val file = File(modelsDir, model.fileName)
            if (file.exists()) file.delete()
        }
        modelStates[modelId] = ModelDownloadState.Idle
    }

    fun importCustomModel(sourceUri: Uri, originalName: String?): Boolean {
        return try {
            val safeName = (originalName ?: "custom_model.gguf").replace(" ", "_")
            val targetFile = File(modelsDir, safeName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            refreshInstalledModels()
            true
        } catch (_: Exception) {
            false
        }
    }
}
