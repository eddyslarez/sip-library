package com.eddyslarez.siplibrary.data.store

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eddyslarez.siplibrary.EddysSipLibrary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore para configuraciones persistentes de la biblioteca
 *
 * @author Eddys Larez
 */
class SettingsDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sip_settings")

    companion object {
        private val USER_AGENT_KEY = stringPreferencesKey("user_agent")
        private val DEFAULT_DOMAIN_KEY = stringPreferencesKey("default_domain")
        private val WEBSOCKET_URL_KEY = stringPreferencesKey("websocket_url")
        private val ENABLE_LOGS_KEY = booleanPreferencesKey("enable_logs")
        private val AUTO_RECONNECT_KEY = booleanPreferencesKey("auto_reconnect")
        private val PING_INTERVAL_KEY = longPreferencesKey("ping_interval")
        private val PREFERRED_AUDIO_DEVICE_KEY = stringPreferencesKey("preferred_audio_device")
        private val PUSH_PROVIDER_KEY = stringPreferencesKey("push_provider")
        private val LAST_PUSH_TOKEN_KEY = stringPreferencesKey("last_push_token")
        private val OPERATION_MODE_KEY = stringPreferencesKey("operation_mode")
    }

    /**
     * Guarda la configuración actual
     */
    suspend fun saveConfiguration(config: EddysSipLibrary.SipConfig) {
        context.dataStore.edit { preferences ->
            preferences[USER_AGENT_KEY] = config.userAgent
            preferences[DEFAULT_DOMAIN_KEY] = config.defaultDomain
            preferences[WEBSOCKET_URL_KEY] = config.webSocketUrl
            preferences[ENABLE_LOGS_KEY] = config.enableLogs
            preferences[AUTO_RECONNECT_KEY] = config.enableAutoReconnect
            preferences[PING_INTERVAL_KEY] = config.pingIntervalMs
            preferences[PREFERRED_AUDIO_DEVICE_KEY] = config.preferredAudioDevice ?: ""
            preferences[PUSH_PROVIDER_KEY] = config.defaultPushProvider
        }
    }

    /**
     * Carga la configuración guardada
     */
    suspend fun loadConfiguration(): EddysSipLibrary.SipConfig {
        val preferences = context.dataStore.data.first()

        return EddysSipLibrary.SipConfig(
            userAgent = preferences[USER_AGENT_KEY] ?: "EddysSipLibrary/1.0",
            defaultDomain = preferences[DEFAULT_DOMAIN_KEY] ?: "",
            webSocketUrl = preferences[WEBSOCKET_URL_KEY] ?: "",
            enableLogs = preferences[ENABLE_LOGS_KEY] ?: true,
            enableAutoReconnect = preferences[AUTO_RECONNECT_KEY] ?: true,
            pingIntervalMs = preferences[PING_INTERVAL_KEY] ?: 30000L,
            preferredAudioDevice = preferences[PREFERRED_AUDIO_DEVICE_KEY]?.takeIf { it.isNotEmpty() },
            defaultPushProvider = preferences[PUSH_PROVIDER_KEY] ?: "fcm"
        )
    }

    /**
     * Guarda el último token de push
     */
    suspend fun saveLastPushToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_PUSH_TOKEN_KEY] = token
        }
    }

    /**
     * Obtiene el último token de push
     */
    suspend fun getLastPushToken(): String {
        val preferences = context.dataStore.data.first()
        return preferences[LAST_PUSH_TOKEN_KEY] ?: ""
    }

    /**
     * Guarda el modo de operación actual
     */
    suspend fun saveOperationMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[OPERATION_MODE_KEY] = mode
        }
    }

    /**
     * Obtiene el modo de operación guardado
     */
    suspend fun getOperationMode(): String {
        val preferences = context.dataStore.data.first()
        return preferences[OPERATION_MODE_KEY] ?: "foreground"
    }

    /**
     * Limpia todas las configuraciones
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
///**
// * DataStore for settings persistence
// *
// * @author Eddys Larez
// */
//class SettingsDataStore(private val application: Application) {
//
//    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sip_settings")
//
//    private val IS_IN_BACKGROUND = booleanPreferencesKey("is_in_background")
//
//    suspend fun setInBackgroundValue(value: Boolean) {
//        application.dataStore.edit { preferences ->
//            preferences[IS_IN_BACKGROUND] = value
//        }
//    }
//
//    fun getInBackgroundFlow(): Flow<Boolean> {
//        return application.dataStore.data.map { preferences ->
//            preferences[IS_IN_BACKGROUND] ?: false
//        }
//    }
//}