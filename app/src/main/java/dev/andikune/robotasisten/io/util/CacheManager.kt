package dev.andikune.robotasisten.io.util

import dev.andikune.robotasisten.io.data.db.CacheDao
import dev.andikune.robotasisten.io.data.db.CacheEntity

/** Manages cached answers. */
class CacheManager(private val cacheDao: CacheDao) {
    suspend fun getCachedAnswer(query: String, mode: String? = null): String? {
        val normalized = normalize(query)
        val entry = cacheDao.getByHash(hashFor(normalized, mode))
        if (entry != null && (mode == null || entry.mode == mode)) {
            cacheDao.updateCache(entry.copy(hitCount = entry.hitCount + 1))
            return entry.answer
        }
        return null
    }

    suspend fun putAnswer(query: String, answer: String, mode: String) {
        val normalized = normalize(query)
        cacheDao.insertCache(CacheEntity(
            queryHash = hashFor(normalized, mode),
            queryNormalized = normalized,
            answer = answer,
            mode = mode
        ))
    }

    suspend fun clear() { cacheDao.clearCache() }

    private fun hashFor(normalized: String, mode: String?): String =
        EncryptionHelper.sha256(if (mode == null) normalized else "$mode:$normalized")

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ").trim()
}
