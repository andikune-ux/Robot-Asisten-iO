package dev.andikune.robotasisten.io.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Optical Character Recognition helper using ML Kit Latin Recognizer.
 */
object OcrHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(Result.success(visionText.text))
                    }
                    .addOnFailureListener { e ->
                        LogHelper.e("OcrHelper", "OCR recognition failed", e)
                        continuation.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }
}
