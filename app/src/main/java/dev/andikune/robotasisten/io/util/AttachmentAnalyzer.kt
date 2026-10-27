package dev.andikune.robotasisten.io.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Extracts local, text-oriented context for the offline text model without uploading the original file. */
object AttachmentAnalyzer {
    data class Result(val displayName: String, val mimeType: String, val text: String, val warning: String = "")

    suspend fun analyze(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        val name = queryName(resolver, uri) ?: uri.lastPathSegment ?: "lampiran"
        runCatching {
            when {
                mime.startsWith("image/") -> Result(name, mime, analyzeImage(resolver, uri))
                mime.startsWith("video/") -> analyzeVideo(context, uri, name, mime)
                mime == "application/pdf" || name.endsWith(".pdf", true) -> Result(name, mime, analyzePdf(resolver, uri))
                isOfficeOpenXml(name, mime) -> Result(name, mime, analyzeOffice(resolver, uri))
                isPlainText(mime, name) -> Result(name, mime, readText(resolver, uri))
                else -> Result(name, mime, "", "Format belum memiliki extractor lokal; nama dan metadata file tetap terbaca.")
            }
        }.getOrElse { error ->
            Result(name, mime, "", "Gagal membaca lampiran secara offline: ${error.message ?: "format tidak terbaca"}")
        }
    }

    fun composeContext(results: List<Result>): String = buildString {
        results.forEachIndexed { index, result ->
            appendLine("[Lampiran ${index + 1}: ${result.displayName} | ${result.mimeType}]")
            if (result.text.isNotBlank()) appendLine(result.text.take(MAX_ATTACHMENT_CHARS))
            if (result.warning.isNotBlank()) appendLine("Catatan: ${result.warning}")
            appendLine()
        }
    }.trim()

    private fun queryName(resolver: android.content.ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun readText(resolver: android.content.ContentResolver, uri: Uri): String =
        resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(MAX_ATTACHMENT_CHARS) }.orEmpty()

    private suspend fun analyzeImage(resolver: android.content.ContentResolver, uri: Uri): String {
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return "Gambar tidak dapat dibuka."
        return try {
            OcrHelper.recognizeText(bitmap).getOrElse { "OCR gambar gagal: ${it.message ?: "tidak ada teks"}" }
        } finally { bitmap.recycle() }
    }

    private fun analyzePdf(resolver: android.content.ContentResolver, uri: Uri): String {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: return "PDF tidak dapat dibuka."
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val output = StringBuilder()
                for (pageIndex in 0 until renderer.pageCount.coerceAtMost(MAX_PDF_PAGES)) {
                    val page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val text = OcrHelper.recognizeText(bitmap).getOrNull().orEmpty()
                    if (text.isNotBlank()) output.appendLine("Halaman ${pageIndex + 1}:\n$text")
                    bitmap.recycle()
                    page.close()
                }
                return output.toString().take(MAX_ATTACHMENT_CHARS)
            }
        }
    }

    private suspend fun analyzeVideo(context: Context, uri: Uri, name: String, mime: String): Result {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val frameCount = ((durationMs / FRAME_INTERVAL_MS).toInt() + 1).coerceAtMost(MAX_VIDEO_FRAMES)
        val output = StringBuilder("Durasi video: ${durationMs} ms\n")
        for (index in 0 until frameCount) {
            val timeUs = (index * FRAME_INTERVAL_MS * 1_000L).coerceAtMost(durationMs * 1_000L)
            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            if (bitmap != null) {
                val text = OcrHelper.recognizeText(bitmap).getOrNull().orEmpty()
                if (text.isNotBlank()) output.appendLine("Frame ${index + 1}:\n$text")
                bitmap.recycle()
            }
        }
        retriever.release()
        return Result(name, mime, output.toString().take(MAX_ATTACHMENT_CHARS), "Video dianalisa melalui metadata dan OCR frame; audio belum ditranskripsi.")
    }

    private fun analyzeOffice(resolver: android.content.ContentResolver, uri: Uri): String {
        val output = StringBuilder()
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name.lowercase()
                    if (!entry.isDirectory && (path.endsWith(".xml") || path.endsWith(".rels"))) {
                        val bytes = ByteArrayOutputStream()
                        var read: Int
                        while (zip.read(buffer).also { read = it } >= 0) bytes.write(buffer, 0, read)
                        val raw = bytes.toString(Charsets.UTF_8.name())
                        val clean = raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                        if (clean.isNotBlank() && !path.contains("[content_types]")) output.appendLine("$path: $clean")
                    }
                    zip.closeEntry()
                    if (output.length > MAX_ATTACHMENT_CHARS) break
                }
            }
        }
        return output.toString().take(MAX_ATTACHMENT_CHARS)
    }

    private fun isPlainText(mime: String, name: String): Boolean =
        mime.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in setOf("txt", "csv", "json", "xml", "md", "kt", "java", "py", "js", "html", "css")

    private fun isOfficeOpenXml(name: String, mime: String): Boolean =
        mime in setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.presentationml.presentation") ||
            name.substringAfterLast('.', "").lowercase() in setOf("docx", "xlsx", "pptx")

    private const val MAX_ATTACHMENT_CHARS = 40_000
    private const val MAX_PDF_PAGES = 40
    private const val MAX_VIDEO_FRAMES = 24
    private const val FRAME_INTERVAL_MS = 10_000L
}
