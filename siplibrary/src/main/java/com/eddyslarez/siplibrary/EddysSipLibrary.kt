package com.eddyslarez.siplibrary

import android.app.Application
import android.content.Context
import com.eddyslarez.siplibrary.core.SipCoreManager
import com.eddyslarez.siplibrary.data.models.CallDirections
import com.eddyslarez.siplibrary.data.models.CallLog
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.utils.CallStateManager
import com.eddyslarez.siplibrary.utils.RegistrationStateManager
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.time.Clock // o import android.os.SystemClock

/**
 * EddysSipLibrary - Biblioteca SIP/VoIP para Android
 *
 * Biblioteca desarrollada por Eddys Larez para manejo de llamadas SIP/VoIP
 * con soporte para WebRTC y WebSocket.
 *
 * @author Eddys Larez
 * @version 1.0.0
 */
class EddysSipLibrary private constructor() {

    private var sipCoreManager: SipCoreManager? = null
    private var isInitialized = false
    private lateinit var config: SipConfig
    private var eventListener: SipEventListener? = null
    private var lifecycleObserver: AppLifecycleObserver? = null

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
     * Configuración completa de la biblioteca
     */
    data class SipConfig(
        // Configuración básica
        val defaultDomain: String = "",
        val webSocketUrl: String = "",
        val userAgent: String = "EddysSipLibrary/1.0",
        val enableLogs: Boolean = true,

        // Configuración de conexión
        val enableAutoReconnect: Boolean = true,
        val pingIntervalMs: Long = 30000L,
        val registrationExpirationSeconds: Int = 3600,
        val renewBeforeExpirationMs: Long = 60000L,

        // Configuración automática de lifecycle
        val autoSwitchToPushOnBackground: Boolean = true,
        val autoSwitchToForegroundOnResume: Boolean = true,
        val autoDisconnectWebSocketOnBackground: Boolean = false,
        val autoReconnectWebSocketOnForeground: Boolean = true,

        // Configuración de contacto y headers
        val customContactParams: Map<String, String> = emptyMap(),
        val customSipHeaders: Map<String, String> = emptyMap(),

        // Configuración de audio
        val enableAutoAudioRouting: Boolean = true,
        val preferredAudioDevice: String? = null, // "speaker", "earpiece", "bluetooth", "wired_headset"

        // Configuración de notificaciones push
        val defaultPushProvider: String = "fcm",
        val pushNotificationEnabled: Boolean = true,

        // Configuración de timeouts
        val callTimeoutMs: Long = 30000L,
        val registrationTimeoutMs: Long = 10000L,
        val webSocketConnectTimeoutMs: Long = 5000L,

        // Configuración de reintentos
        val maxReconnectAttempts: Int = 3,
        val reconnectDelayMs: Long = 2000L,
        val exponentialBackoff: Boolean = true
    )

    /**
     * Interface completa para eventos de la biblioteca
     */
    interface SipEventListener {
        // Estados de registro
        fun onRegistrationStateChanged(state: RegistrationState, message: String = "") {}
        fun onRegistrationSuccess(account: String, expiresIn: Int) {}
        fun onRegistrationFailed(account: String, error: String) {}

        // Estados de llamada
        fun onCallStateChanged(state: CallState, callId: String = "") {}
        fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {}
        fun onCallConnected(callId: String, duration: Long = 0) {}
        fun onCallDisconnected(callId: String, reason: String, duration: Long) {}
        fun onCallFailed(callId: String, error: String) {}
        fun onCallHeld(callId: String) {}
        fun onCallResumed(callId: String) {}

        // Dispositivos de audio
        fun onAudioDevicesChanged(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {}
        fun onAudioDeviceSelected(device: AudioDevice) {}
        fun onMuteStateChanged(isMuted: Boolean) {}

        // Estados de conexión
        fun onWebSocketConnected() {}
        fun onWebSocketDisconnected(code: Int, reason: String) {}
        fun onWebSocketError(error: String) {}
        fun onNetworkStateChanged(isConnected: Boolean) {}

        // Ciclo de vida de la aplicación
        fun onAppMovedToBackground() {}
        fun onAppMovedToForeground() {}
        fun onModeChanged(mode: String) {} // "foreground", "push", "disconnected"

        // DTMF
        fun onDtmfSent(digit: Char, success: Boolean) {}
        fun onDtmfReceived(digit: Char) {}

        // Sistema
        fun onLibraryError(error: String, exception: Throwable?) {}
        fun onConfigurationChanged(newConfig: SipConfig) {}
    }

    /**
     * Inicializa la biblioteca SIP
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
            log.d(tag = TAG) { "Initializing EddysSipLibrary v1.0.0 by Eddys Larez" }

            this.config = config
            sipCoreManager = SipCoreManager.createInstance(application, config)
            sipCoreManager?.initialize()

            // Configurar lifecycle observer
            setupLifecycleObserver(application)

            // Configurar callbacks internos
            setupInternalCallbacks()

            isInitialized = true
            log.d(tag = TAG) { "EddysSipLibrary initialized successfully" }
            eventListener?.onConfigurationChanged(config)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error initializing library: ${e.message}" }
            eventListener?.onLibraryError("Failed to initialize library", e)
            throw SipLibraryException("Failed to initialize library", e)
        }
    }

    /**
     * Establece el listener para eventos de la biblioteca
     */
    fun setEventListener(listener: SipEventListener) {
        this.eventListener = listener
        sipCoreManager?.setEventListener(listener)
    }

    /**
     * Actualiza la configuración de la biblioteca
     */
    fun updateConfiguration(newConfig: SipConfig) {
        checkInitialized()
        this.config = newConfig
        sipCoreManager?.updateConfiguration(newConfig)
        eventListener?.onConfigurationChanged(newConfig)
    }

    /**
     * Registra una cuenta SIP con configuración avanzada
     */
    fun registerAccount(
        username: String,
        password: String,
        domain: String? = null,
        pushToken: String? = null,
        pushProvider: String? = null,
        customHeaders: Map<String, String>? = null,
        expirationSeconds: Int? = null
    ) {
        checkInitialized()

        val finalDomain = domain ?: config.defaultDomain
        val finalPushToken = pushToken ?: ""
        val finalPushProvider = pushProvider ?: config.defaultPushProvider
        val finalHeaders = customHeaders ?: config.customSipHeaders
        val finalExpiration = expirationSeconds ?: config.registrationExpirationSeconds

        sipCoreManager?.register(
            username = username,
            password = password,
            domain = finalDomain,
            provider = finalPushProvider,
            token = finalPushToken,
            customHeaders = finalHeaders,
            expirationSeconds = finalExpiration
        )
    }

    /**
     * Cambia el modo de operación (foreground/push)
     */
    fun setOperationMode(mode: OperationMode, pushToken: String? = null) {
        checkInitialized()

        when (mode) {
            OperationMode.FOREGROUND -> {
                sipCoreManager?.switchToForegroundMode()
                eventListener?.onModeChanged("foreground")
            }
            OperationMode.PUSH -> {
                pushToken?.let { token ->
                    sipCoreManager?.switchToPushMode(token)
                    eventListener?.onModeChanged("push")
                }
            }
            OperationMode.DISCONNECTED -> {
                sipCoreManager?.disconnectAll()
                eventListener?.onModeChanged("disconnected")
            }
        }
    }

    /**
     * Obtiene los dispositivos de audio disponibles
     */
    fun getAudioDevices(): AudioDevicesInfo {
        checkInitialized()
        val devices = sipCoreManager?.webRtcManager?.getAllAudioDevices() ?: Pair(emptyList(), emptyList())
        return AudioDevicesInfo(
            inputDevices = devices.first,
            outputDevices = devices.second,
            currentInput = sipCoreManager?.webRtcManager?.getCurrentInputDevice(),
            currentOutput = sipCoreManager?.webRtcManager?.getCurrentOutputDevice()
        )
    }

    /**
     * Cambia el dispositivo de audio
     */
    fun changeAudioDevice(device: AudioDevice): Boolean {
        checkInitialized()
        val success = if (device.isOutput) {
            sipCoreManager?.webRtcManager?.changeAudioOutputDeviceDuringCall(device) ?: false
        } else {
            sipCoreManager?.webRtcManager?.changeAudioInputDeviceDuringCall(device) ?: false
        }

        if (success) {
            eventListener?.onAudioDeviceSelected(device)
        }

        return success
    }

    /**
     * Realiza una llamada con parámetros avanzados
     */
    fun makeCall(
        phoneNumber: String,
        username: String? = null,
        domain: String? = null,
        customHeaders: Map<String, String>? = null
    ) {
        checkInitialized()

        val finalUsername = username ?: sipCoreManager?.getCurrentUsername()
        val finalDomain = domain ?: sipCoreManager?.getDefaultDomain() ?: ""
        val finalHeaders = customHeaders ?: config.customSipHeaders

        if (finalUsername == null) {
            val error = "No registered account available for calling"
            eventListener?.onLibraryError(error, null)
            throw SipLibraryException(error)
        }

        sipCoreManager?.makeCall(phoneNumber, finalUsername, finalDomain, finalHeaders)
    }

    /**
     * Acepta una llamada entrante
     */
    fun acceptCall() {
        checkInitialized()
        sipCoreManager?.acceptCall()
    }

    /**
     * Rechaza una llamada entrante
     */
    fun declineCall() {
        checkInitialized()
        sipCoreManager?.declineCall()
    }

    /**
     * Termina la llamada actual
     */
    fun endCall() {
        checkInitialized()
        sipCoreManager?.endCall()
    }

    /**
     * Pone la llamada en espera
     */
    fun holdCall() {
        checkInitialized()
        sipCoreManager?.holdCall()
    }

    /**
     * Reanuda una llamada en espera
     */
    fun resumeCall() {
        checkInitialized()
        sipCoreManager?.resumeCall()
    }

    /**
     * Silencia/desmute el micrófono
     */
    fun toggleMute(): Boolean {
        checkInitialized()
        sipCoreManager?.toggleMute()
        val isMuted = sipCoreManager?.webRtcManager?.isMuted() ?: false
        eventListener?.onMuteStateChanged(isMuted)
        return isMuted
    }

    /**
     * Establece el estado de silencio
     */
    fun setMuted(muted: Boolean) {
        checkInitialized()
        sipCoreManager?.webRtcManager?.setMuted(muted)
        eventListener?.onMuteStateChanged(muted)
    }

    /**
     * Envía tonos DTMF
     */
    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
        checkInitialized()
        val success = sipCoreManager?.sendDtmf(digit, duration) ?: false
        eventListener?.onDtmfSent(digit, success)
        return success
    }

    /**
     * Envía una secuencia de tonos DTMF
     */
    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
        checkInitialized()
        return sipCoreManager?.sendDtmfSequence(digits, duration) ?: false
    }

    /**
     * Desregistra una cuenta SIP
     */
    fun unregisterAccount(username: String, domain: String? = null) {
        checkInitialized()
        val finalDomain = domain ?: sipCoreManager?.getDefaultDomain() ?: ""
        sipCoreManager?.unregister(username, finalDomain)
    }

    /**
     * Obtiene información del estado actual
     */
    fun getCurrentStatus(): LibraryStatus {
        checkInitialized()
        return LibraryStatus(
            isInitialized = isInitialized,
            registrationState = sipCoreManager?.getRegistrationState() ?: RegistrationState.NONE,
            callState = sipCoreManager?.getCurrentCallState() ?: CallState.NONE,
            hasActiveCall = sipCoreManager?.hasActiveCall() ?: false,
            isCallConnected = sipCoreManager?.isCallConnected() ?: false,
            isMuted = sipCoreManager?.webRtcManager?.isMuted() ?: false,
            currentMode = determineCurrentMode(),
            systemHealth = sipCoreManager?.isSystemHealthy() ?: false
        )
    }

    /**
     * Obtiene el historial de llamadas
     */
    fun getCallLogs(): List<CallLog> {
        checkInitialized()
        return sipCoreManager?.callLogs() ?: emptyList()
    }

    /**
     * Limpia el historial de llamadas
     */
    fun clearCallLogs() {
        checkInitialized()
        sipCoreManager?.clearCallLogs()
    }

    /**
     * Obtiene reportes de diagnóstico
     */
    fun getDiagnosticReport(): DiagnosticReport {
        checkInitialized()
        return DiagnosticReport(
            systemHealth = sipCoreManager?.getSystemHealthReport() ?: "Library not initialized",
            audioDiagnosis = sipCoreManager?.webRtcManager?.diagnoseAudioIssues() ?: "Audio not available",
            webSocketStatus = sipCoreManager?.getWebSocketStatus() ?: "WebSocket not available",
            configuration = config,
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
    }

    /**
     * Actualiza el token de push notifications
     */
    fun updatePushToken(token: String, provider: String = "fcm") {
        checkInitialized()
        sipCoreManager?.updatePushToken(token, provider)
    }

    /**
     * Fuerza la reconexión
     */
    fun forceReconnect() {
        checkInitialized()
        sipCoreManager?.forceReconnect()
    }

    /**
     * Libera recursos de la biblioteca
     */
    fun dispose() {
        if (isInitialized) {
            lifecycleObserver?.let { observer ->
                ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
            }
            sipCoreManager?.dispose()
            sipCoreManager = null
            eventListener = null
            isInitialized = false
            log.d(tag = TAG) { "EddysSipLibrary disposed" }
        }
    }

    // ===================== MÉTODOS PRIVADOS =====================

    private fun setupLifecycleObserver(application: Application) {
        lifecycleObserver = AppLifecycleObserver().also { observer ->
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        }
    }

    private fun setupInternalCallbacks() {
        sipCoreManager?.setInternalCallbacks(object : SipInternalCallbacks {
            override fun onRegistrationStateChanged(state: RegistrationState, message: String) {
                eventListener?.onRegistrationStateChanged(state, message)
            }

            override fun onCallStateChanged(state: CallState, callId: String) {
                eventListener?.onCallStateChanged(state, callId)
            }

            override fun onWebSocketConnected() {
                eventListener?.onWebSocketConnected()
            }

            override fun onWebSocketDisconnected(code: Int, reason: String) {
                eventListener?.onWebSocketDisconnected(code, reason)
            }

            override fun onError(error: String, exception: Throwable?) {
                eventListener?.onLibraryError(error, exception)
            }
        })
    }

    private fun determineCurrentMode(): String {
        return when {
            sipCoreManager?.isInPushMode() == true -> "push"
            sipCoreManager?.isConnected() == true -> "foreground"
            else -> "disconnected"
        }
    }

    private fun checkInitialized() {
        if (!isInitialized || sipCoreManager == null) {
            throw SipLibraryException("Library not initialized. Call initialize() first.")
        }
    }

    // ===================== INNER CLASSES =====================

    /**
     * Observer para el ciclo de vida de la aplicación
     */
    private inner class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // App moved to foreground
            eventListener?.onAppMovedToForeground()

            if (config.autoSwitchToForegroundOnResume) {
                sipCoreManager?.handleAppMovedToForeground()
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            // App moved to background
            eventListener?.onAppMovedToBackground()

            if (config.autoSwitchToPushOnBackground) {
                sipCoreManager?.handleAppMovedToBackground()
            }

            if (config.autoDisconnectWebSocketOnBackground) {
                sipCoreManager?.disconnectWebSocket()
            }
        }
    }

    /**
     * Información de dispositivos de audio
     */
    data class AudioDevicesInfo(
        val inputDevices: List<AudioDevice>,
        val outputDevices: List<AudioDevice>,
        val currentInput: AudioDevice?,
        val currentOutput: AudioDevice?
    )

    /**
     * Estado actual de la biblioteca
     */
    data class LibraryStatus(
        val isInitialized: Boolean,
        val registrationState: RegistrationState,
        val callState: CallState,
        val hasActiveCall: Boolean,
        val isCallConnected: Boolean,
        val isMuted: Boolean,
        val currentMode: String,
        val systemHealth: Boolean
    )

    /**
     * Reporte de diagnóstico
     */
    data class DiagnosticReport(
        val systemHealth: String,
        val audioDiagnosis: String,
        val webSocketStatus: String,
        val configuration: SipConfig,
        val timestamp: Long
    )

    /**
     * Modos de operación
     */
    enum class OperationMode {
        FOREGROUND,
        PUSH,
        DISCONNECTED
    }

    /**
     * Callbacks internos para el SipCoreManager
     */
    interface SipInternalCallbacks {
        fun onRegistrationStateChanged(state: RegistrationState, message: String = "")
        fun onCallStateChanged(state: CallState, callId: String = "")
        fun onWebSocketConnected()
        fun onWebSocketDisconnected(code: Int, reason: String)
        fun onError(error: String, exception: Throwable?)
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