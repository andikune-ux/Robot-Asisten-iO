package dev.andikune.robotasisten.io.util

import com.google.gson.Gson

/**
 * JSON serialization helper using Gson.
 */
object JsonHelper {
    val gson: Gson = Gson()

    inline fun <reified T> toJson(value: T): String {
        return gson.toJson(value)
    }

    inline fun <reified T> fromJson(json: String): T? {
        return try {
            gson.fromJson(json, T::class.java)
        } catch (e: Exception) {
            LogHelper.e("JsonHelper", "Failed to parse json", e)
            null
        }
    }
}
