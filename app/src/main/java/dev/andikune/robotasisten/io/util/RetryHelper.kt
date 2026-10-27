package dev.andikune.robotasisten.io.util

import kotlinx.coroutines.delay

/**
 * Utility untuk retry dengan exponential backoff.
 * Digunakan pada ModelDownloader dan koneksi Cloud AI untuk menangani kegagalan jaringan sementara.
 */
object RetryHelper {
    
    /**
     * Menjalankan blok kode dengan mekanisme retry otomatis.
     * @param maxRetries Jumlah maksimal percobaan ulang (default: 3)
     * @param initialDelay Delay awal dalam milidetik (default: 1000ms)
     * @param maxDelay Delay maksimal antar percobaan (default: 10000ms)
     * @param factor Faktor pengali delay (default: 2.0x)
     * @param block Blok suspend yang akan dieksekusi
     */
    suspend fun <T> withExponentialBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 1000L,
        maxDelay: Long = 10000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                LogHelper.w("RetryHelper", "Percobaan ${attempt + 1}/$maxRetries gagal: ${e.message}. Menunggu ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        throw lastException ?: IllegalStateException("Percobaan gagal setelah $maxRetries kali")
    }
}
