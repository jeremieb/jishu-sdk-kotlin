package io.jishu.sdk

import android.content.Context
import android.content.SharedPreferences
import io.jishu.sdk.review.ReviewStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewStoreTest {

    private fun makeStore(): ReviewStore {
        val values = mutableMapOf<String, Any>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putLong(any(), any()) } answers {
            values[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            values[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String?>()
            if (value == null) values.remove(key) else values[key] = value
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }

        val prefs = mockk<SharedPreferences>()
        every { prefs.getLong(any(), any()) } answers { values[firstArg<String>()] as? Long ?: secondArg() }
        every { prefs.getInt(any(), any()) } answers { values[firstArg<String>()] as? Int ?: secondArg() }
        every { prefs.getString(any(), any()) } answers { values[firstArg<String>()] as? String ?: secondArg() }
        every { prefs.edit() } returns editor

        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return ReviewStore(context)
    }

    @Test
    fun `review state is scoped per app id`() {
        val store = makeStore()

        store.setInstallDateIfNeeded("app_one")
        store.incrementLaunchCount("app_one")
        store.incrementLaunchCount("app_one")
        store.recordPromptShown("app_one")
        store.incrementLaunchCount("app_two")

        assertEquals(2, store.launchCount("app_one"))
        assertEquals(1, store.promptCount("app_one"))
        assertNotNull(store.lastPromptDate("app_one"))
        assertEquals(1, store.launchCount("app_two"))
        assertEquals(0, store.promptCount("app_two"))
        assertNull(store.lastPromptDate("app_two"))
    }
}
