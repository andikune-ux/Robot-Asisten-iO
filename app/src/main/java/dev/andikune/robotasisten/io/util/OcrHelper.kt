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

    suspend fun recognizeText(bitmap: Bitmap): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            try {
                if (bitmap.isRecycled) {
                    continuation.resumeSafely(Result.failure(IllegalStateException("Bitmap sudah di-recycle")))
                    return@suspendCancellableCoroutine
                }
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resumeSafely(Result.success(visionText.text))
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("RAILX_CRASH_DEBUG", "Error di [ML Kit Text Recognition onFailure]: ${e.message}", e)
                        continuation.resumeSafely(Result.failure(e))
                    }
            } catch (t: Throwable) {
                android.util.Log.e("RAILX_CRASH_DEBUG", "Error di [InputImage atau Recognizer Process]: ${t.message}", t)
                continuation.resumeSafely(Result.failure(Exception(t)))
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun <T> CancellableContinuation<T>.resumeSafely(value: T) {
        val token = tryResume(value) ?: return
        completeResume(token)
    }
}
