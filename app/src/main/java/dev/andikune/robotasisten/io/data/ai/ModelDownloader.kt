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
import java.io.FileOutputStream
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
    val sha256: String,
    val capability: String,
    val recommendedRamText: String,
    val promptStyle: String = "QWEN_CHATML"
)

class ModelDownloader(private val context: Context) {
    val availableModels: List<OfflineModelSpec> = listOf(
        OfflineModelSpec(
            id = MODEL_ID,
            name = "Qwen2.5 0.5B Instruct · ringan",
            sizeText = "547 MB",
            sizeBytes = MODEL_SIZE_BYTES,
            filename = MODEL_FILENAME,
            downloadUrl = MODEL_URL,
            sha256 = MODEL_SHA256,
            capability = "Untuk chat cepat, tanya-jawab umum, dan ponsel dengan RAM terbatas.",
            recommendedRamText = "Sediakan kira-kira 1.5 GB RAM bebas saat inferensi."
        ),
        OfflineModelSpec(
            id = MODEL_15B_ID,
            name = "Qwen2.5 1.5B Instruct · lebih mampu",
            sizeText = "1.60 GB",
            sizeBytes = MODEL_15B_SIZE_BYTES,
            filename = MODEL_15B_FILENAME,
            downloadUrl = MODEL_15B_URL,
            sha256 = MODEL_15B_SHA256,
            capability = "Lebih kuat untuk instruksi panjang, penalaran, dan penjelasan; respons lebih lambat dan memerlukan lebih banyak RAM.",
            recommendedRamText = "Sediakan kira-kira 2.5 GB RAM bebas dan 1.75 GB ruang kosong sebelum unduh."
        ),
        OfflineModelSpec(
            id = MODEL_TINYLLAMA_ID,
            name = "TinyLlama 1.1B Chat · alternatif",
            sizeText = "1.15 GB",
            sizeBytes = MODEL_TINYLLAMA_SIZE_BYTES,
            filename = MODEL_TINYLLAMA_FILENAME,
            downloadUrl = MODEL_TINYLLAMA_URL,
            sha256 = MODEL_TINYLLAMA_SHA256,
            capability = "Model alternatif non-Qwen untuk chat umum dan instruksi singkat; membutuhkan ruang dan RAM lebih besar.",
            recommendedRamText = "Sediakan kira-kira 2 GB RAM bebas dan 1.3 GB ruang kosong sebelum unduh.",
            promptStyle = "TINYLLAMA_CHAT"
        )
    )

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()
    private val downloadMutex = Mutex()

    private fun modelFile(model: OfflineModelSpec) = File(FileHelper.getModelsDir(context), model.filename)
    private fun partialFile(model: OfflineModelSpec) = File(FileHelper.getModelsDir(context), "${model.filename}.part")
    private fun markerFile(model: OfflineModelSpec) = File(FileHelper.getModelsDir(context), "${model.filename}.verified")
    private fun markerTempFile(model: OfflineModelSpec) = File(FileHelper.getModelsDir(context), "${model.filename}.verified.part")

    fun isModelDownloaded(model: OfflineModelSpec): Boolean {
        val file = modelFile(model)
        val marker = markerFile(model)
        return file.isFile && file.length() == model.sizeBytes && marker.isFile &&
            runCatching { marker.readText().trim() == model.sha256 }.getOrDefault(false)
    }

    fun partialDownloadedBytes(model: OfflineModelSpec): Long =
        partialFile(model).takeIf { it.isFile }?.length()?.coerceIn(0L, model.sizeBytes) ?: 0L

    fun hasModelFiles(model: OfflineModelSpec): Boolean =
        modelFile(model).exists() || partialFile(model).exists() || markerFile(model).exists() || markerTempFile(model).exists()

    suspend fun deleteModelFiles(model: OfflineModelSpec): Boolean = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (_downloadProgress.value.containsKey(model.id)) return@withLock false
            runCatching {
                listOf(markerFile(model), markerTempFile(model), partialFile(model), modelFile(model)).forEach {
                    if (it.exists() && !Files.deleteIfExists(it.toPath())) error("Tidak dapat menghapus ${it.name}")
                }
                true
            }.getOrElse { error ->
                LogHelper.w(TAG, "Tidak dapat menghapus ${model.id}: ${error.message}")
                false
            }
        }
    }

    suspend fun startDownload(model: OfflineModelSpec, onComplete: (Boolean) -> Unit) {
        val succeeded = withContext(Dispatchers.IO) {
            downloadMutex.withLock {
                val coroutineContext = currentCoroutineContext()
                var connection: HttpURLConnection? = null
                val dir = FileHelper.getModelsDir(context)
                val target = modelFile(model)
                val partial = partialFile(model)
                val marker = markerFile(model)
                val markerTemp = markerTempFile(model)
                try {
                    require(availableModels.any { it.id == model.id }) { "Model tidak dikenal" }
                    if (isModelDownloaded(model)) return@withLock true
                    if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Tidak dapat membuat folder model")

                    if (partial.length() > model.sizeBytes) partial.delete()
                    var offset = partial.length().coerceAtMost(model.sizeBytes)
                    _downloadProgress.value = _downloadProgress.value + (model.id to (offset.toFloat() / model.sizeBytes).coerceIn(0f, 0.999f))

                    // An interrupted transfer can leave a complete file. Verify it before any network request.
                    if (offset == model.sizeBytes && verifySha256(partial, model.sha256)) {
                        markerTemp.writeText(model.sha256)
                        moveReplacing(partial, target)
                        moveReplacing(markerTemp, marker)
                        return@withLock true
                    } else if (offset == model.sizeBytes) {
                        partial.delete()
                        offset = 0L
                    }

                    val freeBytes = StatFs(dir.absolutePath).availableBytes
                    if (freeBytes < (model.sizeBytes - offset) + MIN_FREE_SPACE_AFTER_DOWNLOAD) {
                        throw IllegalStateException("Ruang kosong tidak cukup untuk sisa unduhan; sisakan ruang kosong tambahan ${(MIN_FREE_SPACE_AFTER_DOWNLOAD / 1_000_000)} MB")
                    }

                    connection = openConnection(model, offset)
                    var status = connection.responseCode
                    if (status == HTTP_RANGE_NOT_SATISFIABLE) {
                        partial.delete()
                        offset = 0L
                        connection.disconnect()
                        connection = openConnection(model, 0L)
                        status = connection.responseCode
                    }

                    var append = offset > 0L && status == HttpURLConnection.HTTP_PARTIAL &&
                        isValidContentRange(connection.getHeaderField("Content-Range"), offset, model.sizeBytes)
                    if (offset > 0L && status == HttpURLConnection.HTTP_PARTIAL && !append) {
                        // A malformed range response cannot be safely appended; request a clean full body.
                        connection.disconnect()
                        partial.delete()
                        offset = 0L
                        connection = openConnection(model, 0L)
                        status = connection.responseCode
                    }
                    if (status !in 200..299 || (status == HttpURLConnection.HTTP_PARTIAL && !append)) {
                        throw IllegalStateException("Server model mengembalikan HTTP $status")
                    }
                    if (status == HttpURLConnection.HTTP_OK) append = false
                    if (append && status != HttpURLConnection.HTTP_PARTIAL) {
                        throw IllegalStateException("Server tidak melanjutkan unduhan dengan benar")
                    }
                    val expectedResponseBytes = model.sizeBytes - if (append) offset else 0L
                    val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
                    if (declaredLength >= 0 && declaredLength != expectedResponseBytes) {
                        throw IllegalStateException("Ukuran data unduhan berbeda dari spesifikasi model")
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    var received = if (append) offset else 0L
                    if (append) updateDigestFromFile(partial, digest, coroutineContext)
                    _downloadProgress.value = _downloadProgress.value + (model.id to (received.toFloat() / model.sizeBytes).coerceIn(0f, 0.999f))
                    connection.inputStream.buffered().use { input ->
                        FileOutputStream(partial, append).buffered().use { output ->
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
                        if (partial.length() >= model.sizeBytes) {
                            partial.delete()
                            throw IllegalStateException("SHA-256 tidak cocok; file penuh yang rusak dihapus. Coba lagi akan mulai dari nol.")
                        }
                        throw IllegalStateException("Transfer belum lengkap; data yang tersimpan dapat dilanjutkan saat Coba Lagi.")
                    }

                    markerTemp.writeText(model.sha256)
                    moveReplacing(partial, target)
                    moveReplacing(markerTemp, marker)
                    true
                } catch (cancelled: CancellationException) {
                    markerTemp.delete()
                    // Preserve the partial model. The next attempt resumes it through HTTP Range.
                    throw cancelled
                } catch (error: Exception) {
                    LogHelper.w(TAG, "Download/verifikasi ${model.id} gagal: ${error.message}")
                    markerTemp.delete()
                    if (partial.length() >= model.sizeBytes) partial.delete()
                    false
                } finally {
                    connection?.disconnect()
                    _downloadProgress.value = _downloadProgress.value - model.id
                }
            }
        }
        onComplete(succeeded)
    }

    private fun openConnection(model: OfflineModelSpec, offset: Long): HttpURLConnection =
        (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "RELX-iO-Android")
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }

    private suspend fun updateDigestFromFile(file: File, digest: MessageDigest, coroutineContext: kotlin.coroutines.CoroutineContext) {
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
    }

    private fun verifySha256(file: File, expected: String): Boolean = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == expected
    }.getOrDefault(false)

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
        const val MODEL_15B_ID = "qwen2.5-1.5b-instruct-mediapipe-q8"
        const val MODEL_15B_SIZE_BYTES = 1_597_913_616L
        const val MODEL_15B_FILENAME = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        const val MODEL_15B_SHA256 = "8d867a7c93a6acf2892f08e0174e2f6f351ad256b7e3cfb6d6cd9c89794b42e0"
        const val MODEL_15B_URL = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        const val MODEL_TINYLLAMA_ID = "tinyllama-1.1b-chat-mediapipe-q8"
        const val MODEL_TINYLLAMA_SIZE_BYTES = 1_148_331_545L
        const val MODEL_TINYLLAMA_FILENAME = "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task"
        const val MODEL_TINYLLAMA_SHA256 = "0f09dc7f792bb8d49b6629effaee3ed1a99e4506b082cd353471bdf391dee053"
        const val MODEL_TINYLLAMA_URL = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task"
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}

internal fun isValidContentRange(header: String?, expectedOffset: Long, expectedTotal: Long): Boolean {
    val match = CONTENT_RANGE_PATTERN.matchEntire(header.orEmpty().trim()) ?: return false
    return match.groupValues[1].toLongOrNull() == expectedOffset &&
        match.groupValues[2].toLongOrNull() == expectedTotal - 1L &&
        match.groupValues[3].toLongOrNull() == expectedTotal
}

private val CONTENT_RANGE_PATTERN = Regex("^bytes\\s+(\\d+)-(\\d+)/(\\d+)$", RegexOption.IGNORE_CASE)
