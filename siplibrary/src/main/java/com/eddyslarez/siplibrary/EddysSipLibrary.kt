package com.eddyslarez.siplibrary

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.eddyslarez.siplibrary.core.CallStatistics
import com.eddyslarez.siplibrary.core.SipCoreManager
import com.eddyslarez.siplibrary.data.models.CallDirections
import com.eddyslarez.siplibrary.data.models.CallLog
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.integration.SipTranslationIntegration
import com.eddyslarez.siplibrary.translation.Language
import com.eddyslarez.siplibrary.translation.RealtimeTranslationManager
import com.eddyslarez.siplibrary.translation.TranslationConfiguration
import com.eddyslarez.siplibrary.translation.TranslationEvent
import com.eddyslarez.siplibrary.translation.TranslationEventListener
import com.eddyslarez.siplibrary.translation.VoiceGender
import com.eddyslarez.siplibrary.utils.CallStateManager
import com.eddyslarez.siplibrary.utils.RegistrationStateManager
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * EddysSipLibrary - Biblioteca SIP/VoIP para Android con Traducción en Tiempo Real
 *
 * Biblioteca desarrollada por Eddys Larez para manejo de llamadas SIP/VoIP
 * con soporte para WebRTC, WebSocket y traducción en tiempo real con OpenAI.
 *
 * @author Eddys Larez
 * @version 1.0.0
 */
/**
 * Biblioteca SIP completa con capacidades de traducción en tiempo real
 * @author Eddys Larez
 */

class EddysSipLibrary private constructor() {

    private var sipCoreManager: SipCoreManager? = null
    private var isInitialized = false
    private lateinit var config: SipConfig
    private var eventListener: SipEventListener? = null
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
     * Configuración completa de la biblioteca
     */
    data class SipConfig(
        val defaultDomain: String = "",
        val webSocketUrl: String = "",
        val userAgent: String = "EddysSipLibrary/1.0.0",
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

        // Eventos de traducción
        fun onTranslationEvent(event: TranslationEvent) {}
        fun onTranslationAudioProcessed(isOutgoing: Boolean, originalSize: Int, translatedSize: Int) {}
        fun onTranslationConfigChanged(oldConfig: TranslationConfiguration, newConfig: TranslationConfiguration) {}
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
        CONFIGURATION,
        TRANSLATION
    }

    enum class WarningCategory {
        AUDIO_QUALITY,
        NETWORK_QUALITY,
        BATTERY_OPTIMIZATION,
        PERMISSION,
        TRANSLATION_QUALITY
    }

    /**
     * Inicializa la biblioteca SIP
     *
     * @param application Instancia de la aplicación Android
     * @param config Configuración de la biblioteca
     * @param eventListener Listener para eventos (opcional)
     */
    fun initialize(
        application: Application,
        config: SipConfig = SipConfig(),
        eventListener: SipEventListener? = null
    ) {
        if (isInitialized) {
            log.w(tag = TAG) { "Library already initialized" }
            return
        }

        try {
            log.d(tag = TAG) { "Initializing EddysSipLibrary v1.0.0 by Eddys Larez with Translation Support" }

            this.config = config
            this.eventListener = eventListener

            // Inicializar gestor de ciclo de vida
            lifecycleManager = AppLifecycleManager(application, config, eventListener)

            // Crear SipCoreManager con configuración mejorada
            sipCoreManager = SipCoreManager.createInstance(application, config, eventListener)
            sipCoreManager?.initialize()

            // Configurar callbacks internos
            setupInternalCallbacks()

            isInitialized = true
            log.d(tag = TAG) { "EddysSipLibrary initialized successfully with translation capabilities" }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error initializing library: ${e.message}" }
            eventListener?.onError(SipError(
                code = -1,
                message = "Failed to initialize library: ${e.message}",
                category = ErrorCategory.CONFIGURATION
            ))
            throw SipLibraryException("Failed to initialize library", e)
        }
    }

    /**
     * Extiende la biblioteca con capacidades de traducción en tiempo real
     * @return Una instancia extendida con funcionalidades de traducción
     */
    fun withRealTimeTranslation(): TranslationEnabledSipLibrary {
        checkInitialized()
        return TranslationEnabledSipLibrary(this)
    }

    /**
     * Crea un tester de traducción independiente para pruebas sin llamadas
     * @param config Configuración de traducción
     * @return Una instancia del tester de traducción
     */
    fun createTranslationTester(config: TranslationConfiguration): TranslationTester {
        checkInitialized()
        return TranslationTester(config)
    }

    /**
     * Establece un listener para eventos
     */
    fun setEventListener(listener: SipEventListener) {
        this.eventListener = listener
        sipCoreManager?.setEventListener(listener)
        lifecycleManager?.setEventListener(listener)
    }

    /**
     * Registra una cuenta SIP con configuración extendida
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

    // ============ MÉTODOS EXISTENTES ============

    fun updatePushConfiguration(
        token: String,
        provider: String = "fcm",
        customParams: Map<String, String> = emptyMap()
    ) {
        checkInitialized()
        sipCoreManager?.updatePushConfiguration(token, provider, customParams)
        eventListener?.onPushTokenUpdated(token, provider)
    }

    fun updateUserAgent(newUserAgent: String) {
        checkInitialized()
        sipCoreManager?.updateUserAgent(newUserAgent)
    }

    fun changeAudioDevice(deviceType: AudioDeviceType): Boolean {
        checkInitialized()
        return sipCoreManager?.changeAudioDeviceByType(deviceType) ?: false
    }

    fun getCurrentCallStatistics(): CallStatistics? {
        checkInitialized()
        return sipCoreManager?.getCurrentCallStatistics()
    }

    fun getNetworkQuality(): NetworkQuality? {
        checkInitialized()
        return sipCoreManager?.getNetworkQuality()
    }

    fun setAutoPushMode(enabled: Boolean) {
        checkInitialized()
        lifecycleManager?.setAutoPushMode(enabled)
    }

    fun enterPushMode(reason: String = "Manual") {
        checkInitialized()
        sipCoreManager?.enterPushMode()
        eventListener?.onPushModeChanged(true, reason)
    }

    fun exitPushMode(reason: String = "Manual") {
        checkInitialized()
        sipCoreManager?.exitPushMode()
        eventListener?.onPushModeChanged(false, reason)
    }

    fun getCurrentConfig(): SipConfig = config

    fun updateConfig(newConfig: SipConfig) {
        checkInitialized()
        this.config = newConfig
        sipCoreManager?.updateConfig(newConfig)
        lifecycleManager?.updateConfig(newConfig)
    }

    fun unregisterAccount(username: String, domain: String? = null) {
        checkInitialized()
        val finalDomain = domain ?: config.defaultDomain
        sipCoreManager?.unregister(username, finalDomain)
    }

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
            eventListener?.onError(error)
            throw SipLibraryException("No registered account available for calling")
        }

        sipCoreManager?.makeCall(phoneNumber, finalUsername, finalDomain, customHeaders)
    }

    fun acceptCall(autoAnswer: Boolean = false) {
        checkInitialized()
        sipCoreManager?.acceptCall()
    }

    fun declineCall(reason: String = "Declined") {
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

    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
        checkInitialized()
        return sipCoreManager?.sendDtmf(digit, duration) ?: false
    }

    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
        checkInitialized()
        return sipCoreManager?.sendDtmfSequence(digits, duration) ?: false
    }

    fun toggleMute() {
        checkInitialized()
        sipCoreManager?.mute()
        eventListener?.onMuteStateChanged(isMuted())
    }

    fun isMuted(): Boolean {
        checkInitialized()
        return sipCoreManager?.webRtcManager?.isMuted() ?: false
    }

    fun getAudioDevices(): Pair<List<AudioDevice>, List<AudioDevice>> {
        checkInitialized()
        val devices = sipCoreManager?.webRtcManager?.getAllAudioDevices() ?: Pair(emptyList(), emptyList())
        eventListener?.onAudioDevicesAvailable(devices.first, devices.second)
        return devices
    }

    fun changeAudioOutput(device: AudioDevice): Boolean {
        checkInitialized()
        val oldDevice = sipCoreManager?.webRtcManager?.getCurrentOutputDevice()
        val success = sipCoreManager?.webRtcManager?.changeAudioOutputDeviceDuringCall(device) ?: false
        if (success) {
            eventListener?.onAudioDeviceChanged(oldDevice, device)
        }
        return success
    }

    fun getCallLogs(): List<CallLog> {
        checkInitialized()
        return sipCoreManager?.callLogs() ?: emptyList()
    }

    fun clearCallLogs() {
        checkInitialized()
        sipCoreManager?.clearCallLogs()
    }

    fun getCurrentCallState(): CallState {
        checkInitialized()
        return sipCoreManager?.callState ?: CallState.NONE
    }

    fun getRegistrationState(): RegistrationState {
        checkInitialized()
        return sipCoreManager?.getRegistrationState() ?: RegistrationState.NONE
    }

    fun hasActiveCall(): Boolean {
        checkInitialized()
        return sipCoreManager?.currentCall() ?: false
    }

    fun isCallConnected(): Boolean {
        checkInitialized()
        return sipCoreManager?.currentCallConnected() ?: false
    }

    fun getCallStateFlow(): Flow<CallState> {
        checkInitialized()
        return CallStateManager.callStateFlow
    }

    fun getRegistrationStateFlow(): Flow<RegistrationState> {
        checkInitialized()
        return RegistrationStateManager.registrationStateFlow
    }

    fun updatePushToken(token: String, provider: String = "fcm") {
        checkInitialized()
        sipCoreManager?.enterPushMode(token)
        eventListener?.onPushTokenUpdated(token, provider)
    }

    fun getSystemHealthReport(): String {
        checkInitialized()
        return sipCoreManager?.getSystemHealthReport() ?: "Library not initialized"
    }

    fun isSystemHealthy(): Boolean {
        checkInitialized()
        return sipCoreManager?.isSipCoreManagerHealthy() ?: false
    }

    /**
     * Configura callbacks para eventos de la biblioteca (método legacy)
     */
    @Deprecated("Use setEventListener instead", ReplaceWith("setEventListener"))
    fun setCallbacks(callbacks: SipCallbacks) {
        checkInitialized()
        sipCoreManager?.onCallTerminated = { callbacks.onCallTerminated() }
    }

    // ============ MÉTODOS INTERNOS ============

    internal fun getSipCoreManager(): SipCoreManager? = sipCoreManager

    fun dispose() {
        if (isInitialized) {
            lifecycleManager?.dispose()
            sipCoreManager?.dispose()
            sipCoreManager = null
            lifecycleManager = null
            eventListener = null
            isInitialized = false
            log.d(tag = TAG) { "EddysSipLibrary disposed" }
        }
    }

    private fun checkInitialized() {
        if (!isInitialized || sipCoreManager == null) {
            throw SipLibraryException("Library not initialized. Call initialize() first.")
        }
    }

    private fun setupInternalCallbacks() {
        sipCoreManager?.onCallTerminated = {
            eventListener?.onCallDisconnected(
                callId = CallStateManager.getCurrentCallId(),
                reason = CallEndReason.USER_HANGUP,
                duration = 0L
            )
        }
    }

    /**
     * Interface para callbacks de eventos (legacy)
     */
    @Deprecated("Use SipEventListener instead")
    interface SipCallbacks {
        fun onCallTerminated() {}
        fun onCallStateChanged(state: CallState) {}
        fun onRegistrationStateChanged(state: RegistrationState) {}
        fun onIncomingCall(callerNumber: String, callerName: String?) {}
        fun onCallConnected() {}
        fun onCallFailed(error: String) {}
    }

    /**
     * Excepción personalizada para la biblioteca
     */
    class SipLibraryException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

// ============ EXTENSIONES DE TRADUCCIÓN ============

/**
 * Extensión de SipConfig para incluir configuración de traducción
 */
fun EddysSipLibrary.SipConfig.withTranslation(
    translationConfig: TranslationConfiguration
): EddysSipLibrary.SipConfig {
    return this.copy(
        customHeaders = this.customHeaders + mapOf(
            "X-Translation-Enabled" to translationConfig.isEnabled.toString(),
            "X-Translation-Language" to translationConfig.myLanguage.code
        )
    )
}

/**
 * Extensión de EddysSipLibrary para funciones de traducción
 */
class EddysSipLibraryTranslationExtensions private constructor() {
    companion object {
        /**
         * Extiende EddysSipLibrary con capacidades de traducción
         */
        fun EddysSipLibrary.withRealTimeTranslation(): TranslationEnabledSipLibrary {
            return TranslationEnabledSipLibrary(this)
        }
    }
}

/**
 * Wrapper de EddysSipLibrary con capacidades de traducción
 */
class TranslationEnabledSipLibrary(
    private val sipLibrary: EddysSipLibrary
) {
    private var translationIntegration: SipTranslationIntegration? = null
    private var translationConfig: TranslationConfiguration? = null

    /**
     * Configura la traducción en tiempo real
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun configureTranslation(config: TranslationConfiguration) {
        this.translationConfig = config

        // Si ya hay una integración activa, actualizarla
        translationIntegration?.updateTranslationConfig(config)
    }

    /**
     * Inicia el sistema de traducción para la llamada actual
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun enableTranslationForCurrentCall(): Boolean {
        val config = translationConfig
        if (config == null || !config.isEnabled) {
            return false
        }

        val sipCoreManager = getSipCoreManager()
        if (sipCoreManager == null) {
            return false
        }

        if (translationIntegration == null) {
            translationIntegration = SipTranslationIntegration(sipCoreManager, config)
        }

        translationIntegration?.enableTranslationForCall()
        return true
    }

    /**
     * Detiene la traducción para la llamada actual
     */
    fun disableTranslationForCurrentCall() {
        translationIntegration?.disableTranslationForCall()
    }

    /**
     * Verifica si la traducción está activa
     */
    fun isTranslationActive(): Boolean {
        return translationIntegration?.isTranslationActive() ?: false
    }

    /**
     * Obtiene estadísticas de traducción
     */
    fun getTranslationStatistics(): Map<String, Any>? {
        return translationIntegration?.getTranslationStatistics()
    }

    /**
     * Alternar traducción (activar/desactivar)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun toggleTranslation(): Boolean {
        return if (isTranslationActive()) {
            disableTranslationForCurrentCall()
            false
        } else {
            enableTranslationForCurrentCall()
        }
    }

    /**
     * Probar configuración de traducción sin llamada activa
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun testTranslation(
        testAudioData: ByteArray,
        onResult: (TranslationEvent) -> Unit
    ) {
        val config = translationConfig ?: return

        val testManager = RealtimeTranslationManager(
            config,
            object : TranslationEventListener {
                override fun onTranslationEvent(event: TranslationEvent) {
                    onResult(event)
                }
            }
        )

        testManager.startTranslation()
        testManager.processMyAudio(testAudioData)

        // Limpiar después de la prueba
        CoroutineScope(Dispatchers.IO).launch {
            delay(5000) // Esperar 5 segundos
            testManager.dispose()
        }
    }

    // Delegar métodos de EddysSipLibrary
    fun initialize(
        application: Application,
        config: EddysSipLibrary.SipConfig,
        eventListener: EddysSipLibrary.SipEventListener? = null
    ) = sipLibrary.initialize(application, config, eventListener)

    fun registerAccount(
        username: String,
        password: String,
        domain: String? = null,
        pushToken: String? = null,
        pushProvider: String = "fcm",
        customHeaders: Map<String, String> = emptyMap(),
        expires: Int? = null
    ) = sipLibrary.registerAccount(username, password, domain, pushToken, pushProvider, customHeaders, expires)

    fun makeCall(
        phoneNumber: String,
        username: String? = null,
        domain: String? = null,
        customHeaders: Map<String, String> = emptyMap()
    ) = sipLibrary.makeCall(phoneNumber, username, domain, customHeaders)

    fun acceptCall(autoAnswer: Boolean = false) = sipLibrary.acceptCall(autoAnswer)
    fun declineCall(reason: String = "Declined") = sipLibrary.declineCall(reason)
    fun endCall() = sipLibrary.endCall()
    fun holdCall() = sipLibrary.holdCall()
    fun resumeCall() = sipLibrary.resumeCall()
    fun toggleMute() = sipLibrary.toggleMute()
    fun sendDtmf(digit: Char, duration: Int = 160) = sipLibrary.sendDtmf(digit, duration)

    // Getters para acceso interno
    private fun getSipCoreManager(): SipCoreManager? {
        return sipLibrary.getSipCoreManager()
    }

    fun dispose() {
        translationIntegration?.dispose()
        sipLibrary.dispose()
    }
}

/**
 * Eventos extendidos para traducción
 */
sealed class TranslationSipEvent {
    data class TranslationStarted(val sessionId: String) : TranslationSipEvent()
    data class TranslationStopped(val sessionId: String) : TranslationSipEvent()
    data class LanguageDetected(val language: Language, val confidence: Float) : TranslationSipEvent()
    data class TranslationQuality(val score: Float, val latency: Long) : TranslationSipEvent()
    data class TranslationError(val error: String, val canRecover: Boolean) : TranslationSipEvent()
}


/**
 * Builder para configuración de traducción
 */
class TranslationConfigurationBuilder {
    private var isEnabled = false
    private var myLanguage = Language.SPANISH
    private var remoteLanguage: Language? = null
    private var autoDetectRemoteLanguage = true
    private var voiceGender = VoiceGender.FEMALE
    private var openAiApiKey = ""
    private var model = "gpt-4o-realtime-preview-2024-12-17"
    private var voice = "alloy"

    fun enable() = apply { isEnabled = true }
    fun disable() = apply { isEnabled = false }

    fun myLanguage(language: Language) = apply { myLanguage = language }
    fun remoteLanguage(language: Language) = apply {
        remoteLanguage = language
        autoDetectRemoteLanguage = false
    }

    fun autoDetectRemoteLanguage() = apply { autoDetectRemoteLanguage = true }
    fun voiceGender(gender: VoiceGender) = apply { voiceGender = gender }
    fun openAiApiKey(key: String) = apply { openAiApiKey = key }
    fun model(modelName: String) = apply { model = modelName }
    fun voice(voiceName: String) = apply { voice = voiceName }

    fun build(): TranslationConfiguration {
        return TranslationConfiguration(
            isEnabled = isEnabled,
            myLanguage = myLanguage,
            voiceGender = voiceGender,
            autoDetectRemoteLanguage = autoDetectRemoteLanguage,
            remoteLanguage = remoteLanguage,
            openAiApiKey = openAiApiKey,
            model = model,
            voice = voice
        )
    }
}

/**
 * Factory para crear configuraciones de traducción comunes
 */
object TranslationPresets {
    fun spanishToEnglish(apiKey: String) = TranslationConfigurationBuilder()
        .enable()
        .myLanguage(Language.SPANISH)
        .remoteLanguage(Language.ENGLISH)
        .voiceGender(VoiceGender.FEMALE)
        .openAiApiKey(apiKey)
        .build()

    fun englishToSpanish(apiKey: String) = TranslationConfigurationBuilder()
        .enable()
        .myLanguage(Language.ENGLISH)
        .remoteLanguage(Language.SPANISH)
        .voiceGender(VoiceGender.MALE)
        .openAiApiKey(apiKey)
        .build()

    fun autoDetect(myLanguage: Language, apiKey: String) = TranslationConfigurationBuilder()
        .enable()
        .myLanguage(myLanguage)
        .autoDetectRemoteLanguage()
        .voiceGender(VoiceGender.NEUTRAL)
        .openAiApiKey(apiKey)
        .build()
}


/**
 * Tester de traducción independiente para pruebas sin llamadas
 */
class TranslationTester(
    private var configuration: TranslationConfiguration
) {
    private val TAG = "TranslationTester"
    private var translationManager: RealtimeTranslationManager? = null
    private var isActive = false
    private var eventListener: TranslationEventListener? = null

    /**
     * Establece un listener para eventos de traducción
     */
    fun setEventListener(listener: TranslationEventListener) {
        this.eventListener = listener
    }

    /**
     * Actualiza la configuración de traducción
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun updateConfiguration(newConfig: TranslationConfiguration) {
        this.configuration = newConfig
        translationManager?.updateConfiguration(newConfig)
    }

    /**
     * Inicia una sesión de prueba de traducción
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun startTestSession(): Boolean {
        if (isActive) {
            log.d(tag = TAG) { "Test session already active" }
            return true
        }

        if (!configuration.isEnabled || configuration.openAiApiKey.isEmpty()) {
            log.d(tag = TAG) { "Translation not configured properly" }
            return false
        }

        try {
            translationManager = RealtimeTranslationManager(
                configuration,
                object : TranslationEventListener {
                    override fun onTranslationEvent(event: TranslationEvent) {
                        eventListener?.onTranslationEvent(event)
                    }
                }
            )

            val success = translationManager?.startTranslation() ?: false
            if (success) {
                isActive = true
                log.d(tag = TAG) { "Translation test session started" }
            }
            return success

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error starting test session: ${e.message}" }
            return false
        }
    }

    /**
     * Detiene la sesión de prueba
     */
    fun stopTestSession() {
        if (!isActive) return

        translationManager?.stopTranslation()
        isActive = false
        log.d(tag = TAG) { "Translation test session stopped" }
    }

    /**
     * Procesa audio para traducción (simula hablar)
     */
    fun processTestAudio(audioData: ByteArray) {
        if (!isActive) {
            log.d(tag = TAG) { "Test session not active" }
            return
        }

        translationManager?.processMyAudio(audioData)
    }

    /**
     * Verifica si la sesión está activa
     */
    fun isSessionActive(): Boolean = isActive

    /**
     * Obtiene estadísticas de la sesión de prueba
     */
    fun getTestStatistics(): Map<String, Any>? {
        return translationManager?.getStatistics()
    }

    /**
     * Obtiene la configuración actual
     */
    fun getCurrentConfiguration(): TranslationConfiguration = configuration

    /**
     * Libera recursos
     */
    fun dispose() {
        stopTestSession()
        translationManager?.dispose()
        translationManager = null
        eventListener = null
    }
}
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