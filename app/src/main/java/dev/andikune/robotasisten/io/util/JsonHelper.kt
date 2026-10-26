package dev.andikune.robotasisten.io.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * JSON serialization helper using Moshi.
 */
object JsonHelper {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    inline fun <reified T> toJson(value: T): String {
        val adapter = moshi.adapter(T::class.java)
        return adapter.toJson(value)
    }

    inline fun <reified T> fromJson(json: String): T? {
        return try {
            val adapter = moshi.adapter(T::class.java)
            adapter.fromJson(json)
        } catch (e: Exception) {
            LogHelper.e("JsonHelper", "Failed to parse json", e)
            null
        }
    }
}
