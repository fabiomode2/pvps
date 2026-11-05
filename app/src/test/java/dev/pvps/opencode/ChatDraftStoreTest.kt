package dev.pvps.opencode

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDraftStoreTest {
    @Test
    fun draftsAreKeptPerSession() {
        val store = ChatDraftStore()
        store.set("ses_a", "first")
        store.set("ses_b", "second")

        assertEquals("first", store.get("ses_a"))
        assertEquals("second", store.get("ses_b"))
    }

    @Test
    fun clearingOneSessionDoesNotTouchAnother() {
        val store = ChatDraftStore()
        store.set("ses_a", "first")
        store.set("ses_b", "second")

        store.clear("ses_a")

        assertEquals("", store.get("ses_a"))
        assertEquals("second", store.get("ses_b"))
    }

    @Test
    fun nullSessionUsesFallbackDraft() {
        val store = ChatDraftStore()
        store.set(null, "before a session exists")

        assertEquals("before a session exists", store.get(null))
        assertEquals("before a session exists", store.get(""))
    }
}
