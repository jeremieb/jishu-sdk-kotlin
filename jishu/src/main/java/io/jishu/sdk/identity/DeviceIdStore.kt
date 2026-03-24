package io.jishu.sdk.identity

import android.content.Context
import java.util.UUID

internal class DeviceIdStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    companion object {
        private const val PREFS_NAME = "io.jishu.sdk.prefs"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
