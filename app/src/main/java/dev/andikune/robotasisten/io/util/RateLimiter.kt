package dev.andikune.robotasisten.io.util

/**
 * Enforces per-minute call throttling to prevent API key quota exhaustion.
 */
class RateLimiter(private val maxCallsPerMinute: Int = 15) {
    private val timestamps = mutableListOf<Long>()

    @Synchronized
    fun canProceed(): Boolean {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60_000
        timestamps.removeAll { it < oneMinuteAgo }
        return if (timestamps.size < maxCallsPerMinute) {
            timestamps.add(now)
            true
        } else {
            false
        }
    }

    @Synchronized
    fun getRemainingCalls(): Int {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60_000
        timestamps.removeAll { it < oneMinuteAgo }
        return (maxCallsPerMinute - timestamps.size).coerceAtLeast(0)
    }
}
