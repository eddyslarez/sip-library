package com.eddyslarez.siplibrary

import android.app.Application
import android.content.Context
import com.eddyslarez.siplibrary.core.RingtoneConfig
import com.eddyslarez.siplibrary.core.SipCoreManager
import com.eddyslarez.siplibrary.core.SipEventDispatcher
import com.eddyslarez.siplibrary.data.models.CallDirections
import com.eddyslarez.siplibrary.data.models.CallHistoryManager.CallStatistics
import com.eddyslarez.siplibrary.data.models.CallLog
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.interfaces.SipEventListener
import com.eddyslarez.siplibrary.utils.StateManager
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

/**
 * EddysSipLibrary - Biblioteca SIP/VoIP optimizada para Android
 *
 * Versión mejorada con interfaces, múltiples listeners y mejor arquitectura
 *
 * @author Eddys Larez
 * @version 2.0.0
 */
class EddysSipLibrary private constructor() {

    private var sipCoreManager: SipCoreManager? = null
    private var isInitialized = false
    private lateinit var config: SipConfig
    private val eventDispatcher = SipEventDispatcher()
    private var lifecycleManager: AppLifecycleManager? = null

    companion object {
        @Volatile
        private var INSTANCE: EddysSipLibrary? = null
        private const val TAG = "EddysSipLibrary"

        /**
         * Obtiene la instancia singleton de la biblioteca
         */
        fun getInstance(): EddysSipLibrary {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EddysSipLibrary().also { INSTANCE = it }
            }
        }
    }

    /**
     * Configuración completa de la biblioteca con soporte para ringtone
     */
    data class SipConfig(
        val defaultDomain: String = "",
        val webSocketUrl: String = "",
        val userAgent: String = "EddysSipLibrary/2.0.0",
        val enableLogs: Boolean = true,
        val enableAutoReconnect: Boolean = true,
        val pingIntervalMs: Long = 30000L,
        val registrationExpiresSeconds: Int = 3600,

        // Configuración automática de push
        val autoEnterPushOnBackground: Boolean = true,
        val autoExitPushOnForeground: Boolean = true,
        val autoDisconnectWebSocketOnBackground: Boolean = false,
        val pushReconnectDelayMs: Long = 2000L,

        // Configuración de audio
        val autoSelectAudioDevice: Boolean = true,
        val preferredAudioDevice: AudioDeviceType = AudioDeviceType.EARPIECE,
        val enableEchoCancellation: Boolean = true,
        val enableNoiseSuppression: Boolean = true,

        // Configuración de llamadas
        val autoAcceptDelay: Long = 0L, // 0 = manual, >0 = auto accept after delay
        val callTimeoutSeconds: Int = 60,
        val enableCallRecording: Boolean = false,

        // Configuración de ringtone
        val ringtoneConfig: RingtoneConfig = RingtoneConfig(),

        // Headers personalizados
        val customHeaders: Map<String, String> = emptyMap(),
        val customContactParams: Map<String, String> = emptyMap()
    )

    /**
     * Tipos de dispositivos de audio
     */
    enum class AudioDeviceType {
        EARPIECE,
        SPEAKER,
        BLUETOOTH,
        WIRED_HEADSET,
        AUTO
    }

    /**
     * Estados de la aplicación
     */
    enum class AppState {
        FOREGROUND,
        BACKGROUND,
        TERMINATED
    }

    /**
     * Razones de finalización de llamada
     */
    enum class CallEndReason {
        USER_HANGUP,
        REMOTE_HANGUP,
        BUSY,
        DECLINED,
        TIMEOUT,
        NETWORK_ERROR,
        SERVER_ERROR,
        CANCELLED
    }

    /**
     * Calidad de red
     */
    data class NetworkQuality(
        val score: Float, // 0.0 - 1.0
        val latency: Long,
        val packetLoss: Float,
        val jitter: Long
    )

    /**
     * Errores de SIP
     */
    data class SipError(
        val code: Int,
        val message: String,
        val category: ErrorCategory,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    )

    /**
     * Advertencias de SIP
     */
    data class SipWarning(
        val message: String,
        val category: WarningCategory,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    )

    enum class ErrorCategory {
        NETWORK,
        AUTHENTICATION,
        AUDIO,
        SIP_PROTOCOL,
        WEBRTC,
        CONFIGURATION
    }

    enum class WarningCategory {
        AUDIO_QUALITY,
        NETWORK_QUALITY,
        BATTERY_OPTIMIZATION,
        PERMISSION
    }

    /**
     * Inicializa la biblioteca SIP
     *
     * @param application Instancia de la aplicación Android
     * @param config Configuración de la biblioteca
     */
    fun initialize(
        application: Application,
        config: SipConfig = SipConfig()
    ) {
        if (isInitialized) {
            log.w(tag = TAG) { "Library already initialized" }
            return
        }

        try {
            log.d(tag = TAG) { "Initializing EddysSipLibrary v2.0.0 by Eddys Larez" }

            this.config = config

            // Inicializar gestor de ciclo de vida
            lifecycleManager = AppLifecycleManager(application, config, eventDispatcher)

            // Crear Enhanced SipCoreManager
            sipCoreManager = SipCoreManager.createInstance(application, config, eventDispatcher)
            sipCoreManager?.initialize()

            // Configurar callbacks internos
            setupInternalCallbacks()

            isInitialized = true
            log.d(tag = TAG) { "EddysSipLibrary initialized successfully" }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error initializing library: ${e.message}" }
            CoroutineScope(Dispatchers.IO).launch {
                eventDispatcher.onError(SipError(
                    code = -1,
                    message = "Failed to initialize library: ${e.message}",
                    category = ErrorCategory.CONFIGURATION
                ))
            }
            throw SipLibraryException("Failed to initialize library", e)
        }
    }

    // ============ GESTIÓN DE LISTENERS ============

    /**
     * Agrega un listener para eventos SIP
     */
    suspend fun addEventListener(listener: SipEventListener) {
        checkInitialized()
        eventDispatcher.addListener(listener)
        log.d(tag = TAG) { "Event listener added" }
    }

    /**
     * Remueve un listener de eventos SIP
     */
    suspend fun removeEventListener(listener: SipEventListener) {
        checkInitialized()
        eventDispatcher.removeListener(listener)
        log.d(tag = TAG) { "Event listener removed" }
    }

    /**
     * Limpia todos los listeners
     */
    suspend fun clearEventListeners() {
        checkInitialized()
        eventDispatcher.clearListeners()
        log.d(tag = TAG) { "All event listeners cleared" }
    }

    /**
     * Obtiene la cantidad de listeners registrados
     */
    suspend fun getListenerCount(): Int {
        checkInitialized()
        return eventDispatcher.getListenerCount()
    }

    // ============ GESTIÓN DE LLAMADAS (usando CallManager interface) ============

    /**
     * Realiza una llamada
     */
    suspend fun makeCall(
        phoneNumber: String,
        username: String? = null,
        domain: String? = null,
        customHeaders: Map<String, String> = emptyMap()
    ) {
        checkInitialized()

        val finalUsername = username ?: sipCoreManager?.getCurrentUsername()
        val finalDomain = domain ?: config.defaultDomain

        if (finalUsername == null) {
            val error = SipError(
                code = 1001,
                message = "No registered account available for calling",
                category = ErrorCategory.CONFIGURATION
            )
            eventDispatcher.onError(error)
            throw SipLibraryException("No registered account available for calling")
        }

        sipCoreManager?.makeCall(phoneNumber, finalUsername, finalDomain, customHeaders)
    }

    /**
     * Acepta una llamada entrante
     */
    suspend fun acceptCall() {
        checkInitialized()
        sipCoreManager?.acceptCall()
    }

    /**
     * Rechaza una llamada entrante
     */
    suspend fun declineCall() {
        checkInitialized()
        sipCoreManager?.declineCall()
    }

    /**
     * Termina la llamada actual
     */
    suspend fun endCall() {
        checkInitialized()
        sipCoreManager?.endCall()
    }

    /**
     * Pone en espera la llamada actual
     */
    suspend fun holdCall() {
        checkInitialized()
        sipCoreManager?.holdCall()
    }

    /**
     * Reanuda una llamada en espera
     */
    suspend fun resumeCall() {
        checkInitialized()
        sipCoreManager?.resumeCall()
    }

    // ============ GESTIÓN DE AUDIO ============

    /**
     * Obtiene todos los dispositivos de audio disponibles
     */
    suspend fun getAudioDevices(): Pair<List<AudioDevice>, List<AudioDevice>> {
        checkInitialized()
        val audioManager = sipCoreManager?.getAudioManager()
        val inputDevices = audioManager?.getAudioInputDevices() ?: emptyList()
        val outputDevices = audioManager?.getAudioOutputDevices() ?: emptyList()

        eventDispatcher.onAudioDevicesAvailable(inputDevices, outputDevices)
        return Pair(inputDevices, outputDevices)
    }

    /**
     * Cambia el dispositivo de audio de entrada
     */
    fun changeAudioInputDevice(device: AudioDevice): Boolean {
        checkInitialized()
        val audioManager = sipCoreManager?.getAudioManager()
        val oldDevice = audioManager?.getCurrentInputDevice()
        val success = audioManager?.changeAudioInputDevice(device) ?: false
        if (success) {
            CoroutineScope(Dispatchers.IO).launch {
                eventDispatcher.onAudioDeviceChanged(oldDevice, device)
            }
        }
        return success
    }

    /**
     * Cambia el dispositivo de audio de salida
     */
    fun changeAudioOutputDevice(device: AudioDevice): Boolean {
        checkInitialized()
        val audioManager = sipCoreManager?.getAudioManager()
        val oldDevice = audioManager?.getCurrentOutputDevice()
        val success = audioManager?.changeAudioOutputDevice(device) ?: false
        if (success) {
            CoroutineScope(Dispatchers.IO).launch {
                eventDispatcher.onAudioDeviceChanged(oldDevice, device)
            }
        }
        return success
    }

    /**
     * Obtiene el dispositivo de entrada actual
     */
    fun getCurrentInputDevice(): AudioDevice? {
        checkInitialized()
        return sipCoreManager?.getAudioManager()?.getCurrentInputDevice()
    }

    /**
     * Obtiene el dispositivo de salida actual
     */
    fun getCurrentOutputDevice(): AudioDevice? {
        checkInitialized()
        return sipCoreManager?.getAudioManager()?.getCurrentOutputDevice()
    }

    /**
     * Alterna el estado de mute del micrófono
     */
    fun toggleMute() {
        checkInitialized()
        val audioManager = sipCoreManager?.getAudioManager()
        val wasMuted = audioManager?.isMuted() ?: false
        audioManager?.setMuted(!wasMuted)

        CoroutineScope(Dispatchers.IO).launch {
            eventDispatcher.onMuteStateChanged(!wasMuted)
        }
    }

    /**
     * Verifica si el micrófono está en mute
     */
    fun isMuted(): Boolean {
        checkInitialized()
        return sipCoreManager?.getAudioManager()?.isMuted() ?: false
    }

    /**
     * Diagnostica problemas de audio
     */
    fun diagnoseAudioIssues(): String {
        checkInitialized()
        return sipCoreManager?.getAudioManager()?.diagnoseAudioIssues() ?: "Audio manager not available"
    }

    // ============ GESTIÓN DE RINGTONE ============

    /**
     * Actualiza la configuración de ringtone
     */
    fun updateRingtoneConfig(ringtoneConfig: RingtoneConfig) {
        checkInitialized()
        sipCoreManager?.updateRingtoneConfig(ringtoneConfig)

        // Update config
        this.config = this.config.copy(ringtoneConfig = ringtoneConfig)
    }

    /**
     * Habilita o deshabilita los ringtones
     */
    fun setRingtoneEnabled(enabled: Boolean) {
        checkInitialized()
        sipCoreManager?.getRingtoneManager()?.setRingtoneEnabled(enabled)
    }

    /**
     * Verifica si los ringtones están habilitados
     */
    fun isRingtoneEnabled(): Boolean {
        checkInitialized()
        return sipCoreManager?.getRingtoneManager()?.isRingtoneEnabled() ?: true
    }

    /**
     * Detiene todos los ringtones manualmente
     */
    fun stopAllRingtones() {
        checkInitialized()
        sipCoreManager?.getRingtoneManager()?.stopAllRingtones()
    }

    // ============ DTMF ============

    /**
     * Envía un dígito DTMF
     */
    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
        checkInitialized()
        return sipCoreManager?.sendDtmf(digit, duration) ?: false
    }

    /**
     * Envía una secuencia de dígitos DTMF
     */
    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
        checkInitialized()
        return sipCoreManager?.sendDtmfSequence(digits, duration) ?: false
    }

    // ============ ESTADO Y FLUJOS REACTIVOS ============

    /**
     * Obtiene el estado actual de la llamada
     */
    fun getCurrentCallState(): CallState {
        checkInitialized()
        return StateManager.getCurrentCallState()
    }

    /**
     * Obtiene el estado actual de registro
     */
    fun getRegistrationState(): RegistrationState {
        checkInitialized()
        return StateManager.getCurrentRegistrationState()
    }

    /**
     * Verifica si hay una llamada activa
     */
    fun hasActiveCall(): Boolean {
        checkInitialized()
        return sipCoreManager?.hasActiveCall() ?: false
    }

    /**
     * Verifica si hay una llamada conectada
     */
    fun isCallConnected(): Boolean {
        checkInitialized()
        return sipCoreManager?.isCallConnected() ?: false
    }

    /**
     * Obtiene el flow reactivo del estado de llamada
     */
    fun getCallStateFlow(): Flow<CallState> {
        checkInitialized()
        return StateManager.callStateFlow
    }

    /**
     * Obtiene el flow reactivo del estado de registro
     */
    fun getRegistrationStateFlow(): Flow<RegistrationState> {
        checkInitialized()
        return StateManager.registrationStateFlow
    }

    /**
     * Obtiene el flow reactivo del estado de audio
     */
    fun getAudioStateFlow(): Flow<StateManager.AudioState> {
        checkInitialized()
        return StateManager.audioStateFlow
    }

    /**
     * Obtiene el flow reactivo del estado de ringtone
     */
    fun getRingtoneStateFlow(): Flow<StateManager.RingtoneState> {
        checkInitialized()
        return StateManager.ringtoneStateFlow
    }

    /**
     * Obtiene el flow reactivo del estado de red
     */
    fun getNetworkStateFlow(): Flow<StateManager.NetworkState> {
        checkInitialized()
        return StateManager.networkStateFlow
    }

    // ============ REGISTRO Y CONEXIÓN ============

    /**
     * Registra una cuenta SIP
     */
    fun registerAccount(
        username: String,
        password: String,
        domain: String? = null,
        pushToken: String? = null,
        pushProvider: String = "fcm",
        customHeaders: Map<String, String> = emptyMap(),
        expires: Int? = null
    ) {
        checkInitialized()

        val finalDomain = domain ?: config.defaultDomain
        val finalExpires = expires ?: config.registrationExpiresSeconds
        val finalHeaders = config.customHeaders + customHeaders

        sipCoreManager?.register(
            username = username,
            password = password,
            domain = finalDomain,
            provider = pushProvider,
            token = pushToken ?: "",
            customHeaders = finalHeaders,
            expires = finalExpires
        )
    }

    /**
     * Desregistra una cuenta SIP
     */
    fun unregisterAccount(username: String, domain: String? = null) {
        checkInitialized()
        val finalDomain = domain ?: config.defaultDomain
        // Implementation depends on SipCoreManager
    }

    // ============ CONFIGURACIÓN Y MODO PUSH ============

    /**
     * Actualiza la configuración de push notifications
     */
    fun updatePushConfiguration(
        token: String,
        provider: String = "fcm",
        customParams: Map<String, String> = emptyMap()
    ) {
        checkInitialized()
        CoroutineScope(Dispatchers.IO).launch {
            eventDispatcher.onPushTokenUpdated(token, provider)
        }
    }

    /**
     * Entra en modo push
     */
    fun enterPushMode(reason: String = "Manual") {
        checkInitialized()
        sipCoreManager?.enterPushMode(reason)
    }

    /**
     * Sale del modo push
     */
    fun exitPushMode(reason: String = "Manual") {
        checkInitialized()
        sipCoreManager?.exitPushMode(reason)
    }

    /**
     * Actualiza el user agent
     */
    fun updateUserAgent(newUserAgent: String) {
        checkInitialized()
        // Implementation depends on SipCoreManager
    }

    /**
     * Obtiene configuración actual
     */
    fun getCurrentConfig(): SipConfig = config

    /**
     * Actualiza configuración dinámicamente
     */
    fun updateConfig(newConfig: SipConfig) {
        checkInitialized()
        this.config = newConfig
        lifecycleManager?.updateConfig(newConfig)
    }

    // ============ ESTADÍSTICAS Y CALIDAD ============

    /**
     * Obtiene estadísticas de la llamada actual
     */
    fun getCurrentCallStatistics(): CallStatistics? {
        checkInitialized()
        return sipCoreManager?.getCurrentCallStatistics() as CallStatistics?
    }

    /**
     * Obtiene calidad de red actual
     */
    fun getNetworkQuality(): NetworkQuality? {
        checkInitialized()
        return sipCoreManager?.getNetworkQuality()
    }

    // ============ HISTORIAL DE LLAMADAS ============

    /**
     * Obtiene el historial de llamadas
     */
    fun getCallLogs(): List<CallLog> {
        checkInitialized()
        return sipCoreManager?.callHistoryManager?.getAllCallLogs() ?: emptyList()
    }

    /**
     * Limpia el historial de llamadas
     */
    fun clearCallLogs() {
        checkInitialized()
        sipCoreManager?.callHistoryManager?.clearCallLogs()
    }

    // ============ DIAGNÓSTICO Y SALUD ============

    /**
     * Obtiene reporte de salud del sistema
     */
    fun getSystemHealthReport(): String {
        checkInitialized()
        return buildString {
            appendLine("=== EddysSipLibrary v2.0.0 Health Report ===")
            appendLine("Initialized: $isInitialized")
            appendLine("Active Listeners: ${runBlocking { eventDispatcher.getListenerCount() }}")
            appendLine("Call State: ${getCurrentCallState()}")
            appendLine("Registration State: ${getRegistrationState()}")
            appendLine("Ringtone Enabled: ${isRingtoneEnabled()}")
            appendLine("Audio Muted: ${isMuted()}")
            appendLine("")
            appendLine("=== Audio Diagnosis ===")
            appendLine(diagnoseAudioIssues())
        }
    }

    /**
     * Verifica si el sistema está saludable
     */
    fun isSystemHealthy(): Boolean {
        checkInitialized()
        return try {
            isInitialized &&
                    sipCoreManager != null &&
                    runBlocking { eventDispatcher.getListenerCount() } >= 0
        } catch (e: Exception) {
            false
        }
    }

    // ============ MÉTODOS PRIVADOS ============

    private fun checkInitialized() {
        if (!isInitialized || sipCoreManager == null) {
            throw SipLibraryException("Library not initialized. Call initialize() first.")
        }
    }

    private fun setupInternalCallbacks() {
        sipCoreManager?.onCallTerminated = {
            CoroutineScope(Dispatchers.IO).launch {
                eventDispatcher.onCallDisconnected(
                    callId = StateManager.getCurrentCallId(),
                    reason = CallEndReason.USER_HANGUP,
                    duration = StateManager.getCurrentCallDuration()
                )
            }
        }
    }

    /**
     * Libera recursos de la biblioteca
     */
    fun dispose() {
        if (isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                eventDispatcher.clearListeners()
            }
            lifecycleManager?.dispose()
            sipCoreManager?.dispose()
            sipCoreManager = null
            lifecycleManager = null
            isInitialized = false
            log.d(tag = TAG) { "EddysSipLibrary disposed" }
        }
    }

    /**
     * Excepción personalizada para la biblioteca
     */
    class SipLibraryException(message: String, cause: Throwable? = null) : Exception(message, cause)
}


//
///**
// * EddysSipLibrary - Biblioteca SIP/VoIP para Android
// *
// * Biblioteca desarrollada por Eddys Larez para manejo de llamadas SIP/VoIP
// * con soporte para WebRTC y WebSocket.
// *
// * @author Eddys Larez
// * @version 1.0.0
// */
//class EddysSipLibrary private constructor() {
//
//    private var sipCoreManager: SipCoreManager? = null
//    private var isInitialized = false
//    private lateinit var config: SipConfig
//    private var eventListener: SipEventListener? = null
//    private var lifecycleManager: AppLifecycleManager? = null
//
//    companion object {
//        @Volatile
//        private var INSTANCE: EddysSipLibrary? = null
//        private const val TAG = "EddysSipLibrary"
//
//        /**
//         * Obtiene la instancia singleton de la biblioteca
//         */
//        fun getInstance(): EddysSipLibrary {
//            return INSTANCE ?: synchronized(this) {
//                INSTANCE ?: EddysSipLibrary().also { INSTANCE = it }
//            }
//        }
//    }
//
//    /**
//     * Configuración completa de la biblioteca
//     */
//    data class SipConfig(
//        val defaultDomain: String = "",
//        val webSocketUrl: String = "",
//        val userAgent: String = "EddysSipLibrary/1.0.0",
//        val enableLogs: Boolean = true,
//        val enableAutoReconnect: Boolean = true,
//        val pingIntervalMs: Long = 30000L,
//        val registrationExpiresSeconds: Int = 3600,
//
//        // Configuración automática de push
//        val autoEnterPushOnBackground: Boolean = true,
//        val autoExitPushOnForeground: Boolean = true,
//        val autoDisconnectWebSocketOnBackground: Boolean = false,
//        val pushReconnectDelayMs: Long = 2000L,
//
//        // Configuración de audio
//        val autoSelectAudioDevice: Boolean = true,
//        val preferredAudioDevice: AudioDeviceType = AudioDeviceType.EARPIECE,
//        val enableEchoCancellation: Boolean = true,
//        val enableNoiseSuppression: Boolean = true,
//
//        // Configuración de llamadas
//        val autoAcceptDelay: Long = 0L, // 0 = manual, >0 = auto accept after delay
//        val callTimeoutSeconds: Int = 60,
//        val enableCallRecording: Boolean = false,
//
//        // Headers personalizados
//        val customHeaders: Map<String, String> = emptyMap(),
//        val customContactParams: Map<String, String> = emptyMap()
//    )
//
//    /**
//     * Tipos de dispositivos de audio
//     */
//    enum class AudioDeviceType {
//        EARPIECE,
//        SPEAKER,
//        BLUETOOTH,
//        WIRED_HEADSET,
//        AUTO
//    }
//
//    /**
//     * Interface principal para eventos de la biblioteca
//     */
//    interface SipEventListener {
//
//        // Eventos de registro
//        fun onRegistrationStateChanged(state: RegistrationState, account: String) {}
//        fun onRegistrationSuccess(account: String, expiresIn: Int) {}
//        fun onRegistrationFailed(account: String, reason: String) {}
//
//        // Eventos de llamadas
//        fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {}
//        fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) {}
//        fun onCallConnected(callId: String, duration: Long = 0) {}
//        fun onCallDisconnected(callId: String, reason: CallEndReason, duration: Long) {}
//        fun onCallFailed(callId: String, error: String, errorCode: Int = -1) {}
//        fun onCallHold(callId: String, isOnHold: Boolean) {}
//
//        // Eventos de audio
//        fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {}
//        fun onAudioDevicesAvailable(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {}
//        fun onMuteStateChanged(isMuted: Boolean) {}
//        fun onAudioLevelChanged(level: Float) {} // 0.0 - 1.0
//
//        // Eventos de conectividad
//        fun onNetworkStateChanged(isConnected: Boolean, networkType: String) {}
//        fun onWebSocketStateChanged(isConnected: Boolean, url: String) {}
//        fun onReconnectionAttempt(attempt: Int, maxAttempts: Int) {}
//
//        // Eventos de modo push
//        fun onPushModeChanged(isInPushMode: Boolean, reason: String) {}
//        fun onPushTokenUpdated(token: String, provider: String) {}
//
//        // Eventos de ciclo de vida de la app
//        fun onAppStateChanged(appState: AppState, previousState: AppState) {}
//
//        // Eventos de mensajería SIP
//        fun onSipMessageReceived(message: String, messageType: String) {}
//        fun onSipMessageSent(message: String, messageType: String) {}
//
//        // Eventos de estadísticas
//        fun onCallStatistics(stats: CallStatistics) {}
//        fun onNetworkQuality(quality: NetworkQuality) {}
//
//        // Eventos de errores
//        fun onError(error: SipError) {}
//        fun onWarning(warning: SipWarning) {}
//    }
//
//    /**
//     * Estados de la aplicación
//     */
//    enum class AppState {
//        FOREGROUND,
//        BACKGROUND,
//        TERMINATED
//    }
//
//    /**
//     * Razones de finalización de llamada
//     */
//    enum class CallEndReason {
//        USER_HANGUP,
//        REMOTE_HANGUP,
//        BUSY,
//        DECLINED,
//        TIMEOUT,
//        NETWORK_ERROR,
//        SERVER_ERROR,
//        CANCELLED
//    }
//
//    /**
//     * Calidad de red
//     */
//    data class NetworkQuality(
//        val score: Float, // 0.0 - 1.0
//        val latency: Long,
//        val packetLoss: Float,
//        val jitter: Long
//    )
//
//    /**
//     * Errores de SIP
//     */
//    data class SipError(
//        val code: Int,
//        val message: String,
//        val category: ErrorCategory,
//        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
//    )
//
//    /**
//     * Advertencias de SIP
//     */
//    data class SipWarning(
//        val message: String,
//        val category: WarningCategory,
//        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
//    )
//
//    enum class ErrorCategory {
//        NETWORK,
//        AUTHENTICATION,
//        AUDIO,
//        SIP_PROTOCOL,
//        WEBRTC,
//        CONFIGURATION
//    }
//
//    enum class WarningCategory {
//        AUDIO_QUALITY,
//        NETWORK_QUALITY,
//        BATTERY_OPTIMIZATION,
//        PERMISSION
//    }
//
//    /**
//     * Inicializa la biblioteca SIP
//     *
//     * @param application Instancia de la aplicación Android
//     * @param config Configuración de la biblioteca
//     * @param eventListener Listener para eventos (opcional)
//     */
//    fun initialize(
//        application: Application,
//        config: SipConfig = SipConfig(),
//        eventListener: SipEventListener? = null
//    ) {
//        if (isInitialized) {
//            log.w(tag = TAG) { "Library already initialized" }
//            return
//        }
//
//        try {
//            log.d(tag = TAG) { "Initializing EddysSipLibrary v1.0.0 by Eddys Larez" }
//
//            this.config = config
//            this.eventListener = eventListener
//
//            // Inicializar gestor de ciclo de vida
//            lifecycleManager = AppLifecycleManager(application, config, eventListener)
//
//            // Crear SipCoreManager con configuración mejorada
//            sipCoreManager = SipCoreManager.createInstance(application, config, eventListener)
//            sipCoreManager?.initialize()
//
//            // Configurar callbacks internos
//            setupInternalCallbacks()
//
//            isInitialized = true
//            log.d(tag = TAG) { "EddysSipLibrary initialized successfully" }
//
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error initializing library: ${e.message}" }
//            eventListener?.onError(SipError(
//                code = -1,
//                message = "Failed to initialize library: ${e.message}",
//                category = ErrorCategory.CONFIGURATION
//            ))
//            throw SipLibraryException("Failed to initialize library", e)
//        }
//    }
//
//    /**
//     * Establece un listener para eventos
//     */
//    fun setEventListener(listener: SipEventListener) {
//        this.eventListener = listener
//        sipCoreManager?.setEventListener(listener)
//        lifecycleManager?.setEventListener(listener)
//    }
//
//    /**
//     * Registra una cuenta SIP con configuración extendida
//     */
//    fun registerAccount(
//        username: String,
//        password: String,
//        domain: String? = null,
//        pushToken: String? = null,
//        pushProvider: String = "fcm",
//        customHeaders: Map<String, String> = emptyMap(),
//        expires: Int? = null
//    ) {
//        checkInitialized()
//
//        val finalDomain = domain ?: config.defaultDomain
//        val finalExpires = expires ?: config.registrationExpiresSeconds
//        val finalHeaders = config.customHeaders + customHeaders
//
//        sipCoreManager?.register(
//            username = username,
//            password = password,
//            domain = finalDomain,
//            provider = pushProvider,
//            token = pushToken ?: "",
//            customHeaders = finalHeaders,
//            expires = finalExpires
//        )
//    }
//
//    /**
//     * Actualiza la configuración de push notifications
//     */
//    fun updatePushConfiguration(
//        token: String,
//        provider: String = "fcm",
//        customParams: Map<String, String> = emptyMap()
//    ) {
//        checkInitialized()
//        sipCoreManager?.updatePushConfiguration(token, provider, customParams)
//        eventListener?.onPushTokenUpdated(token, provider)
//    }
//
//    /**
//     * Cambia el user agent dinámicamente
//     */
//    fun updateUserAgent(newUserAgent: String) {
//        checkInitialized()
//        sipCoreManager?.updateUserAgent(newUserAgent)
//    }
//
//    /**
//     * Cambia el dispositivo de audio con configuración automática
//     */
//    fun changeAudioDevice(deviceType: AudioDeviceType): Boolean {
//        checkInitialized()
//        return sipCoreManager?.changeAudioDeviceByType(deviceType) ?: false
//    }
//
//    /**
//     * Obtiene estadísticas de la llamada actual
//     */
//    fun getCurrentCallStatistics(): CallStatistics? {
//        checkInitialized()
//        return sipCoreManager?.getCurrentCallStatistics()
//    }
//
//    /**
//     * Obtiene calidad de red actual
//     */
//    fun getNetworkQuality(): NetworkQuality? {
//        checkInitialized()
//        return sipCoreManager?.getNetworkQuality()
//    }
//
//    /**
//     * Habilita/deshabilita el modo push automático
//     */
//    fun setAutoPushMode(enabled: Boolean) {
//        checkInitialized()
//        lifecycleManager?.setAutoPushMode(enabled)
//    }
//
//    /**
//     * Fuerza el cambio a modo push
//     */
//    fun enterPushMode(reason: String = "Manual") {
//        checkInitialized()
//        sipCoreManager?.enterPushMode()
//        eventListener?.onPushModeChanged(true, reason)
//    }
//
//    /**
//     * Fuerza el cambio a modo foreground
//     */
//    fun exitPushMode(reason: String = "Manual") {
//        checkInitialized()
//        sipCoreManager?.exitPushMode()
//        eventListener?.onPushModeChanged(false, reason)
//    }
//
//    /**
//     * Obtiene configuración actual
//     */
//    fun getCurrentConfig(): SipConfig = config
//
//    /**
//     * Actualiza configuración dinámicamente
//     */
//    fun updateConfig(newConfig: SipConfig) {
//        checkInitialized()
//        this.config = newConfig
//        sipCoreManager?.updateConfig(newConfig)
//        lifecycleManager?.updateConfig(newConfig)
//    }
//
//    // ============ MÉTODOS EXISTENTES MEJORADOS ============
//
//    fun unregisterAccount(username: String, domain: String? = null) {
//        checkInitialized()
//        val finalDomain = domain ?: config.defaultDomain
//        sipCoreManager?.unregister(username, finalDomain)
//    }
//
//    fun makeCall(
//        phoneNumber: String,
//        username: String? = null,
//        domain: String? = null,
//        customHeaders: Map<String, String> = emptyMap()
//    ) {
//        checkInitialized()
//
//        val finalUsername = username ?: sipCoreManager?.getCurrentUsername()
//        val finalDomain = domain ?: config.defaultDomain
//
//        if (finalUsername == null) {
//            val error = SipError(
//                code = 1001,
//                message = "No registered account available for calling",
//                category = ErrorCategory.CONFIGURATION
//            )
//            eventListener?.onError(error)
//            throw SipLibraryException("No registered account available for calling")
//        }
//
//        sipCoreManager?.makeCall(phoneNumber, finalUsername, finalDomain, customHeaders)
//    }
//
//    fun acceptCall(autoAnswer: Boolean = false) {
//        checkInitialized()
//        sipCoreManager?.acceptCall()
//    }
//
//    fun declineCall(reason: String = "Declined") {
//        checkInitialized()
//        sipCoreManager?.declineCall()
//    }
//
//    fun endCall() {
//        checkInitialized()
//        sipCoreManager?.endCall()
//    }
//
//    fun holdCall() {
//        checkInitialized()
//        sipCoreManager?.holdCall()
//    }
//
//    fun resumeCall() {
//        checkInitialized()
//        sipCoreManager?.resumeCall()
//    }
//
//    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
//        checkInitialized()
//        return sipCoreManager?.sendDtmf(digit, duration) ?: false
//    }
//
//    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
//        checkInitialized()
//        return sipCoreManager?.sendDtmfSequence(digits, duration) ?: false
//    }
//
//    fun toggleMute() {
//        checkInitialized()
//        sipCoreManager?.mute()
//        eventListener?.onMuteStateChanged(isMuted())
//    }
//
//    fun isMuted(): Boolean {
//        checkInitialized()
//        return sipCoreManager?.webRtcManager?.isMuted() ?: false
//    }
//
//    fun getAudioDevices(): Pair<List<AudioDevice>, List<AudioDevice>> {
//        checkInitialized()
//        val devices = sipCoreManager?.webRtcManager?.getAllAudioDevices() ?: Pair(emptyList(), emptyList())
//        eventListener?.onAudioDevicesAvailable(devices.first, devices.second)
//        return devices
//    }
//
//    fun changeAudioOutput(device: AudioDevice): Boolean {
//        checkInitialized()
//        val oldDevice = sipCoreManager?.webRtcManager?.getCurrentOutputDevice()
//        val success = sipCoreManager?.webRtcManager?.changeAudioOutputDeviceDuringCall(device) ?: false
//        if (success) {
//            eventListener?.onAudioDeviceChanged(oldDevice, device)
//        }
//        return success
//    }
//
//    fun getCallLogs(): List<CallLog> {
//        checkInitialized()
//        return sipCoreManager?.callLogs() ?: emptyList()
//    }
//
//    fun clearCallLogs() {
//        checkInitialized()
//        sipCoreManager?.clearCallLogs()
//    }
//
//    fun getCurrentCallState(): CallState {
//        checkInitialized()
//        return sipCoreManager?.callState ?: CallState.NONE
//    }
//
//    fun getRegistrationState(): RegistrationState {
//        checkInitialized()
//        return sipCoreManager?.getRegistrationState() ?: RegistrationState.NONE
//    }
//
//    fun hasActiveCall(): Boolean {
//        checkInitialized()
//        return sipCoreManager?.currentCall() ?: false
//    }
//
//    fun isCallConnected(): Boolean {
//        checkInitialized()
//        return sipCoreManager?.currentCallConnected() ?: false
//    }
//
//    fun getCallStateFlow(): Flow<CallState> {
//        checkInitialized()
//        return CallStateManager.callStateFlow
//    }
//
//    fun getRegistrationStateFlow(): Flow<RegistrationState> {
//        checkInitialized()
//        return RegistrationStateManager.registrationStateFlow
//    }
//
//    fun updatePushToken(token: String, provider: String = "fcm") {
//        checkInitialized()
//        sipCoreManager?.enterPushMode(token)
//        eventListener?.onPushTokenUpdated(token, provider)
//    }
//
//    fun getSystemHealthReport(): String {
//        checkInitialized()
//        return sipCoreManager?.getSystemHealthReport() ?: "Library not initialized"
//    }
//
//    fun isSystemHealthy(): Boolean {
//        checkInitialized()
//        return sipCoreManager?.isSipCoreManagerHealthy() ?: false
//    }
//
//    /**
//     * Configura callbacks para eventos de la biblioteca (método legacy)
//     */
//    @Deprecated("Use setEventListener instead", ReplaceWith("setEventListener"))
//    fun setCallbacks(callbacks: SipCallbacks) {
//        checkInitialized()
//        sipCoreManager?.onCallTerminated = { callbacks.onCallTerminated() }
//    }
//
//    fun dispose() {
//        if (isInitialized) {
//            lifecycleManager?.dispose()
//            sipCoreManager?.dispose()
//            sipCoreManager = null
//            lifecycleManager = null
//            eventListener = null
//            isInitialized = false
//            log.d(tag = TAG) { "EddysSipLibrary disposed" }
//        }
//    }
//
//    private fun checkInitialized() {
//        if (!isInitialized || sipCoreManager == null) {
//            throw SipLibraryException("Library not initialized. Call initialize() first.")
//        }
//    }
//
//    private fun setupInternalCallbacks() {
//        sipCoreManager?.onCallTerminated = {
//            eventListener?.onCallDisconnected(
//                callId = CallStateManager.getCurrentCallId(),
//                reason = CallEndReason.USER_HANGUP,
//                duration = 0L
//            )
//        }
//    }
//
//    /**
//     * Interface para callbacks de eventos (legacy)
//     */
//    @Deprecated("Use SipEventListener instead")
//    interface SipCallbacks {
//        fun onCallTerminated() {}
//        fun onCallStateChanged(state: CallState) {}
//        fun onRegistrationStateChanged(state: RegistrationState) {}
//        fun onIncomingCall(callerNumber: String, callerName: String?) {}
//        fun onCallConnected() {}
//        fun onCallFailed(error: String) {}
//    }
//
//    /**
//     * Excepción personalizada para la biblioteca
//     */
//    class SipLibraryException(message: String, cause: Throwable? = null) : Exception(message, cause)
//}

//////// 2/////
//
//
///**
// * EddysSipLibrary - Biblioteca SIP/VoIP para Android
// *
// * Biblioteca desarrollada por Eddys Larez para manejo de llamadas SIP/VoIP
// * con soporte para WebRTC y WebSocket.
// *
// * @author Eddys Larez
// * @version 1.0.0
// */
//class EddysSipLibrary private constructor() {
//
//    private var sipCoreManager: SipCoreManager? = null
//    private var isInitialized = false
//    private lateinit var config: SipConfig
//
//    companion object {
//        @Volatile
//        private var INSTANCE: EddysSipLibrary? = null
//        private const val TAG = "EddysSipLibrary"
//
//        /**
//         * Obtiene la instancia singleton de la biblioteca
//         */
//        fun getInstance(): EddysSipLibrary {
//            return INSTANCE ?: synchronized(this) {
//                INSTANCE ?: EddysSipLibrary().also { INSTANCE = it }
//            }
//        }
//    }
//
//    /**
//     * Configuración de la biblioteca
//     */
//    data class SipConfig(
//        val defaultDomain: String = "",
//        val webSocketUrl: String = "",
//        val userAgent: String = "",
//        val enableLogs: Boolean = true,
//        val enableAutoReconnect: Boolean = true,
//        val pingIntervalMs: Long = 30000L
//    )
//
//    /**
//     * Inicializa la biblioteca SIP
//     *
//     * @param application Instancia de la aplicación Android
//     * @param config Configuración opcional de la biblioteca
//     */
//
//    fun initialize(
//        application: Application,
//        config: SipConfig = SipConfig()
//    ) {
//        if (isInitialized) {
//            log.w(tag = TAG) { "Library already initialized" }
//            return
//        }
//
//        try {
//            log.d(tag = TAG) { "Initializing EddysSipLibrary v1.0.0 by Eddys Larez" }
//
//            this.config = config // <--- GUARDAMOS CONFIGURACIÓN
//            sipCoreManager = SipCoreManager.createInstance(application, config)
//            sipCoreManager?.initialize()
//
//            isInitialized = true
//            log.d(tag = TAG) { "EddysSipLibrary initialized successfully" }
//
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error initializing library: ${e.message}" }
//            throw SipLibraryException("Failed to initialize library", e)
//        }
//    }
//
//
//    /**
//     * Registra una cuenta SIP
//     *
//     * @param username Nombre de usuario SIP
//     * @param password Contraseña SIP
//     * @param domain Dominio SIP (opcional, usa el configurado por defecto)
//     * @param pushToken Token para notificaciones push (opcional)
//     * @param pushProvider Proveedor de push (fcm/apns)
//     */
//    fun registerAccount(
//        username: String,
//        password: String,
//        domain: String? = null,
//        pushToken: String? = null,
//        pushProvider: String = "fcm"
//    ) {
//        checkInitialized()
//
//        val finalDomain = domain ?: sipCoreManager?.getDefaultDomain() ?: "mcn.ru"
//        val finalToken = pushToken ?: ""
//
//        sipCoreManager?.register(
//            username = username,
//            password = password,
//            domain = finalDomain,
//            provider = pushProvider,
//            token = finalToken
//        )
//    }
//
//    /**
//     * Desregistra una cuenta SIP
//     */
//    fun unregisterAccount(username: String, domain: String? = null) {
//        checkInitialized()
//        val finalDomain = domain ?: sipCoreManager?.getDefaultDomain() ?: ""
//        sipCoreManager?.unregister(username, finalDomain)
//    }
//
//    /**
//     * Realiza una llamada
//     *
//     * @param phoneNumber Número de teléfono a llamar
//     * @param username Cuenta SIP a usar (opcional)
//     * @param domain Dominio SIP (opcional)
//     */
//    fun makeCall(
//        phoneNumber: String,
//        username: String? = null,
//        domain: String? = null
//    ) {
//        checkInitialized()
//
//        val finalUsername = username ?: sipCoreManager?.getCurrentUsername()
//        val finalDomain = domain ?: sipCoreManager?.getDefaultDomain() ?: ""
//
//        if (finalUsername == null) {
//            throw SipLibraryException("No registered account available for calling")
//        }
//
//        sipCoreManager?.makeCall(phoneNumber, finalUsername, finalDomain)
//    }
//
//    /**
//     * Acepta una llamada entrante
//     */
//    fun acceptCall() {
//        checkInitialized()
//        sipCoreManager?.acceptCall()
//    }
//
//    /**
//     * Rechaza una llamada entrante
//     */
//    fun declineCall() {
//        checkInitialized()
//        sipCoreManager?.declineCall()
//    }
//
//    /**
//     * Termina la llamada actual
//     */
//    fun endCall() {
//        checkInitialized()
//        sipCoreManager?.endCall()
//    }
//
//    /**
//     * Pone la llamada en espera
//     */
//    fun holdCall() {
//        checkInitialized()
//        sipCoreManager?.holdCall()
//    }
//
//    /**
//     * Reanuda una llamada en espera
//     */
//    fun resumeCall() {
//        checkInitialized()
//        sipCoreManager?.resumeCall()
//    }
//
//    /**
//     * Envía tonos DTMF
//     *
//     * @param digit Dígito DTMF (0-9, *, #, A-D)
//     * @param duration Duración en milisegundos
//     */
//    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
//        checkInitialized()
//        return sipCoreManager?.sendDtmf(digit, duration) ?: false
//    }
//
//    /**
//     * Envía una secuencia de tonos DTMF
//     */
//    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
//        checkInitialized()
//        return sipCoreManager?.sendDtmfSequence(digits, duration) ?: false
//    }
//
//    /**
//     * Silencia/desmute el micrófono
//     */
//    fun toggleMute() {
//        checkInitialized()
//        sipCoreManager?.mute()
//    }
//
//    /**
//     * Verifica si está silenciado
//     */
//    fun isMuted(): Boolean {
//        checkInitialized()
//        return sipCoreManager?.webRtcManager?.isMuted() ?: false
//    }
//
//    /**
//     * Obtiene dispositivos de audio disponibles
//     */
//    fun getAudioDevices(): Pair<List<AudioDevice>, List<AudioDevice>> {
//        checkInitialized()
//        return sipCoreManager?.webRtcManager?.getAllAudioDevices() ?: Pair(emptyList(), emptyList())
//    }
//
//    /**
//     * Cambia el dispositivo de audio de salida
//     */
//    fun changeAudioOutput(device: AudioDevice): Boolean {
//        checkInitialized()
//        return sipCoreManager?.webRtcManager?.changeAudioOutputDeviceDuringCall(device) ?: false
//    }
//
//    /**
//     * Obtiene el historial de llamadas
//     */
//    fun getCallLogs(): List<CallLog> {
//        checkInitialized()
//        return sipCoreManager?.callLogs() ?: emptyList()
//    }
//
//    /**
//     * Limpia el historial de llamadas
//     */
//    fun clearCallLogs() {
//        checkInitialized()
//        sipCoreManager?.clearCallLogs()
//    }
//
//    /**
//     * Obtiene el estado actual de la llamada
//     */
//    fun getCurrentCallState(): CallState {
//        checkInitialized()
//        return sipCoreManager?.callState ?: CallState.NONE
//    }
//
//    /**
//     * Obtiene el estado de registro
//     */
//    fun getRegistrationState(): RegistrationState {
//        checkInitialized()
//        return sipCoreManager?.getRegistrationState() ?: RegistrationState.NONE
//    }
//
//    /**
//     * Verifica si hay una llamada activa
//     */
//    fun hasActiveCall(): Boolean {
//        checkInitialized()
//        return sipCoreManager?.currentCall() ?: false
//    }
//
//    /**
//     * Verifica si la llamada está conectada
//     */
//    fun isCallConnected(): Boolean {
//        checkInitialized()
//        return sipCoreManager?.currentCallConnected() ?: false
//    }
//
//    /**
//     * Obtiene el Flow del estado de llamada para observar cambios
//     */
//    fun getCallStateFlow(): Flow<CallState> {
//        checkInitialized()
//        return CallStateManager.callStateFlow
//    }
//
//    /**
//     * Obtiene el Flow del estado de registro para observar cambios
//     */
//    fun getRegistrationStateFlow(): Flow<RegistrationState> {
//        checkInitialized()
//        return RegistrationStateManager.registrationStateFlow
//    }
//
//    /**
//     * Actualiza el token de push notifications
//     */
//    fun updatePushToken(token: String, provider: String = "fcm") {
//        checkInitialized()
//        sipCoreManager?.enterPushMode(token)
//    }
//
//    /**
//     * Obtiene información de salud del sistema
//     */
//    fun getSystemHealthReport(): String {
//        checkInitialized()
//        return sipCoreManager?.getSystemHealthReport() ?: "Library not initialized"
//    }
//
//    /**
//     * Verifica si el sistema está saludable
//     */
//    fun isSystemHealthy(): Boolean {
//        checkInitialized()
//        return sipCoreManager?.isSipCoreManagerHealthy() ?: false
//    }
//
//    /**
//     * Configura callbacks para eventos de la biblioteca
//     */
//    fun setCallbacks(callbacks: SipCallbacks) {
//        checkInitialized()
//        sipCoreManager?.onCallTerminated = { callbacks.onCallTerminated() }
//    }
//
//    /**
//     * Libera recursos de la biblioteca
//     */
//    fun dispose() {
//        if (isInitialized) {
//            sipCoreManager?.dispose()
//            sipCoreManager = null
//            isInitialized = false
//            log.d(tag = TAG) { "EddysSipLibrary disposed" }
//        }
//    }
//
//    private fun checkInitialized() {
//        if (!isInitialized || sipCoreManager == null) {
//            throw SipLibraryException("Library not initialized. Call initialize() first.")
//        }
//    }
//
//    /**
//     * Interface para callbacks de eventos
//     */
//    interface SipCallbacks {
//        fun onCallTerminated() {}
//        fun onCallStateChanged(state: CallState) {}
//        fun onRegistrationStateChanged(state: RegistrationState) {}
//        fun onIncomingCall(callerNumber: String, callerName: String?) {}
//        fun onCallConnected() {}
//        fun onCallFailed(error: String) {}
//    }
//
//    /**
//     * Excepción personalizada para la biblioteca
//     */
//    class SipLibraryException(message: String, cause: Throwable? = null) : Exception(message, cause)
//}