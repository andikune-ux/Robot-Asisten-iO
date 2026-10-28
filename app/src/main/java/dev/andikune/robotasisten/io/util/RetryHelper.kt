package dev.andikune.robotasisten.io.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Exponential backoff retry utility for resilient network calls.
 */
object RetryHelper {
    suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelayMs: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                LogHelper.w("RetryHelper", "Attempt ${attempt + 1} failed: ${e.message}, retrying in $currentDelay ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block()
    }
}
