package com.lockin.focus.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.SessionRecord
import com.lockin.focus.core.model.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lockin")

/**
 * All persisted state. Small enough that separate tables would be ceremony, so
 * it lives as three JSON blobs behind typed flows.
 *
 * History is hard-capped: an unbounded log in a preferences file is a slow-motion
 * OOM, and nobody scrolls past a few hundred sessions.
 */
class SettingsStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val SETTINGS = stringPreferencesKey("settings_v1")
        val SESSION = stringPreferencesKey("session_v1")
        val HISTORY = stringPreferencesKey("history_v1")
    }

    private val safePrefs: Flow<Preferences> = context.dataStore.data.catch { cause ->
        if (cause is java.io.IOException) {
            Log.w(TAG, "Unreadable preferences, starting from defaults", cause)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        } else {
            throw cause
        }
    }

    val settings: Flow<FocusSettings> = safePrefs.map { prefs ->
        prefs[Keys.SETTINGS]
            ?.let { runCatching { json.decodeFromString<FocusSettings>(it) }.getOrNull() }
            ?: FocusSettings.DEFAULT
    }

    val activeSession: Flow<SessionState?> = safePrefs.map { prefs ->
        prefs[Keys.SESSION]
            ?.let { runCatching { json.decodeFromString<SessionState>(it) }.getOrNull() }
            ?.takeIf { it.active }
    }

    val history: Flow<List<SessionRecord>> = safePrefs.map { prefs ->
        prefs[Keys.HISTORY]
            ?.let { runCatching { json.decodeFromString<List<SessionRecord>>(it) }.getOrNull() }
            .orEmpty()
    }

    suspend fun currentSettings(): FocusSettings = settings.first()

    suspend fun currentSession(): SessionState? = activeSession.first()

    /**
     * Writes the transformed settings and returns exactly what was stored.
     *
     * The return value matters: callers that need to react to a new value (the
     * live doom detector) must not read it back off the state flow, because the
     * flow collector may not have run yet and they would act on the old one.
     */
    suspend fun updateSettings(transform: (FocusSettings) -> FocusSettings): FocusSettings {
        var written: FocusSettings = FocusSettings.DEFAULT
        context.dataStore.edit { prefs ->
            val next = transform(
                prefs[Keys.SETTINGS]
                    ?.let { runCatching { json.decodeFromString<FocusSettings>(it) }.getOrNull() }
                    ?: FocusSettings.DEFAULT,
            )
            written = next
            prefs[Keys.SETTINGS] = json.encodeToString(next)
        }
        return written
    }

    suspend fun saveSession(session: SessionState?) {
        context.dataStore.edit { prefs ->
            if (session == null || !session.active) {
                prefs.remove(Keys.SESSION)
            } else {
                prefs[Keys.SESSION] = json.encodeToString(session)
            }
        }
    }

    suspend fun addRecord(record: SessionRecord) {
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.HISTORY]
                ?.let { runCatching { json.decodeFromString<List<SessionRecord>>(it) }.getOrNull() }
                .orEmpty()
            prefs[Keys.HISTORY] = json.encodeToString((listOf(record) + existing).take(MAX_HISTORY))
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(Keys.HISTORY) }
    }

    private companion object {
        const val TAG = "LockInStore"
        const val MAX_HISTORY = 300
    }
}
