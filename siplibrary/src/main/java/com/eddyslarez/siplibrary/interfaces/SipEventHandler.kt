package com.eddyslarez.siplibrary.interfaces

import com.eddyslarez.siplibrary.NetworkQuality
import com.eddyslarez.siplibrary.SipError
import com.eddyslarez.siplibrary.SipWarning
import com.eddyslarez.siplibrary.core.CallStatistics
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice

/**
 * Interfaces específicas para eventos SIP - Permite múltiples listeners
 *
 * @author Eddys Larez
 */

/**
 * Interface principal para todos los eventos SIP
 */
interface SipEventHandler

/**
 * Interface para eventos de registro SIP
 */
interface RegistrationEventListener : SipEventHandler {
    fun onRegistrationStateChanged(state: RegistrationState, account: String) {}
    fun onRegistrationSuccess(account: String, expiresIn: Int) {}
    fun onRegistrationFailed(account: String, reason: String) {}
    fun onUnregistrationComplete(account: String) {}
}

/**
 * Interface para eventos de llamadas
 */
interface CallEventListener : SipEventHandler {
    fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {}
    fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) {}
    fun onCallConnected(callId: String, duration: Long = 0) {}
    fun onCallDisconnected(callId: String, reason: CallEndReason, duration: Long) {}
    fun onCallFailed(callId: String, error: String, errorCode: Int = -1) {}
    fun onCallHold(callId: String, isOnHold: Boolean) {}
    fun onDtmfSent(callId: String, digit: Char, success: Boolean) {}
}

/**
 * Interface para eventos de audio
 */
interface AudioEventListener : SipEventHandler {
    fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {}
    fun onAudioDevicesAvailable(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {}
    fun onMuteStateChanged(isMuted: Boolean) {}
    fun onAudioLevelChanged(level: Float) {}
    fun onAudioQualityChanged(quality: AudioQuality) {}
}

/**
 * Interface para eventos de conectividad
 */
interface ConnectivityEventListener : SipEventHandler {
    fun onNetworkStateChanged(isConnected: Boolean, networkType: String) {}
    fun onWebSocketStateChanged(isConnected: Boolean, url: String) {}
    fun onReconnectionAttempt(attempt: Int, maxAttempts: Int) {}
    fun onConnectionQualityChanged(quality: NetworkQuality) {}
}

/**
 * Interface para eventos de la aplicación
 */
interface AppEventListener : SipEventHandler {
    fun onAppStateChanged(appState: AppState, previousState: AppState) {}
    fun onPushModeChanged(isInPushMode: Boolean, reason: String) {}
    fun onPushTokenUpdated(token: String, provider: String) {}
}

/**
 * Interface para eventos de mensajería SIP
 */
interface MessageEventListener : SipEventHandler {
    fun onSipMessageReceived(message: String, messageType: String) {}
    fun onSipMessageSent(message: String, messageType: String) {}
}

/**
 * Interface para eventos de estadísticas
 */
interface StatisticsEventListener : SipEventHandler {
    fun onCallStatistics(stats: CallStatistics) {}
    fun onNetworkQuality(quality: NetworkQuality) {}
    fun onPerformanceMetrics(metrics: PerformanceMetrics) {}
}

/**
 * Interface para eventos de errores y advertencias
 */
interface ErrorEventListener : SipEventHandler {
    fun onError(error: SipError) {}
    fun onWarning(warning: SipWarning) {}
    fun onDebugInfo(info: String, level: DebugLevel) {}
}

/**
 * Enum para niveles de debug
 */
enum class DebugLevel {
    VERBOSE, DEBUG, INFO, WARNING, ERROR
}

/**
 * Calidad de audio
 */
data class AudioQuality(
    val score: Float, // 0.0 - 1.0
    val signalToNoiseRatio: Float,
    val echoCancellationActive: Boolean,
    val noiseSuppressionActive: Boolean
)

/**
 * Métricas de rendimiento
 */
data class PerformanceMetrics(
    val cpuUsage: Float,
    val memoryUsage: Long,
    val batteryLevel: Float,
    val thermalState: String
)

/**
 * Razones de finalización de llamada extendidas
 */
enum class CallEndReason {
    USER_HANGUP,
    REMOTE_HANGUP,
    BUSY,
    DECLINED,
    TIMEOUT,
    NETWORK_ERROR,
    SERVER_ERROR,
    CANCELLED,
    NO_ANSWER,
    CALL_TRANSFER,
    CONFERENCE_END
}

/**
 * Estados de la aplicación extendidos
 */
enum class AppState {
    FOREGROUND,
    BACKGROUND,
    TERMINATED,
    LOCKED,
    INACTIVE
}