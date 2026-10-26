package dev.andikune.robotasisten.io.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Room Database for R A . i O application.
 */
@Database(
    entities = [
        RuleEntity::class,
        CacheEntity::class,
        HistoryEntity::class,
        StatsEntity::class,
        AiQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun cacheDao(): CacheDao
    abstract fun historyDao(): HistoryDao
    abstract fun statsDao(): StatsDao
    abstract fun aiQueueDao(): AiQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "raio_database.db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialRules(database.ruleDao())
                        database.statsDao().insertOrUpdate(StatsEntity(id = 1))
                    }
                }
            }

            private suspend fun populateInitialRules(ruleDao: RuleDao) {
                val sampleRules = listOf(
                    RuleEntity(questionPattern = "ibu kota indonesia", answer = "Nusantara (IKN)", explanation = "Ibu kota baru Indonesia yang dibangun di Kalimantan Timur.", category = "Geografi"),
                    RuleEntity(questionPattern = "ibu kota jepang", answer = "Tokyo", explanation = "Ibu kota dan pusat metropolitan negara Jepang.", category = "Geografi"),
                    RuleEntity(questionPattern = "ibu kota prancis", answer = "Paris", explanation = "Ibu kota Prancis terkenal dengan Menara Eiffel.", category = "Geografi"),
                    RuleEntity(questionPattern = "ibu kota amerika serikat", answer = "Washington, D.C.", explanation = "Pusat pemerintahan federal AS.", category = "Geografi"),
                    RuleEntity(questionPattern = "sila pertama pancasila", answer = "Ketuhanan yang Maha Esa", explanation = "Dilambangkan dengan bintang emas.", category = "Kewarganegaraan"),
                    RuleEntity(questionPattern = "sila kedua pancasila", answer = "Kemanusiaan yang adil dan beradab", explanation = "Dilambangkan dengan rantai emas.", category = "Kewarganegaraan"),
                    RuleEntity(questionPattern = "sila ketiga pancasila", answer = "Persatuan Indonesia", explanation = "Dilambangkan dengan pohon beringin.", category = "Kewarganegaraan"),
                    RuleEntity(questionPattern = "sila keempat pancasila", answer = "Kerakyatan yang dipimpin oleh hikmat kebijaksanaan dalam permusyawaratan/perwakilan", explanation = "Dilambangkan dengan kepala banteng.", category = "Kewarganegaraan"),
                    RuleEntity(questionPattern = "sila kelima pancasila", answer = "Keadilan sosial bagi seluruh rakyat Indonesia", explanation = "Dilambangkan dengan padi dan kapas.", category = "Kewarganegaraan"),
                    RuleEntity(questionPattern = "hari kemerdekaan indonesia", answer = "17 Agustus 1945", explanation = "Proklamasi oleh Ir. Soekarno dan Drs. Moh. Hatta.", category = "Sejarah"),
                    RuleEntity(questionPattern = "hari sumpah pemuda", answer = "28 Oktober 1928", explanation = "Kongres Pemuda II di Batavia.", category = "Sejarah"),
                    RuleEntity(questionPattern = "hari pahlawan", answer = "10 November", explanation = "Mengenang pertempuran heroik di Surabaya tahun 1945.", category = "Sejarah"),
                    RuleEntity(questionPattern = "hari kesaktian pancasila", answer = "1 Oktober", explanation = "Diperingati pasca peristiwa G30S.", category = "Sejarah"),
                    RuleEntity(questionPattern = "planet terdekat dari matahari", answer = "Merkurius", explanation = "Planet pertama dalam tata surya.", category = "Sains"),
                    RuleEntity(questionPattern = "rumus luas lingkaran", answer = "π × r²", explanation = "Pi dikali jari-jari kuadrat.", category = "Matematika"),
                    RuleEntity(questionPattern = "rumus keliling lingkaran", answer = "2 × π × r", explanation = "Dua kali pi kali jari-jari.", category = "Matematika"),
                    RuleEntity(questionPattern = "rumus luas segitiga", answer = "1/2 × alas × tinggi", explanation = "Setengah kali alas dikalikan tinggi.", category = "Matematika"),
                    RuleEntity(questionPattern = "lambang kimia air", answer = "H2O", explanation = "Dua atom hidrogen dan satu atom oksigen.", category = "Sains"),
                    RuleEntity(questionPattern = "penemu lampu pijar", answer = "Thomas Alva Edison", explanation = "Mematenkan bola lampu pijar komersial pada tahun 1879.", category = "Pengetahuan Umum"),
                    RuleEntity(questionPattern = "simbol mata uang rupiah", answer = "Rp", explanation = "Mata uang resmi Republik Indonesia.", category = "Ekonomi")
                )
                ruleDao.insertAll(sampleRules)
            }
        }
    }
}
