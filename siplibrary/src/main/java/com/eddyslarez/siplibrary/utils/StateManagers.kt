package com.eddyslarez.siplibrary.utils

import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
//
///**
// * Gestores de estado para llamadas y registro
// *
// * @author Eddys Larez
// */
//object CallStateManager {
//    private val _callStateFlow = MutableStateFlow(CallState.NONE)
//    val callStateFlow: StateFlow<CallState> = _callStateFlow.asStateFlow()
//
//    private val _callerNumberFlow = MutableStateFlow("")
//    val callerNumberFlow: StateFlow<String> = _callerNumberFlow.asStateFlow()
//
//    private val _callIdFlow = MutableStateFlow("")
//    val callIdFlow: StateFlow<String> = _callIdFlow.asStateFlow()
//
//    private val _isBackgroundFlow = MutableStateFlow(false)
//    val isBackgroundFlow: StateFlow<Boolean> = _isBackgroundFlow.asStateFlow()
//
//    fun updateCallState(newState: CallState) {
//        _callStateFlow.value = newState
//    }
//
//    fun callerNumber(number: String) {
//        _callerNumberFlow.value = number
//    }
//
//    fun callId(id: String) {
//        _callIdFlow.value = id
//    }
//
//    fun setBackground() {
//        _isBackgroundFlow.value = true
//    }
//
//    fun setForeground() {
//        _isBackgroundFlow.value = false
//    }
//
//    fun setAppClosed() {
//        _isBackgroundFlow.value = true
//    }
//
//    fun getCurrentCallState(): CallState = _callStateFlow.value
//    fun getCurrentCallerNumber(): String = _callerNumberFlow.value
//    fun getCurrentCallId(): String = _callIdFlow.value
//}
//
//object RegistrationStateManager {
//    private val _registrationStateFlow = MutableStateFlow(RegistrationState.NONE)
//    val registrationStateFlow: StateFlow<RegistrationState> = _registrationStateFlow.asStateFlow()
//
//    fun updateCallState(newState: RegistrationState) {
//        _registrationStateFlow.value = newState
//    }
//
//    fun getCurrentRegistrationState(): RegistrationState = _registrationStateFlow.value
//}

/**
 * Gestor de estado unificado usando StateFlow
 *
 * @author Eddys Larez
 */
object StateManager {
    private val TAG = "StateManager"

    // Call State
    private val _callStateFlow = MutableStateFlow(CallState.NONE)
    val callStateFlow: StateFlow<CallState> = _callStateFlow.asStateFlow()

    private val _callerNumberFlow = MutableStateFlow("")
    val callerNumberFlow: StateFlow<String> = _callerNumberFlow.asStateFlow()

    private val _callIdFlow = MutableStateFlow("")
    val callIdFlow: StateFlow<String> = _callIdFlow.asStateFlow()

    private val _callDurationFlow = MutableStateFlow(0L)
    val callDurationFlow: StateFlow<Long> = _callDurationFlow.asStateFlow()

    // Registration State
    private val _registrationStateFlow = MutableStateFlow(RegistrationState.NONE)
    val registrationStateFlow: StateFlow<RegistrationState> = _registrationStateFlow.asStateFlow()

    private val _registeredAccountsFlow = MutableStateFlow(emptySet<String>())
    val registeredAccountsFlow: StateFlow<Set<String>> = _registeredAccountsFlow.asStateFlow()

    // App State
    private val _appStateFlow = MutableStateFlow(EddysSipLibrary.AppState.FOREGROUND)
    val appStateFlow: StateFlow<EddysSipLibrary.AppState> = _appStateFlow.asStateFlow()

    // Network State
    private val _networkStateFlow = MutableStateFlow(NetworkState(isConnected = true, networkType = "unknown"))
    val networkStateFlow: StateFlow<NetworkState> = _networkStateFlow.asStateFlow()

    // Audio State
    private val _audioStateFlow = MutableStateFlow(AudioState())
    val audioStateFlow: StateFlow<AudioState> = _audioStateFlow.asStateFlow()

    // WebSocket State
    private val _webSocketStateFlow = MutableStateFlow(WebSocketState(isConnected = false, url = ""))
    val webSocketStateFlow: StateFlow<WebSocketState> = _webSocketStateFlow.asStateFlow()

    // Push State
    private val _pushStateFlow = MutableStateFlow(PushState())
    val pushStateFlow: StateFlow<PushState> = _pushStateFlow.asStateFlow()

    // Ringtone State
    private val _ringtoneStateFlow = MutableStateFlow(RingtoneState())
    val ringtoneStateFlow: StateFlow<RingtoneState> = _ringtoneStateFlow.asStateFlow()

    // Call State Methods
    fun updateCallState(newState: CallState) {
        log.d(tag = TAG) { "Call state changed: ${_callStateFlow.value} -> $newState" }
        _callStateFlow.value = newState
    }

    fun updateCallerNumber(number: String) {
        _callerNumberFlow.value = number
    }

    fun updateCallId(id: String) {
        _callIdFlow.value = id
    }

    fun updateCallDuration(duration: Long) {
        _callDurationFlow.value = duration
    }

    // Registration State Methods
    fun updateRegistrationState(newState: RegistrationState) {
        log.d(tag = TAG) { "Registration state changed: ${_registrationStateFlow.value} -> $newState" }
        _registrationStateFlow.value = newState
    }

    fun addRegisteredAccount(account: String) {
        _registeredAccountsFlow.value = _registeredAccountsFlow.value + account
    }

    fun removeRegisteredAccount(account: String) {
        _registeredAccountsFlow.value = _registeredAccountsFlow.value - account
    }

    // App State Methods
    fun updateAppState(newState: EddysSipLibrary.AppState) {
        log.d(tag = TAG) { "App state changed: ${_appStateFlow.value} -> $newState" }
        _appStateFlow.value = newState
    }

    // Network State Methods
    fun updateNetworkState(isConnected: Boolean, networkType: String) {
        _networkStateFlow.value = NetworkState(isConnected, networkType)
    }

    // Audio State Methods
    fun updateAudioState(audioState: AudioState) {
        _audioStateFlow.value = audioState
    }

    fun updateMuteState(isMuted: Boolean) {
        _audioStateFlow.value = _audioStateFlow.value.copy(isMuted = isMuted)
    }

    fun updateCurrentAudioDevice(inputDevice: AudioDevice?, outputDevice: AudioDevice?) {
        _audioStateFlow.value = _audioStateFlow.value.copy(
            currentInputDevice = inputDevice,
            currentOutputDevice = outputDevice
        )
    }

    fun updateAvailableAudioDevices(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {
        _audioStateFlow.value = _audioStateFlow.value.copy(
            availableInputDevices = inputDevices,
            availableOutputDevices = outputDevices
        )
    }

    // WebSocket State Methods
    fun updateWebSocketState(isConnected: Boolean, url: String) {
        _webSocketStateFlow.value = WebSocketState(isConnected, url)
    }

    // Push State Methods
    fun updatePushState(pushState: PushState) {
        _pushStateFlow.value = pushState
    }

    // Ringtone State Methods
    fun updateRingtoneState(ringtoneState: RingtoneState) {
        _ringtoneStateFlow.value = ringtoneState
    }

    fun setIncomingRingtoneState(isPlaying: Boolean) {
        _ringtoneStateFlow.value = _ringtoneStateFlow.value.copy(isIncomingPlaying = isPlaying)
    }

    fun setOutgoingRingtoneState(isPlaying: Boolean) {
        _ringtoneStateFlow.value = _ringtoneStateFlow.value.copy(isOutgoingPlaying = isPlaying)
    }

    fun setRingtoneEnabled(enabled: Boolean) {
        _ringtoneStateFlow.value = _ringtoneStateFlow.value.copy(isEnabled = enabled)
    }

    // Getters
    fun getCurrentCallState(): CallState = _callStateFlow.value
    fun getCurrentCallerNumber(): String = _callerNumberFlow.value
    fun getCurrentCallId(): String = _callIdFlow.value
    fun getCurrentCallDuration(): Long = _callDurationFlow.value
    fun getCurrentRegistrationState(): RegistrationState = _registrationStateFlow.value
    fun getCurrentAppState(): EddysSipLibrary.AppState = _appStateFlow.value
    fun getCurrentNetworkState(): NetworkState = _networkStateFlow.value
    fun getCurrentAudioState(): AudioState = _audioStateFlow.value
    fun getCurrentWebSocketState(): WebSocketState = _webSocketStateFlow.value
    fun getCurrentPushState(): PushState = _pushStateFlow.value
    fun getCurrentRingtoneState(): RingtoneState = _ringtoneStateFlow.value

    // Data classes for states
    data class NetworkState(
        val isConnected: Boolean,
        val networkType: String
    )

    data class AudioState(
        val isMuted: Boolean = false,
        val isAudioEnabled: Boolean = true,
        val currentInputDevice: AudioDevice? = null,
        val currentOutputDevice: AudioDevice? = null,
        val availableInputDevices: List<AudioDevice> = emptyList(),
        val availableOutputDevices: List<AudioDevice> = emptyList(),
        val audioLevel: Float = 0f
    )

    data class WebSocketState(
        val isConnected: Boolean,
        val url: String
    )

    data class PushState(
        val isInPushMode: Boolean = false,
        val token: String = "",
        val provider: String = "fcm"
    )

    data class RingtoneState(
        val isEnabled: Boolean = true,
        val isIncomingPlaying: Boolean = false,
        val isOutgoingPlaying: Boolean = false
    )
}