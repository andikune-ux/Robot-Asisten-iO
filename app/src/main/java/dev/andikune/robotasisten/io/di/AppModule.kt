package dev.andikune.robotasisten.io.di

import android.content.Context
import dev.andikune.robotasisten.io.data.ai.AiRepository
import dev.andikune.robotasisten.io.data.ai.CloudAi
import dev.andikune.robotasisten.io.data.ai.LibraryAi
import dev.andikune.robotasisten.io.data.ai.ModelDownloader
import dev.andikune.robotasisten.io.data.ai.OfflineAi
import dev.andikune.robotasisten.io.data.db.AppDatabase
import dev.andikune.robotasisten.io.data.repo.HistoryRepository
import dev.andikune.robotasisten.io.data.repo.RuleRepository
import dev.andikune.robotasisten.io.data.repo.SettingsRepository
import dev.andikune.robotasisten.io.data.security.ApiKeyStore
import dev.andikune.robotasisten.io.logic.AiCallQueue
import dev.andikune.robotasisten.io.logic.CacheMatcher
import dev.andikune.robotasisten.io.logic.FallbackChain
import dev.andikune.robotasisten.io.logic.RuleEngine
import dev.andikune.robotasisten.io.util.BackupHelper
import dev.andikune.robotasisten.io.util.CacheManager
import dev.andikune.robotasisten.io.util.NetworkMonitor
import dev.andikune.robotasisten.io.util.RateLimiter

/**
 * Dependency Injection container providing singleton application dependencies.
 */
object AppModule {

    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    private var ruleRepository: RuleRepository? = null

    @Volatile
    private var historyRepository: HistoryRepository? = null

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    @Volatile
    private var apiKeyStore: ApiKeyStore? = null

    @Volatile
    private var cacheManager: CacheManager? = null

    @Volatile
    private var fallbackChain: FallbackChain? = null

    @Volatile
    private var aiRepository: AiRepository? = null

    @Volatile
    private var modelDownloader: ModelDownloader? = null

    @Volatile
    private var backupHelper: BackupHelper? = null

    @Volatile
    private var networkMonitor: NetworkMonitor? = null

    fun provideDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: AppDatabase.getDatabase(context).also { database = it }
        }
    }

    fun provideRuleRepository(context: Context): RuleRepository {
        return ruleRepository ?: synchronized(this) {
            ruleRepository ?: RuleRepository(provideDatabase(context).ruleDao()).also { ruleRepository = it }
        }
    }

    fun provideHistoryRepository(context: Context): HistoryRepository {
        return historyRepository ?: synchronized(this) {
            val db = provideDatabase(context)
            historyRepository ?: HistoryRepository(db.historyDao(), db.statsDao()).also { historyRepository = it }
        }
    }

    fun provideSettingsRepository(context: Context): SettingsRepository {
        return settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext).also { settingsRepository = it }
        }
    }

    fun provideApiKeyStore(context: Context): ApiKeyStore {
        return apiKeyStore ?: synchronized(this) {
            apiKeyStore ?: ApiKeyStore(context.applicationContext).also { apiKeyStore = it }
        }
    }

    fun provideCacheManager(context: Context): CacheManager {
        return cacheManager ?: synchronized(this) {
            cacheManager ?: CacheManager(provideDatabase(context).cacheDao()).also { cacheManager = it }
        }
    }

    fun provideModelDownloader(context: Context): ModelDownloader {
        return modelDownloader ?: synchronized(this) {
            modelDownloader ?: ModelDownloader(context.applicationContext).also { modelDownloader = it }
        }
    }

    fun provideNetworkMonitor(context: Context): NetworkMonitor {
        return networkMonitor ?: synchronized(this) {
            networkMonitor ?: NetworkMonitor(context.applicationContext).also { networkMonitor = it }
        }
    }

    fun provideBackupHelper(context: Context): BackupHelper {
        return backupHelper ?: synchronized(this) {
            backupHelper ?: BackupHelper(
                provideRuleRepository(context),
                provideHistoryRepository(context)
            ).also { backupHelper = it }
        }
    }

    fun provideAiRepository(context: Context): AiRepository {
        return aiRepository ?: synchronized(this) {
            val ruleRepo = provideRuleRepository(context)
            val ruleEngine = RuleEngine(ruleRepo)
            val libraryAi = LibraryAi(ruleEngine)
            val downloader = provideModelDownloader(context)
            val offlineAi = OfflineAi(context.applicationContext, downloader)
            val keyStore = provideApiKeyStore(context)
            val rateLimiter = RateLimiter()
            val cloudAi = CloudAi(keyStore, rateLimiter)
            val settingsRepo = provideSettingsRepository(context)

            aiRepository ?: AiRepository(
                libraryAi,
                offlineAi,
                cloudAi,
                settingsRepo
            ).also { aiRepository = it }
        }
    }

    fun provideFallbackChain(context: Context): FallbackChain {
        return fallbackChain ?: synchronized(this) {
            val cacheMgr = provideCacheManager(context)
            val cacheMatcher = CacheMatcher(cacheMgr)
            val ruleRepo = provideRuleRepository(context)
            val ruleEngine = RuleEngine(ruleRepo)
            val aiRepo = provideAiRepository(context)

            fallbackChain ?: FallbackChain(
                cacheMatcher,
                ruleEngine,
                aiRepo
            ).also { fallbackChain = it }
        }
    }

    fun provideAiCallQueue(context: Context): AiCallQueue {
        return AiCallQueue(provideDatabase(context).aiQueueDao())
    }
}
