package io.jishu.sdk.identity

import android.content.Context
import java.util.UUID

internal class VoterTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(): String {
        val existing = prefs.getString(KEY_VOTER_TOKEN, null)
        if (existing != null) return existing
        val newToken = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_VOTER_TOKEN, newToken).apply()
        return newToken
    }

    companion object {
        private const val PREFS_NAME = "io.jishu.sdk.prefs"
        private const val KEY_VOTER_TOKEN = "voter_token"
    }
}
