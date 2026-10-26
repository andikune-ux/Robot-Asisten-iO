package dev.andikune.robotasisten.io.data.ai

import android.content.Context
import android.os.StatFs
import dev.andikune.robotasisten.io.util.FileHelper
import dev.andikune.robotasisten.io.util.LogHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class OfflineModelSpec(
    val id: String,
    val name: String,
    val sizeText: String,
    val sizeBytes: Long,
    val filename: String,
    val downloadUrl: String,
    val sha256: String
)

class ModelDownloader(private val context: Context) {
    val availableModels: List<OfflineModelSpec> = listOf(
        OfflineModelSpec(
            id = MODEL_ID,
            name = "Qwen2.5 0.5B Instruct (MediaPipe, Q8)",
            sizeText = "546,7 MB",
            sizeBytes = MODEL_SIZE_BYTES,
            filename = MODEL_FILENAME,
            downloadUrl = MODEL_URL,
            sha256 = MODEL_SHA256
        )
    )

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()
    private val downloadMutex = Mutex()

    private fun modelFile(model: OfflineModelSpec) = File(FileHelper.getModelsDir(context), model.filename)
    private fun markerFile(model: OfflineModelSpec) = File(FileHelper.getModelsDir(context), "${model.filename}.verified")

    fun isModelDownloaded(model: OfflineModelSpec): Boolean {
        val file = modelFile(model)
        val marker = markerFile(model)
        return file.isFile && file.length() == model.sizeBytes && marker.isFile &&
            runCatching { marker.readText().trim() == model.sha256 }.getOrDefault(false)
    }

    suspend fun startDownload(model: OfflineModelSpec, onComplete: (Boolean) -> Unit) {
        val succeeded = withContext(Dispatchers.IO) {
            downloadMutex.withLock {
                val coroutineContext = currentCoroutineContext()
                var connection: HttpURLConnection? = null
                val dir = FileHelper.getModelsDir(context)
                val target = modelFile(model)
                val partial = File(dir, "${model.filename}.part")
                val marker = markerFile(model)
                val markerTemp = File(dir, "${model.filename}.verified.part")
                try {
                    require(availableModels.any { it.id == model.id }) { "Model tidak dikenal" }
                    if (isModelDownloaded(model)) return@withLock true
                    if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Tidak dapat membuat folder model")
                    val freeBytes = StatFs(dir.absolutePath).availableBytes
                    if (freeBytes < model.sizeBytes + MIN_FREE_SPACE_AFTER_DOWNLOAD) {
                        throw IllegalStateException("Ruang kosong tidak cukup; sisakan setidaknya ${(model.sizeBytes + MIN_FREE_SPACE_AFTER_DOWNLOAD) / 1_000_000} MB")
                    }
                    partial.delete()
                    markerTemp.delete()
                    connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        instanceFollowRedirects = true
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/octet-stream")
                        setRequestProperty("User-Agent", "RELX-iO-Android")
                    }
                    val status = connection.responseCode
                    if (status !in 200..299) throw IllegalStateException("Server model mengembalikan HTTP $status")
                    val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
                    if (declaredLength >= 0 && declaredLength != model.sizeBytes) {
                        throw IllegalStateException("Ukuran unduhan berbeda dari spesifikasi model")
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    var received = 0L
                    connection.inputStream.buffered().use { input ->
                        partial.outputStream().buffered().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                received += count
                                if (received > model.sizeBytes) throw IllegalStateException("Unduhan melebihi ukuran model")
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                _downloadProgress.value = _downloadProgress.value +
                                    (model.id to (received.toFloat() / model.sizeBytes).coerceIn(0f, 0.999f))
                            }
                        }
                    }
                    coroutineContext.ensureActive()
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if (received != model.sizeBytes || actualHash != model.sha256) {
                        throw IllegalStateException("Verifikasi ukuran atau SHA-256 model gagal")
                    }

                    markerTemp.writeText(model.sha256)
                    moveReplacing(partial, target)
                    moveReplacing(markerTemp, marker)
                    true
                } catch (cancelled: CancellationException) {
                    partial.delete()
                    markerTemp.delete()
                    throw cancelled
                } catch (error: Exception) {
                    LogHelper.w(TAG, "Download/verifikasi ${model.id} gagal: ${error.message}")
                    partial.delete()
                    markerTemp.delete()
                    false
                } finally {
                    connection?.disconnect()
                    _downloadProgress.value = _downloadProgress.value - model.id
                }
            }
        }
        onComplete(succeeded)
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val TAG = "ModelDownloader"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val MIN_FREE_SPACE_AFTER_DOWNLOAD = 150_000_000L
        const val MODEL_ID = "qwen2.5-0.5b-instruct-mediapipe-q8"
        const val MODEL_SIZE_BYTES = 546_660_344L
        const val MODEL_FILENAME = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        const val MODEL_SHA256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
        const val MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    }
}
