package dev.andikune.robotasisten.io.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing local rule-based question & answer entries for Mode Library.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val questionPattern: String,
    val answer: String,
    val explanation: String = "",
    val category: String = "Umum",
    val isRegex: Boolean = false
)
