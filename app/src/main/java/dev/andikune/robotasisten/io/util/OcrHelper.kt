package dev.andikune.robotasisten.io.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Optical Character Recognition helper using ML Kit Latin Recognizer.
 */
object OcrHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(InternalCoroutinesApi::class)
    suspend fun recognizeText(bitmap: Bitmap): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resumeSafely(Result.success(visionText.text))
                    }
                    .addOnFailureListener { e ->
                        LogHelper.e("OcrHelper", "OCR recognition failed", e)
                        continuation.resumeSafely(Result.failure(e))
                    }
            } catch (e: Exception) {
                continuation.resumeSafely(Result.failure(e))
            }
        }
    }

    private fun <T> CancellableContinuation<T>.resumeSafely(value: T) {
        val token = tryResume(value) ?: return
        completeResume(token)
    }
}
