package com.eddyslarez.siplibrary

import android.app.Application
import android.content.Context
import com.eddyslarez.siplibrary.core.SipCoreManager
import com.eddyslarez.siplibrary.data.models.CallLog
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.utils.CallStateManager
import com.eddyslarez.siplibrary.utils.RegistrationStateManager
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.flow.Flow

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
     * Configuración de la biblioteca
     */
    data class SipConfig(
        val defaultDomain: String = "",
        val webSocketUrl: String = "",
        val userAgent: String = "",
        val enableLogs: Boolean = true,
        val enableAutoReconnect: Boolean = true,
        val pingIntervalMs: Long = 30000L
    )
    
    /**
     * Inicializa la biblioteca SIP
     * 
     * @param application Instancia de la aplicación Android
     * @param config Configuración opcional de la biblioteca
     */
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
//            // Inicializar el core manager
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

            this.config = config // <--- GUARDAMOS CONFIGURACIÓN
            sipCoreManager = SipCoreManager.createInstance(application, config)
            sipCoreManager?.initialize()

            isInitialized = true
            log.d(tag = TAG) { "EddysSipLibrary initialized successfully" }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error initializing library: ${e.message}" }
            throw SipLibraryException("Failed to initialize library", e)
        }
    }


    /**
     * Registra una cuenta SIP
     * 
     * @param username Nombre de usuario SIP
     * @param password Contraseña SIP
     * @param domain Dominio SIP (opcional, usa el configurado por defecto)
     * @param pushToken Token para notificaciones push (opcional)
     * @param pushProvider Proveedor de push (fcm/apns)
     */
    fun registerAccount(
        username: String,
        password: String,
        domain: String? = null,
        pushToken: String? = null,
        pushProvider: String = "fcm"
    ) {
        checkInitialized()
        
        val finalDomain = domain ?: sipCoreManager?.getDefaultDomain() ?: "mcn.ru"
        val finalToken = pushToken ?: ""
        
        sipCoreManager?.register(
            username = username,
            password = password,
            domain = finalDomain,
            provider = pushProvider,
            token = finalToken
        )
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
     * Realiza una llamada
     * 
     * @param phoneNumber Número de teléfono a llamar
     * @param username Cuenta SIP a usar (opcional)
     * @param domain Dominio SIP (opcional)
     */
    fun makeCall(
        phoneNumber: String,
        username: String? = null,
        domain: String? = null
    ) {
        checkInitialized()
        
        val finalUsername = username ?: sipCoreManager?.getCurrentUsername()
        val finalDomain = domain ?: sipCoreManager?.getDefaultDomain() ?: ""
        
        if (finalUsername == null) {
            throw SipLibraryException("No registered account available for calling")
        }
        
        sipCoreManager?.makeCall(phoneNumber, finalUsername, finalDomain)
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
     * Envía tonos DTMF
     * 
     * @param digit Dígito DTMF (0-9, *, #, A-D)
     * @param duration Duración en milisegundos
     */
    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
        checkInitialized()
        return sipCoreManager?.sendDtmf(digit, duration) ?: false
    }
    
    /**
     * Envía una secuencia de tonos DTMF
     */
    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
        checkInitialized()
        return sipCoreManager?.sendDtmfSequence(digits, duration) ?: false
    }
    
    /**
     * Silencia/desmute el micrófono
     */
    fun toggleMute() {
        checkInitialized()
        sipCoreManager?.mute()
    }
    
    /**
     * Verifica si está silenciado
     */
    fun isMuted(): Boolean {
        checkInitialized()
        return sipCoreManager?.webRtcManager?.isMuted() ?: false
    }
    
    /**
     * Obtiene dispositivos de audio disponibles
     */
    fun getAudioDevices(): Pair<List<AudioDevice>, List<AudioDevice>> {
        checkInitialized()
        return sipCoreManager?.webRtcManager?.getAllAudioDevices() ?: Pair(emptyList(), emptyList())
    }
    
    /**
     * Cambia el dispositivo de audio de salida
     */
    fun changeAudioOutput(device: AudioDevice): Boolean {
        checkInitialized()
        return sipCoreManager?.webRtcManager?.changeAudioOutputDeviceDuringCall(device) ?: false
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
     * Obtiene el estado actual de la llamada
     */
    fun getCurrentCallState(): CallState {
        checkInitialized()
        return sipCoreManager?.callState ?: CallState.NONE
    }
    
    /**
     * Obtiene el estado de registro
     */
    fun getRegistrationState(): RegistrationState {
        checkInitialized()
        return sipCoreManager?.getRegistrationState() ?: RegistrationState.NONE
    }
    
    /**
     * Verifica si hay una llamada activa
     */
    fun hasActiveCall(): Boolean {
        checkInitialized()
        return sipCoreManager?.currentCall() ?: false
    }
    
    /**
     * Verifica si la llamada está conectada
     */
    fun isCallConnected(): Boolean {
        checkInitialized()
        return sipCoreManager?.currentCallConnected() ?: false
    }
    
    /**
     * Obtiene el Flow del estado de llamada para observar cambios
     */
    fun getCallStateFlow(): Flow<CallState> {
        checkInitialized()
        return CallStateManager.callStateFlow
    }
    
    /**
     * Obtiene el Flow del estado de registro para observar cambios
     */
    fun getRegistrationStateFlow(): Flow<RegistrationState> {
        checkInitialized()
        return RegistrationStateManager.registrationStateFlow
    }
    
    /**
     * Actualiza el token de push notifications
     */
    fun updatePushToken(token: String, provider: String = "fcm") {
        checkInitialized()
        sipCoreManager?.enterPushMode(token)
    }
    
    /**
     * Obtiene información de salud del sistema
     */
    fun getSystemHealthReport(): String {
        checkInitialized()
        return sipCoreManager?.getSystemHealthReport() ?: "Library not initialized"
    }
    
    /**
     * Verifica si el sistema está saludable
     */
    fun isSystemHealthy(): Boolean {
        checkInitialized()
        return sipCoreManager?.isSipCoreManagerHealthy() ?: false
    }
    
    /**
     * Configura callbacks para eventos de la biblioteca
     */
    fun setCallbacks(callbacks: SipCallbacks) {
        checkInitialized()
        sipCoreManager?.onCallTerminated = { callbacks.onCallTerminated() }
    }
    
    /**
     * Libera recursos de la biblioteca
     */
    fun dispose() {
        if (isInitialized) {
            sipCoreManager?.dispose()
            sipCoreManager = null
            isInitialized = false
            log.d(tag = TAG) { "EddysSipLibrary disposed" }
        }
    }
    
    private fun checkInitialized() {
        if (!isInitialized || sipCoreManager == null) {
            throw SipLibraryException("Library not initialized. Call initialize() first.")
        }
    }
    
    /**
     * Interface para callbacks de eventos
     */
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