package com.eddyslarez.siplibrary

import android.app.Application
import android.content.Context
import android.media.AudioManager
import com.eddyslarez.siplibrary.core.CallStatistics
import com.eddyslarez.siplibrary.core.SipCoreManager
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.data.services.audio.AudioDeviceDetector
import com.eddyslarez.siplibrary.data.services.audio.AudioDeviceType
import com.eddyslarez.siplibrary.data.services.audio.NotificationType
import com.eddyslarez.siplibrary.data.services.audio.RingtoneConfig
import com.eddyslarez.siplibrary.data.services.audio.RingtoneController
import com.eddyslarez.siplibrary.interfaces.AppEventListener
import com.eddyslarez.siplibrary.interfaces.AppState
import com.eddyslarez.siplibrary.interfaces.AudioEventListener
import com.eddyslarez.siplibrary.interfaces.CallEndReason
import com.eddyslarez.siplibrary.interfaces.CallEventListener
import com.eddyslarez.siplibrary.interfaces.ConnectivityEventListener
import com.eddyslarez.siplibrary.interfaces.ErrorEventListener
import com.eddyslarez.siplibrary.interfaces.RegistrationEventListener
import com.eddyslarez.siplibrary.interfaces.SipEventHandler
import com.eddyslarez.siplibrary.managers.EventDispatcher
import com.eddyslarez.siplibrary.states.AudioState
import com.eddyslarez.siplibrary.states.CallInfo
import com.eddyslarez.siplibrary.states.CallerInfo
import com.eddyslarez.siplibrary.states.ConnectionState
import com.eddyslarez.siplibrary.states.NetworkState
import com.eddyslarez.siplibrary.states.SipStateManager
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * EddysSipLibrary Optimizada con Interfaces y StateFlow
 *
 * @author Eddys Larez
 * @version 2.0.0
 */
class EddysSipLibrary private constructor() {

    private var sipCoreManager: SipCoreManager? = null
    private var isInitialized = false
    private lateinit var config: SipConfig
    lateinit var eventDispatcher: EventDispatcher
    private lateinit var stateManager: SipStateManager
    private lateinit var audioDeviceDetector: AudioDeviceDetector
    private lateinit var ringtoneController: RingtoneController
    private var lifecycleManager: AppLifecycleManager? = null

    companion object {
        @Volatile
        private var INSTANCE: EddysSipLibrary? = null
        private const val TAG = "EddysSipLibraryOptimized"

        fun getInstance(): EddysSipLibrary {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EddysSipLibrary().also { INSTANCE = it }
            }
        }
    }

    /**
     * Configuración extendida de la biblioteca
     */
    data class SipConfig(
        val defaultDomain: String = "",
        val webSocketUrl: String = "",
        val userAgent: String = "EddysSipLibrary/2.0.0",
        val enableLogs: Boolean = true,
        val enableAutoReconnect: Boolean = true,
        val pingIntervalMs: Long = 30000L,
        val registrationExpiresSeconds: Int = 3600,

        // Configuración de push notifications
        val autoEnterPushOnBackground: Boolean = true,
        val autoExitPushOnForeground: Boolean = true,
        val autoDisconnectWebSocketOnBackground: Boolean = false,
        val pushReconnectDelayMs: Long = 2000L,

        // Configuración de audio
        val autoSelectAudioDevice: Boolean = true,
        val preferredAudioDevice: AudioDeviceType = AudioDeviceType.EARPIECE,
        val enableEchoCancellation: Boolean = true,
        val enableNoiseSuppression: Boolean = true,

        // Configuración de ringtones
        val ringtoneConfig: RingtoneConfig = RingtoneConfig(),

        // Configuración de llamadas
        val autoAcceptDelay: Long = 0L,
        val callTimeoutSeconds: Int = 60,
        val enableCallRecording: Boolean = false,
        val maxConcurrentCalls: Int = 1,

        // Headers y parámetros personalizados
        val customHeaders: Map<String, String> = emptyMap(),
        val customContactParams: Map<String, String> = emptyMap(),

        // Configuración de calidad
        val enableAdaptiveQuality: Boolean = true,
        val enableCallStatistics: Boolean = true,
        val statisticsUpdateIntervalMs: Long = 5000L
    )

    /**
     * Inicializa la biblioteca optimizada
     */
    fun initialize(
        application: Application,
        config: SipConfig = SipConfig(),
        initialListeners: List<SipEventHandler> = emptyList()
    ) {
        if (isInitialized) {
            log.w(tag = TAG) { "Library already initialized" }
            return
        }

        try {
            log.d(tag = TAG) { "Initializing EddysSipLibrary Optimized v2.0.0" }

            this.config = config

            // Inicializar componentes principales
            initializeComponents(application, config)
            isInitialized = true

            // Registrar listeners iniciales
            initialListeners.forEach { listener ->
                registerEventListener(listener)
            }

            // Crear SipCoreManager mejorado
            sipCoreManager = createOptimizedSipCoreManager(application, config)
            sipCoreManager?.initialize()

            // Configurar conexiones entre componentes
            setupComponentConnections()

            log.d(tag = TAG) { "EddysSipLibrary Optimized initialized successfully" }

            // Notificar inicialización
            eventDispatcher.dispatch<AppEventListener> {
                it.onAppStateChanged(AppState.FOREGROUND, AppState.TERMINATED)
            }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error initializing library: ${e.message}" }
            dispose()
            throw SipLibraryException("Failed to initialize library", e)
        }
    }

    private fun initializeComponents(application: Application, config: SipConfig) {
        // EventDispatcher para múltiples listeners
        eventDispatcher = EventDispatcher()

        // StateManager para reactividad
        stateManager = SipStateManager()

        // AudioDeviceDetector mejorado
        val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioDeviceDetector = AudioDeviceDetector(application, audioManager)

        // RingtoneController con configuración
        ringtoneController = RingtoneController(application, config.ringtoneConfig)

        // AppLifecycleManager
        lifecycleManager = AppLifecycleManager(application, config, null)
    }

    private fun createOptimizedSipCoreManager(
        application: Application,
        config: SipConfig
    ): SipCoreManager {
        // Convertir configuración a formato compatible
        val legacyConfig = SipConfig(
            defaultDomain = config.defaultDomain,
            webSocketUrl = config.webSocketUrl,
            userAgent = config.userAgent,
            enableLogs = config.enableLogs,
            enableAutoReconnect = config.enableAutoReconnect,
            pingIntervalMs = config.pingIntervalMs,
            registrationExpiresSeconds = config.registrationExpiresSeconds,
            autoEnterPushOnBackground = config.autoEnterPushOnBackground,
            autoExitPushOnForeground = config.autoExitPushOnForeground,
            customHeaders = config.customHeaders
        )

        return SipCoreManager.createInstance(
            application = application,
            config = legacyConfig,
            eventListener = createBridgeEventListener()
        )
    }

    private fun createBridgeEventListener(): SipEventListener {
        return object : SipEventListener {
            override fun onRegistrationStateChanged(state: RegistrationState, account: String) {
                stateManager.updateRegistrationState(state)
                eventDispatcher.dispatch<RegistrationEventListener> {
                    it.onRegistrationStateChanged(state, account)
                }
            }

            override fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) {
                stateManager.updateCallState(newState)

                // Gestionar ringtones automáticamente según el estado
                handleRingtoneForCallState(oldState, newState)

                eventDispatcher.dispatch<CallEventListener> {
                    it.onCallStateChanged(oldState, newState, callId)
                }
            }

            override fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
                stateManager.updateCallerInfo(
                    CallerInfo(
                    number = callerNumber,
                    name = callerName
                )
                )

                // Reproducir ringtone automáticamente
                ringtoneController.playIncomingRingtone()

                eventDispatcher.dispatch<CallEventListener> {
                    it.onIncomingCall(callerNumber, callerName, callId)
                }
            }

            override fun onCallConnected(callId: String, duration: Long) {
                // Detener todos los ringtones cuando se conecta
                ringtoneController.stopAllRingtones()

                eventDispatcher.dispatch<CallEventListener> {
                    it.onCallConnected(callId, duration)
                }
            }

            override fun onCallDisconnected(callId: String, reason: CallEndReason, duration: Long) {
                // Detener todos los ringtones cuando se desconecta
                ringtoneController.stopAllRingtones()

                // Reproducir sonido de finalización si está configurado
                ringtoneController.playNotificationSound(NotificationType.CALL_ENDED)

                stateManager.updateCurrentCall(null)
                stateManager.updateCallerInfo(null)

                eventDispatcher.dispatch<CallEventListener> {
                    it.onCallDisconnected(callId, reason, duration)
                }
            }

            override fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {
                val currentAudioState = stateManager.audioState.value
                stateManager.updateAudioState(
                    currentAudioState.copy(
                        currentOutputDevice = newDevice
                    )
                )

                eventDispatcher.dispatch<AudioEventListener> {
                    it.onAudioDeviceChanged(oldDevice, newDevice)
                }
            }

            override fun onNetworkStateChanged(isConnected: Boolean, networkType: String) {
                val currentNetworkState = stateManager.networkState.value
                stateManager.updateNetworkState(
                    currentNetworkState.copy(
                        isConnected = isConnected,
                        networkType = networkType
                    )
                )

                eventDispatcher.dispatch<ConnectivityEventListener> {
                    it.onNetworkStateChanged(isConnected, networkType)
                }
            }

            override fun onError(error: SipError) {
                val optimizedError = SipError(
                    code = error.code,
                    message = error.message,
                    category = when (error.category) {
                       ErrorCategory.NETWORK -> ErrorCategory.NETWORK
                       ErrorCategory.AUTHENTICATION -> ErrorCategory.AUTHENTICATION
                      ErrorCategory.AUDIO -> ErrorCategory.AUDIO
                        ErrorCategory.SIP_PROTOCOL -> ErrorCategory.SIP_PROTOCOL
                       ErrorCategory.WEBRTC -> ErrorCategory.WEBRTC
                        ErrorCategory.CONFIGURATION -> ErrorCategory.CONFIGURATION
                        ErrorCategory.PERMISSION -> TODO()
                        ErrorCategory.SYSTEM -> TODO()
                    }
                )

                eventDispatcher.dispatch<ErrorEventListener> {
                    it.onError(optimizedError)
                }
            }
        }
    }

    private fun handleRingtoneForCallState(oldState: CallState, newState: CallState) {
        when (newState) {
            CallState.OUTGOING, CallState.CALLING -> {
                if (config.ringtoneConfig.enableOutgoingRingtone) {
                    ringtoneController.playOutgoingRingtone()
                }
            }
            CallState.CONNECTED -> {
                ringtoneController.stopAllRingtones()
                ringtoneController.playNotificationSound(NotificationType.CALL_CONNECTED)
            }
            CallState.ENDED, CallState.DECLINED -> {
                ringtoneController.stopAllRingtones()
                if (oldState == CallState.CONNECTED) {
                    ringtoneController.playNotificationSound(NotificationType.CALL_ENDED)
                }
            }
            else -> {
                // No hacer nada para otros estados
            }
        }
    }

    private fun setupComponentConnections() {
        // Conectar detector de audio con state manager
        CoroutineScope(Dispatchers.Main).launch {
            audioDeviceDetector.outputDevices.collect { devices ->
                val currentState = stateManager.audioState.value
                stateManager.updateAudioState(
                    currentState.copy(availableOutputDevices = devices)
                )

                eventDispatcher.dispatch<AudioEventListener> { listener ->
                    listener.onAudioDevicesAvailable(
                        audioDeviceDetector.inputDevices.value,
                        devices
                    )
                }
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            audioDeviceDetector.inputDevices.collect { devices ->
                val currentState = stateManager.audioState.value
                stateManager.updateAudioState(
                    currentState.copy(availableInputDevices = devices)
                )
            }
        }
    }

    // ===== GESTIÓN DE LISTENERS =====

    /**
     * Registra un listener para eventos específicos
     */
    inline fun <reified T : SipEventHandler> registerEventListener(listener: T) {
        checkInitialized()
        eventDispatcher.registerListener(listener)
    }

    /**
     * Desregistra un listener
     */
    inline fun <reified T : SipEventHandler> unregisterEventListener(listener: T) {
        checkInitialized()
        eventDispatcher.unregisterListener(listener)
    }

    // ===== GESTIÓN DE CUENTAS =====

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

    fun unregisterAccount(username: String, domain: String? = null) {
        checkInitialized()
        val finalDomain = domain ?: config.defaultDomain
        sipCoreManager?.unregister(username, finalDomain)
    }

    // ===== GESTIÓN DE LLAMADAS =====

    fun makeCall(
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
            eventDispatcher.dispatch<ErrorEventListener> { it.onError(error) }
            return
        }

        sipCoreManager?.makeCall(phoneNumber, finalUsername, finalDomain, customHeaders)
    }

    fun acceptCall() {
        checkInitialized()
        sipCoreManager?.acceptCall()
    }

    fun declineCall() {
        checkInitialized()
        sipCoreManager?.declineCall()
    }

    fun endCall() {
        checkInitialized()
        sipCoreManager?.endCall()
    }

    fun holdCall() {
        checkInitialized()
        sipCoreManager?.holdCall()
    }

    fun resumeCall() {
        checkInitialized()
        sipCoreManager?.resumeCall()
    }

    // ===== GESTIÓN DE AUDIO =====

    fun toggleMute(): Boolean {
        checkInitialized()
        sipCoreManager?.mute()
        val isMuted = sipCoreManager?.webRtcManager?.isMuted() ?: false

        val currentState = stateManager.audioState.value
        stateManager.updateAudioState(currentState.copy(isMuted = isMuted))

        eventDispatcher.dispatch<AudioEventListener> {
            it.onMuteStateChanged(isMuted)
        }

        return isMuted
    }

    fun changeAudioDevice(device: AudioDevice): Boolean {
        checkInitialized()
        val success = if (device.isOutput) {
            sipCoreManager?.webRtcManager?.changeAudioOutputDeviceDuringCall(device) ?: false
        } else {
            sipCoreManager?.webRtcManager?.changeAudioInputDeviceDuringCall(device) ?: false
        }

        if (success) {
            val currentState = stateManager.audioState.value
            if (device.isOutput) {
                stateManager.updateAudioState(currentState.copy(currentOutputDevice = device))
            } else {
                stateManager.updateAudioState(currentState.copy(currentInputDevice = device))
            }
        }

        return success
    }

    fun getAvailableAudioDevices(): Pair<List<AudioDevice>, List<AudioDevice>> {
        checkInitialized()
        return audioDeviceDetector.getAllDevices()
    }

    // ===== GESTIÓN DE DTMF =====

    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
        checkInitialized()
        val success = sipCoreManager?.sendDtmf(digit, duration) ?: false

        eventDispatcher.dispatch<CallEventListener> { listener ->
            listener.onDtmfSent(stateManager.currentCall.value?.id ?: "", digit, success)
        }

        if (success && config.ringtoneConfig.enableNotificationSounds) {
            ringtoneController.playNotificationSound(NotificationType.DTMF_TONE)
        }

        return success
    }

    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
        checkInitialized()
        return sipCoreManager?.sendDtmfSequence(digits, duration) ?: false
    }

    // ===== GESTIÓN DE CONFIGURACIÓN =====

    fun updateRingtoneConfig(newConfig: RingtoneConfig) {
        checkInitialized()
        this.config = this.config.copy(ringtoneConfig = newConfig)
        ringtoneController.updateConfig(newConfig)
    }

    fun updatePushConfiguration(
        token: String,
        provider: String = "fcm",
        customParams: Map<String, String> = emptyMap()
    ) {
        checkInitialized()
        sipCoreManager?.updatePushConfiguration(token, provider, customParams)

        eventDispatcher.dispatch<AppEventListener> {
            it.onPushTokenUpdated(token, provider)
        }
    }

    // ===== ACCESO A ESTADOS (StateFlow) =====

    fun getCallStateFlow(): StateFlow<CallState> = stateManager.callState
    fun getRegistrationStateFlow(): StateFlow<RegistrationState> = stateManager.registrationState
    fun getCallerInfoFlow(): StateFlow<CallerInfo?> = stateManager.callerInfo
    fun getCurrentCallFlow(): StateFlow<CallInfo?> = stateManager.currentCall
    fun getAudioStateFlow(): StateFlow<AudioState> = stateManager.audioState
    fun getNetworkStateFlow(): StateFlow<NetworkState> = stateManager.networkState
    fun getAppStateFlow(): StateFlow<AppState> = stateManager.appState
    fun getConnectionStateFlow(): StateFlow<ConnectionState> = stateManager.connectionState

    // Estados combinados
    fun getIsCallActiveFlow(): StateFlow<Boolean> = stateManager.isCallActive
    fun getCanMakeCallFlow(): StateFlow<Boolean> = stateManager.canMakeCall

    // ===== MÉTODOS DE UTILIDAD =====

    fun getCurrentConfig(): SipConfig = config

    fun getEventStatistics(): Map<String, Int> = eventDispatcher.getEventStatistics()

    fun getListenerCounts(): Map<String, Int> = eventDispatcher.getListenerCounts()

    fun getSystemHealthReport(): String {
        checkInitialized()
        return buildString {
            appendLine("=== EddysSipLibrary Optimized Health Report ===")
            appendLine("Version: 2.0.0")
            appendLine("Initialized: $isInitialized")
            appendLine("Active Listeners: ${eventDispatcher.getListenerCounts()}")
            appendLine("Current Call State: ${stateManager.callState.value}")
            appendLine("Registration State: ${stateManager.registrationState.value}")
            appendLine("Network Connected: ${stateManager.networkState.value.isConnected}")
            appendLine("Audio Devices: ${audioDeviceDetector.getAllDevices().let { "${it.first.size} input, ${it.second.size} output" }}")
            appendLine("Ringtone Playing: Incoming=${ringtoneController.isPlayingIncoming.value}, Outgoing=${ringtoneController.isPlayingOutgoing.value}")
            appendLine()
            appendLine("=== Event Statistics ===")
            eventDispatcher.getEventStatistics().forEach { (event, count) ->
                appendLine("$event: $count")
            }
        }
    }

    fun checkInitialized() {
        if (!isInitialized || sipCoreManager == null) {
            throw SipLibraryException("Library not initialized. Call initialize() first.")
        }
    }

    fun dispose() {
        if (isInitialized) {
            ringtoneController.stopAllRingtones()
            ringtoneController.dispose()
            audioDeviceDetector.dispose()
            lifecycleManager?.dispose()
            sipCoreManager?.dispose()
            eventDispatcher.dispose()

            sipCoreManager = null
            lifecycleManager = null
            isInitialized = false

            log.d(tag = TAG) { "EddysSipLibrary Optimized disposed" }
        }
    }

    /**
     * Excepción personalizada para la biblioteca optimizada
     */
    class SipLibraryException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
/**
 * Interface principal para eventos de la biblioteca
 */
interface SipEventListener {


    // Eventos de registro
    fun onRegistrationStateChanged(state: RegistrationState, account: String) {}
    fun onRegistrationSuccess(account: String, expiresIn: Int) {}
    fun onRegistrationFailed(account: String, reason: String) {}


    // Eventos de llamadas
    fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {}
    fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) {}
    fun onCallConnected(callId: String, duration: Long = 0) {}
    fun onCallDisconnected(callId: String, reason: CallEndReason, duration: Long) {}
    fun onCallFailed(callId: String, error: String, errorCode: Int = -1) {}
    fun onCallHold(callId: String, isOnHold: Boolean) {}


    // Eventos de audio
    fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {}
    fun onAudioDevicesAvailable(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {}
    fun onMuteStateChanged(isMuted: Boolean) {}
    fun onAudioLevelChanged(level: Float) {} // 0.0 - 1.0


    // Eventos de conectividad
    fun onNetworkStateChanged(isConnected: Boolean, networkType: String) {}
    fun onWebSocketStateChanged(isConnected: Boolean, url: String) {}
    fun onReconnectionAttempt(attempt: Int, maxAttempts: Int) {}


    // Eventos de modo push
    fun onPushModeChanged(isInPushMode: Boolean, reason: String) {}
    fun onPushTokenUpdated(token: String, provider: String) {}


    // Eventos de ciclo de vida de la app
    fun onAppStateChanged(appState: AppState, previousState: AppState) {}


    // Eventos de mensajería SIP
    fun onSipMessageReceived(message: String, messageType: String) {}
    fun onSipMessageSent(message: String, messageType: String) {}


    // Eventos de estadísticas
    fun onCallStatistics(stats: CallStatistics) {}
    fun onNetworkQuality(quality: NetworkQuality) {}


    // Eventos de errores
    fun onError(error: SipError) {}
    fun onWarning(warning: SipWarning) {}
}
data class NetworkQuality(
    val score: Float, // 0.0 - 1.0
    val latency: Long,
    val packetLoss: Float,
    val jitter: Long
)

/**
 * Tipos de errores extendidos
 */
enum class ErrorCategory {
    NETWORK,
    AUTHENTICATION,
    AUDIO,
    SIP_PROTOCOL,
    WEBRTC,
    CONFIGURATION,
    PERMISSION,
    SYSTEM
}

/**
 * Error de SIP optimizado
 */
data class SipError(
    val code: Int,
    val message: String,
    val category: ErrorCategory,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val details: Map<String, String> = emptyMap()
)

/**
 * Advertencia de SIP optimizada
 */
data class SipWarning(
    val message: String,
    val category: WarningCategory,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val actionable: Boolean = false,
    val action: String? = null
)

enum class WarningCategory {
    AUDIO_QUALITY,
    NETWORK_QUALITY,
    BATTERY_OPTIMIZATION,
    PERMISSION,
    PERFORMANCE,
    CONFIGURATION
}
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