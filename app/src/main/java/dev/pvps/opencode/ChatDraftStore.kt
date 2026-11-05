package dev.pvps.opencode

/**
 * Keeps unsent chat text alive while the user moves between tabs.
 *
 * The map lives in the app-scoped container, not in a composable, so leaving
 * the chat screen does not erase what the user was typing. Drafts are keyed by
 * session id; a single fallback key is used before a session exists.
 */
class ChatDraftStore {
    private val drafts = mutableMapOf<String, String>()

    fun get(sessionId: String?): String =
        drafts[sessionId?.takeIf { it.isNotBlank() } ?: KEY_FALLBACK].orEmpty()

    fun set(sessionId: String?, text: String) {
        val key = sessionId?.takeIf { it.isNotBlank() } ?: KEY_FALLBACK
        if (text.isBlank()) drafts.remove(key) else drafts[key] = text
    }

    fun clear(sessionId: String?) = set(sessionId, "")

    private companion object {
        const val KEY_FALLBACK = "draft:no-session"
    }
}
