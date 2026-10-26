package dev.andikune.robotasisten.io.logic

import dev.andikune.robotasisten.io.util.CacheManager

/**
 * Checks cache for previously computed answers before invoking AI.
 */
class CacheMatcher(private val cacheManager: CacheManager) {

    suspend fun findInCache(query: String): String? {
        return cacheManager.getCachedAnswer(query)
    }

    suspend fun saveToCache(query: String, answer: String, mode: String) {
        cacheManager.putAnswer(query, answer, mode)
    }
}
