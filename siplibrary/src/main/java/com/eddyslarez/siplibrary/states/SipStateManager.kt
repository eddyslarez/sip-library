package com.eddyslarez.siplibrary.states

/**
 * Gestor de estado con StateFlow para reactividad
 *
 * @author Eddys Larez
 */
import com.eddyslarez.siplibrary.NetworkQuality
import com.eddyslarez.siplibrary.data.models.CallDirections
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.interfaces.AppState
import com.eddyslarez.siplibrary.interfaces.AudioQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class SipStateManager {

    // Estados principales con StateFlow
    private val _callState = MutableStateFlow(CallState.NONE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _registrationState = MutableStateFlow(RegistrationState.NONE)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _callerInfo = MutableStateFlow<CallerInfo?>(null)
    val callerInfo: StateFlow<CallerInfo?> = _callerInfo.asStateFlow()

    private val _currentCall = MutableStateFlow<CallInfo?>(null)
    val currentCall: StateFlow<CallInfo?> = _currentCall.asStateFlow()

    private val _audioState = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private val _networkState = MutableStateFlow(NetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _appState = MutableStateFlow(AppState.FOREGROUND)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Métodos para actualizar estados
    fun updateCallState(newState: CallState) {
        _callState.value = newState
    }

    fun updateRegistrationState(newState: RegistrationState) {
        _registrationState.value = newState
    }

    fun updateCallerInfo(info: CallerInfo?) {
        _callerInfo.value = info
    }

    fun updateCurrentCall(call: CallInfo?) {
        _currentCall.value = call
    }

    fun updateAudioState(state: AudioState) {
        _audioState.value = state
    }

    fun updateNetworkState(state: NetworkState) {
        _networkState.value = state
    }

    fun updateAppState(state: AppState) {
        _appState.value = state
    }

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    // Estados combinados para casos complejos
    val isCallActive: StateFlow<Boolean> = callState.map {
        it != CallState.NONE && it != CallState.ENDED
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Main),
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val canMakeCall: StateFlow<Boolean> = combine(
        callState,
        registrationState,
        networkState
    ) { call, registration, network ->
        call == CallState.NONE &&
                registration == RegistrationState.OK &&
                network.isConnected
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Main),
        started = SharingStarted.Eagerly,
        initialValue = false
    )
}

/**
 * Información del llamador
 */
data class CallerInfo(
    val number: String,
    val name: String? = null,
    val avatar: String? = null,
    val isContact: Boolean = false
)

/**
 * Información de la llamada actual
 */
data class CallInfo(
    val id: String,
    val number: String,
    val direction: CallDirections,
    val startTime: Long,
    val duration: Long = 0,
    val isOnHold: Boolean = false,
    val isMuted: Boolean = false,
    val quality: NetworkQuality? = null
)

/**
 * Estado del audio
 */
data class AudioState(
    val isMuted: Boolean = false,
    val currentInputDevice: AudioDevice? = null,
    val currentOutputDevice: AudioDevice? = null,
    val availableInputDevices: List<AudioDevice> = emptyList(),
    val availableOutputDevices: List<AudioDevice> = emptyList(),
    val audioLevel: Float = 0f,
    val quality: AudioQuality? = null
)

/**
 * Estado de la red
 */
data class NetworkState(
    val isConnected: Boolean = false,
    val networkType: String = "none",
    val quality: NetworkQuality? = null,
    val isRoaming: Boolean = false
)

/**
 * Estado de conexión
 */
data class ConnectionState(
    val isWebSocketConnected: Boolean = false,
    val webSocketUrl: String = "",
    val reconnectionAttempts: Int = 0,
    val lastConnectionTime: Long = 0,
    val pingLatency: Long = 0
)