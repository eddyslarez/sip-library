package com.eddyslarez.siplibrary.core

import com.eddyslarez.siplibrary.events.SipEvent
import com.eddyslarez.siplibrary.interfaces.SipEventListener
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sistema de eventos global independiente
 * Permite agregar listeners desde cualquier parte de la aplicación
 *
 * @author Eddys Larez
 */
object GlobalEventBus {
    private val TAG = "GlobalEventBus"

    private val listeners = mutableSetOf<SipEventListener>()
    private val listenersMutex = Mutex()
    private val eventFlow = MutableSharedFlow<SipEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var isInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Inicializa el sistema de eventos
     */
    fun initialize() {
        if (isInitialized) return

        log.d(tag = TAG) { "Initializing GlobalEventBus" }

        // Procesar eventos
        coroutineScope.launch {
            eventFlow.collect { event ->
                dispatchEvent(event)
            }
        }

        isInitialized = true
        log.d(tag = TAG) { "GlobalEventBus initialized" }
    }

    /**
     * Agrega un listener desde cualquier parte de la aplicación
     */
    suspend fun addListener(listener: SipEventListener) {
        listenersMutex.withLock {
            listeners.add(listener)
        }
        log.d(tag = TAG) { "Listener added. Total: ${listeners.size}" }
    }

    /**
     * Remueve un listener
     */
    suspend fun removeListener(listener: SipEventListener) {
        listenersMutex.withLock {
            listeners.remove(listener)
        }
        log.d(tag = TAG) { "Listener removed. Total: ${listeners.size}" }
    }

    /**
     * Limpia todos los listeners
     */
    suspend fun clearListeners() {
        listenersMutex.withLock {
            listeners.clear()
        }
        log.d(tag = TAG) { "All listeners cleared" }
    }

    /**
     * Obtiene la cantidad de listeners
     */
    fun getListenerCount(): Int {
        return listeners.size
    }

    /**
     * Emite un evento de manera thread-safe
     */
    fun emit(event: SipEvent) {
        if (!isInitialized) {
            log.w(tag = TAG) { "EventBus not initialized, ignoring event: $event" }
            return
        }

        val emitResult = eventFlow.tryEmit(event)
        if (!emitResult) {
            log.w(tag = TAG) { "Failed to emit event: $event" }
        }
    }

    /**
     * Obtiene el flow de eventos para observación reactiva
     */
    fun getEventFlow(): SharedFlow<SipEvent> = eventFlow.asSharedFlow()

    /**
     * Obtiene un flow filtrado por tipo de evento - CORREGIDO
     */
    fun <T : SipEvent> getEventFlowOfType(eventClass: Class<T>): Flow<T> {
        return eventFlow.filter { eventClass.isInstance(it) }.map { eventClass.cast(it)!! }
    }

    /**
     * Despacha eventos a todos los listeners
     */
    private suspend fun dispatchEvent(event: SipEvent) {
        val currentListeners = listenersMutex.withLock { listeners.toSet() }

        currentListeners.forEach { listener ->
            try {
                when (event) {
                    is SipEvent.LibraryInitialized -> listener.onLibraryInitialized(event.version)
                    is SipEvent.LibraryDisposed -> listener.onLibraryDisposed()
                    is SipEvent.RegistrationStateChanged -> listener.onRegistrationStateChanged(event.state, event.account)
                    is SipEvent.RegistrationSuccess -> listener.onRegistrationSuccess(event.account, event.expiresIn)
                    is SipEvent.RegistrationFailed -> listener.onRegistrationFailed(event.account, event.reason)
                    is SipEvent.IncomingCall -> listener.onIncomingCall(event.callerNumber, event.callerName, event.callId)
                    is SipEvent.CallStateChanged -> listener.onCallStateChanged(event.oldState, event.newState, event.callId)
                    is SipEvent.CallConnected -> listener.onCallConnected(event.callId, event.duration)
                    is SipEvent.CallDisconnected -> listener.onCallDisconnected(event.callId, event.reason, event.duration)
                    is SipEvent.CallFailed -> listener.onCallFailed(event.callId, event.error, event.errorCode)
                    is SipEvent.CallHold -> listener.onCallHold(event.callId, event.isOnHold)
                    is SipEvent.AudioDeviceChanged -> listener.onAudioDeviceChanged(event.oldDevice, event.newDevice)
                    is SipEvent.AudioDevicesAvailable -> listener.onAudioDevicesAvailable(event.inputDevices, event.outputDevices)
                    is SipEvent.MuteStateChanged -> listener.onMuteStateChanged(event.isMuted)
                    is SipEvent.AudioLevelChanged -> listener.onAudioLevelChanged(event.level)
                    is SipEvent.NetworkStateChanged -> listener.onNetworkStateChanged(event.isConnected, event.networkType)
                    is SipEvent.WebSocketStateChanged -> listener.onWebSocketStateChanged(event.isConnected, event.url)
                    is SipEvent.ReconnectionAttempt -> listener.onReconnectionAttempt(event.attempt, event.maxAttempts)
                    is SipEvent.PushModeChanged -> listener.onPushModeChanged(event.isInPushMode, event.reason)
                    is SipEvent.PushTokenUpdated -> listener.onPushTokenUpdated(event.token, event.provider)
                    is SipEvent.AppStateChanged -> listener.onAppStateChanged(event.appState, event.previousState)
                    is SipEvent.SipMessageReceived -> listener.onSipMessageReceived(event.message, event.messageType)
                    is SipEvent.SipMessageSent -> listener.onSipMessageSent(event.message, event.messageType)
                    is SipEvent.CallStatistics -> listener.onCallStatistics(event.stats)
                    is SipEvent.NetworkQuality -> listener.onNetworkQuality(event.quality)
                    is SipEvent.Error -> listener.onError(event.error)
                    is SipEvent.Warning -> listener.onWarning(event.warning)
                    is SipEvent.TranslationStateChanged -> listener.onTranslationStateChanged(event.isActive, event.sourceLanguage, event.targetLanguage)
                    is SipEvent.TranslationError -> listener.onTranslationError(event.error)
                    is SipEvent.TranslationCompleted -> listener.onTranslationCompleted(event.originalText, event.translatedText)
                }
            } catch (e: Exception) {
                log.e(tag = TAG) { "Error dispatching event to listener: ${e.message}" }
            }
        }
    }

    /**
     * Libera recursos
     */
    fun dispose() {
        if (isInitialized) {
            coroutineScope.cancel()
            runBlocking {
                clearListeners()
            }
            isInitialized = false
            log.d(tag = TAG) { "GlobalEventBus disposed" }
        }
    }
}