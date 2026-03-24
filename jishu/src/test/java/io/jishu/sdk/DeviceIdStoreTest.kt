package io.jishu.sdk

import android.content.Context
import android.content.SharedPreferences
import io.jishu.sdk.identity.DeviceIdStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeviceIdStoreTest {

    private fun makePrefs(stored: String?): Pair<SharedPreferences, SharedPreferences.Editor> {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString("device_id", null) } returns stored
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        return prefs to editor
    }

    private fun makeContext(prefs: SharedPreferences): Context {
        val ctx = mockk<Context>()
        every { ctx.getSharedPreferences(any(), any()) } returns prefs
        every { ctx.applicationContext } returns ctx
        return ctx
    }

    @Test
    fun `returns existing id without creating a new one`() {
        val (prefs, editor) = makePrefs("existing-uuid")
        val store = DeviceIdStore(makeContext(prefs))
        val id = store.getOrCreate()
        assertEquals("existing-uuid", id)
        verify(exactly = 0) { editor.putString(any(), any()) }
    }

    @Test
    fun `generates and persists a new id when none stored`() {
        val (prefs, editor) = makePrefs(null)
        val store = DeviceIdStore(makeContext(prefs))
        val id = store.getOrCreate()
        assertNotNull(id)
        val savedSlot = slot<String>()
        verify { editor.putString("device_id", capture(savedSlot)) }
        assertEquals(id, savedSlot.captured)
    }
}
