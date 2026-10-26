package dev.andikune.robotasisten.io.util

import dev.andikune.robotasisten.io.data.db.CacheDao
import dev.andikune.robotasisten.io.data.db.CacheEntity

/**
 * Manages caching layer to intercept identical queries and save AI tokens.
 */
class CacheManager(private val cacheDao: CacheDao) {

    suspend fun getCachedAnswer(query: String): String? {
        val hash = EncryptionHelper.sha256(normalize(query))
        val entry = cacheDao.getByHash(hash)
        if (entry != null) {
            cacheDao.updateCache(entry.copy(hitCount = entry.hitCount + 1))
            return entry.answer
        }
        return null
    }

    suspend fun putAnswer(query: String, answer: String, mode: String) {
        val normalized = normalize(query)
        val hash = EncryptionHelper.sha256(normalized)
        cacheDao.insertCache(
            CacheEntity(
                queryHash = hash,
                queryNormalized = normalized,
                answer = answer,
                mode = mode
            )
        )
    }

    suspend fun clear() {
        cacheDao.clearCache()
    }

    private fun normalize(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
    }
}
