package dev.pvps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-aware collection: the UI stops observing when the activity is not
 * STARTED. The repositories and SSH supervisor are app-scoped, so background
 * work is unaffected; this only removes pointless recompositions behind the
 * lock screen or another app.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateValue(): T {
    val value by collectAsStateWithLifecycle()
    return value
}
