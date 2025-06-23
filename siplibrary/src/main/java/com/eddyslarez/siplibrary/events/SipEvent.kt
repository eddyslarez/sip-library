package com.eddyslarez.siplibrary.events

import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.error.SipError
import com.eddyslarez.siplibrary.error.SipWarning

/**
 * Eventos del sistema SIP
 * Sealed class para type safety
 *
 * @author Eddys Larez
 */
sealed class SipEvent {
    // Eventos de biblioteca
    data class LibraryInitialized(val version: String) : SipEvent()
    object LibraryDisposed : SipEvent()

    // Eventos de registro
    data class RegistrationStateChanged(val state: RegistrationState, val account: String) : SipEvent()
    data class RegistrationSuccess(val account: String, val expiresIn: Int) : SipEvent()
    data class RegistrationFailed(val account: String, val reason: String) : SipEvent()

    // Eventos de llamadas
    data class IncomingCall(val callerNumber: String, val callerName: String?, val callId: String) : SipEvent()
    data class CallStateChanged(val oldState: CallState, val newState: CallState, val callId: String) : SipEvent()
    data class CallConnected(val callId: String, val duration: Long = 0) : SipEvent()
    data class CallDisconnected(val callId: String, val reason: EddysSipLibrary.CallEndReason, val duration: Long) : SipEvent()
    data class CallFailed(val callId: String, val error: String, val errorCode: Int = -1) : SipEvent()
    data class CallHold(val callId: String, val isOnHold: Boolean) : SipEvent()

    // Eventos de audio
    data class AudioDeviceChanged(val oldDevice: AudioDevice?, val newDevice: AudioDevice) : SipEvent()
    data class AudioDevicesAvailable(val inputDevices: List<AudioDevice>, val outputDevices: List<AudioDevice>) : SipEvent()
    data class MuteStateChanged(val isMuted: Boolean) : SipEvent()
    data class AudioLevelChanged(val level: Float) : SipEvent()

    // Eventos de conectividad
    data class NetworkStateChanged(val isConnected: Boolean, val networkType: String) : SipEvent()
    data class WebSocketStateChanged(val isConnected: Boolean, val url: String) : SipEvent()
    data class ReconnectionAttempt(val attempt: Int, val maxAttempts: Int) : SipEvent()

    // Eventos de modo push
    data class PushModeChanged(val isInPushMode: Boolean, val reason: String) : SipEvent()
    data class PushTokenUpdated(val token: String, val provider: String) : SipEvent()

    // Eventos de ciclo de vida de la app
    data class AppStateChanged(val appState: EddysSipLibrary.AppState, val previousState: EddysSipLibrary.AppState) : SipEvent()

    // Eventos de mensajería SIP
    data class SipMessageReceived(val message: String, val messageType: String) : SipEvent()
    data class SipMessageSent(val message: String, val messageType: String) : SipEvent()

    // Eventos de estadísticas
    data class CallStatistics(val stats: com.eddyslarez.siplibrary.models.CallStatistics) : SipEvent()
    data class NetworkQuality(val quality: EddysSipLibrary.NetworkQuality) : SipEvent()

    // Eventos de errores
    data class Error(val error: SipError) : SipEvent()
    data class Warning(val warning: SipWarning) : SipEvent()
}