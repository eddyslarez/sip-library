package com.eddyslarez.siplibrary.core

import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.interfaces.SipEventListener
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Dispatcher para eventos SIP que maneja múltiples listeners
 *
 * @author Eddys Larez
 */
class SipEventDispatcher {
    private val listeners = mutableSetOf<SipEventListener>()
    private val listenersMutex = Mutex()

    suspend fun addListener(listener: SipEventListener) {
        listenersMutex.withLock {
            listeners.add(listener)
        }
        log.d(tag = "SipEventDispatcher") { "Listener added. Total listeners: ${listeners.size}" }
    }

    suspend fun removeListener(listener: SipEventListener) {
        listenersMutex.withLock {
            listeners.remove(listener)
        }
        log.d(tag = "SipEventDispatcher") { "Listener removed. Total listeners: ${listeners.size}" }
    }

    suspend fun clearListeners() {
        listenersMutex.withLock {
            listeners.clear()
        }
        log.d(tag = "SipEventDispatcher") { "All listeners cleared" }
    }

    suspend fun getListenerCount(): Int {
        return listenersMutex.withLock { listeners.size }
    }

    // Métodos para disparar eventos a todos los listeners
    suspend fun onRegistrationStateChanged(state: RegistrationState, account: String) {
        dispatchEvent { it.onRegistrationStateChanged(state, account) }
    }

    suspend fun onRegistrationSuccess(account: String, expiresIn: Int) {
        dispatchEvent { it.onRegistrationSuccess(account, expiresIn) }
    }

    suspend fun onRegistrationFailed(account: String, reason: String) {
        dispatchEvent { it.onRegistrationFailed(account, reason) }
    }

    suspend fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
        dispatchEvent { it.onIncomingCall(callerNumber, callerName, callId) }
    }

    suspend fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) {
        dispatchEvent { it.onCallStateChanged(oldState, newState, callId) }
    }

    suspend fun onCallConnected(callId: String, duration: Long = 0) {
        dispatchEvent { it.onCallConnected(callId, duration) }
    }

    suspend fun onCallDisconnected(callId: String, reason: EddysSipLibrary.CallEndReason, duration: Long) {
        dispatchEvent { it.onCallDisconnected(callId, reason, duration) }
    }

    suspend fun onCallFailed(callId: String, error: String, errorCode: Int = -1) {
        dispatchEvent { it.onCallFailed(callId, error, errorCode) }
    }

    suspend fun onCallHold(callId: String, isOnHold: Boolean) {
        dispatchEvent { it.onCallHold(callId, isOnHold) }
    }

    suspend fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {
        dispatchEvent { it.onAudioDeviceChanged(oldDevice, newDevice) }
    }

    suspend fun onAudioDevicesAvailable(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {
        dispatchEvent { it.onAudioDevicesAvailable(inputDevices, outputDevices) }
    }

    suspend fun onMuteStateChanged(isMuted: Boolean) {
        dispatchEvent { it.onMuteStateChanged(isMuted) }
    }

    suspend fun onAudioLevelChanged(level: Float) {
        dispatchEvent { it.onAudioLevelChanged(level) }
    }

    suspend fun onNetworkStateChanged(isConnected: Boolean, networkType: String) {
        dispatchEvent { it.onNetworkStateChanged(isConnected, networkType) }
    }

    suspend fun onWebSocketStateChanged(isConnected: Boolean, url: String) {
        dispatchEvent { it.onWebSocketStateChanged(isConnected, url) }
    }

    suspend fun onReconnectionAttempt(attempt: Int, maxAttempts: Int) {
        dispatchEvent { it.onReconnectionAttempt(attempt, maxAttempts) }
    }

    suspend fun onPushModeChanged(isInPushMode: Boolean, reason: String) {
        dispatchEvent { it.onPushModeChanged(isInPushMode, reason) }
    }

    suspend fun onPushTokenUpdated(token: String, provider: String) {
        dispatchEvent { it.onPushTokenUpdated(token, provider) }
    }

    suspend fun onAppStateChanged(appState: EddysSipLibrary.AppState, previousState: EddysSipLibrary.AppState) {
        dispatchEvent { it.onAppStateChanged(appState, previousState) }
    }

    suspend fun onSipMessageReceived(message: String, messageType: String) {
        dispatchEvent { it.onSipMessageReceived(message, messageType) }
    }

    suspend fun onSipMessageSent(message: String, messageType: String) {
        dispatchEvent { it.onSipMessageSent(message, messageType) }
    }

    suspend fun onCallStatistics(stats: com.eddyslarez.siplibrary.models.CallStatistics) {
        dispatchEvent { it.onCallStatistics(stats) }
    }

    suspend fun onNetworkQuality(quality: EddysSipLibrary.NetworkQuality) {
        dispatchEvent { it.onNetworkQuality(quality) }
    }


    private suspend fun dispatchEvent(event: (SipEventListener) -> Unit) {
        val currentListeners = listenersMutex.withLock { listeners.toSet() }

        currentListeners.forEach { listener ->
            try {
                event(listener)
            } catch (e: Exception) {
                log.e(tag = "SipEventDispatcher") { "Error dispatching event to listener: ${e.message}" }
            }
        }
    }
}