package com.eddyslarez.siplibrary.interfaces

import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice

/**
 * Interface para eventos de la biblioteca SIP
 * Permite múltiples listeners y mejor organización
 *
 * @author Eddys Larez
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
    fun onCallDisconnected(callId: String, reason: EddysSipLibrary.CallEndReason, duration: Long) {}
    fun onCallFailed(callId: String, error: String, errorCode: Int = -1) {}
    fun onCallHold(callId: String, isOnHold: Boolean) {}

    // Eventos de audio
    fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {}
    fun onAudioDevicesAvailable(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {}
    fun onMuteStateChanged(isMuted: Boolean) {}
    fun onAudioLevelChanged(level: Float) {}

    // Eventos de conectividad
    fun onNetworkStateChanged(isConnected: Boolean, networkType: String) {}
    fun onWebSocketStateChanged(isConnected: Boolean, url: String) {}
    fun onReconnectionAttempt(attempt: Int, maxAttempts: Int) {}

    // Eventos de modo push
    fun onPushModeChanged(isInPushMode: Boolean, reason: String) {}
    fun onPushTokenUpdated(token: String, provider: String) {}

    // Eventos de ciclo de vida de la app
    fun onAppStateChanged(appState: EddysSipLibrary.AppState, previousState: EddysSipLibrary.AppState) {}

    // Eventos de mensajería SIP
    fun onSipMessageReceived(message: String, messageType: String) {}
    fun onSipMessageSent(message: String, messageType: String) {}

    // Eventos de estadísticas
    fun onCallStatistics(stats: com.eddyslarez.siplibrary.models.CallStatistics) {}
    fun onNetworkQuality(quality: EddysSipLibrary.NetworkQuality) {}

    // Eventos de errores
    fun onError(error: EddysSipLibrary.SipError) {}
    fun onWarning(warning: EddysSipLibrary.SipWarning) {}
}