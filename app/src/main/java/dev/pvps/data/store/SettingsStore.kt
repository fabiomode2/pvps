package dev.pvps.data.store

import android.content.Context
import dev.pvps.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        // SSH state changes, saveHost and the Settings UI can update different
        // fields concurrently. Serialise the read-modify-write so toggling
        // "always-on" cannot silently revert the last model or host id.
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString(KEY, json.encodeToString(AppSettings.serializer(), next)).apply()
    }

    private fun read(): AppSettings {
        val raw = prefs.getString(KEY, null) ?: return AppSettings()
        return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrDefault(AppSettings())
    }

    companion object {
        private const val KEY = "app_settings"
    }
}
