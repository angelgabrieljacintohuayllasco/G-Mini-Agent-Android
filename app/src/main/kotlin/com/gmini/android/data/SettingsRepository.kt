package com.gmini.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gmini.android.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "gmini_settings")

/** Ajustes que no son sensibles: se guardan en DataStore, no cifrados. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val surfaceDisplayText: Boolean = true,
    val surfaceTtsSpeak: Boolean = true,
    val backgroundConnectionEnabled: Boolean = false,
    val remoteServersAcknowledged: Boolean = false,
    /**
     * Pasarela SMS. Modulo opcional, apagado por defecto: con el encendido el
     * telefono reenvia los SMS entrantes al agente y expone la superficie
     * `sms.send`, que solo envia tras la confirmacion del usuario y solo en red
     * local. Ver docs/MODULOS.md.
     */
    val smsGatewayEnabled: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            surfaceDisplayText = prefs[KEY_SURFACE_DISPLAY_TEXT] ?: true,
            surfaceTtsSpeak = prefs[KEY_SURFACE_TTS_SPEAK] ?: true,
            backgroundConnectionEnabled = prefs[KEY_BACKGROUND] ?: false,
            remoteServersAcknowledged = prefs[KEY_REMOTE_ACK] ?: false,
            smsGatewayEnabled = prefs[KEY_SMS_GATEWAY] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setSurfaceDisplayText(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SURFACE_DISPLAY_TEXT] = enabled }
    }

    suspend fun setSurfaceTtsSpeak(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SURFACE_TTS_SPEAK] = enabled }
    }

    suspend fun setBackgroundConnectionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_BACKGROUND] = enabled }
    }

    suspend fun setRemoteServersAcknowledged(acknowledged: Boolean) {
        context.settingsDataStore.edit { it[KEY_REMOTE_ACK] = acknowledged }
    }

    suspend fun setSmsGatewayEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SMS_GATEWAY] = enabled }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SURFACE_DISPLAY_TEXT = booleanPreferencesKey("surface_display_text")
        val KEY_SURFACE_TTS_SPEAK = booleanPreferencesKey("surface_tts_speak")
        val KEY_BACKGROUND = booleanPreferencesKey("background_connection")
        val KEY_REMOTE_ACK = booleanPreferencesKey("remote_servers_ack")
        val KEY_SMS_GATEWAY = booleanPreferencesKey("sms_gateway_enabled")
    }
}
