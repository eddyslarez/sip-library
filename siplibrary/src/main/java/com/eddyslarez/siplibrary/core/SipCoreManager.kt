package com.eddyslarez.siplibrary.core

import android.app.Application
import android.net.Uri
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.*
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.data.services.audio.CallHoldManager
import com.eddyslarez.siplibrary.data.services.audio.EnhancedAudioDeviceManager
import com.eddyslarez.siplibrary.data.services.audio.EnhancedRingtoneManager
import com.eddyslarez.siplibrary.data.services.audio.WebRtcConnectionState
import com.eddyslarez.siplibrary.data.services.audio.WebRtcEventListener
import com.eddyslarez.siplibrary.data.services.audio.WebRtcManagerFactory
import com.eddyslarez.siplibrary.data.services.sip.SipMessageHandler
import com.eddyslarez.siplibrary.data.services.websocket.MultiplatformWebSocket
import com.eddyslarez.siplibrary.data.services.websocket.WebSocket
import com.eddyslarez.siplibrary.data.store.SettingsDataStore
import com.eddyslarez.siplibrary.error.ErrorCategory
import com.eddyslarez.siplibrary.error.ErrorCodes
import com.eddyslarez.siplibrary.error.SipError
import com.eddyslarez.siplibrary.error.SipLibraryException
import com.eddyslarez.siplibrary.events.SipEvent
import com.eddyslarez.siplibrary.interfaces.CallManager
import com.eddyslarez.siplibrary.interfaces.SipEventListener
import com.eddyslarez.siplibrary.models.CallStatistics
import com.eddyslarez.siplibrary.platform.PlatformInfo
import com.eddyslarez.siplibrary.platform.PlatformRegistration
import com.eddyslarez.siplibrary.platform.WindowManager
import com.eddyslarez.siplibrary.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

//
///**
// * Enhanced SIP Core Manager - Gestor principal del core SIP con funcionalidades extendidas
// *
// * @author Eddys Larez
// */
//class SipCoreManager private constructor(
///**
// * Enhanced SIP Core Manager con interfaces y múltiples listeners
// *
// * @author Eddys Larez
// */
//class SipCoreManager private constructor(
//    private val application: Application,
//    private val config: EddysSipLibrary.SipConfig,
//    private val eventDispatcher: SipEventDispatcher,
//    private val audioManager: EnhancedAudioDeviceManager,
//    private val ringtoneManager: EnhancedRingtoneManager,
//    private val windowManager: WindowManager,
//    private val platformInfo: PlatformInfo,
//    private val settingsDataStore: SettingsDataStore,
//) : CallManager {
//
//    private val TAG = "EnhancedSipCoreManager"
//
//    // Core managers
//    val callHistoryManager = CallHistoryManager()
//    private val messageHandler: SipMessageHandler by lazy {
//        SipMessageHandler(this, eventDispatcher)
//    }
//
//    // WebRTC and networking
//    val webRtcManager = WebRtcManagerFactory.createWebRtcManager(application)
//    private val platformRegistration = PlatformRegistration(application)
//    private val callHoldManager = CallHoldManager(webRtcManager)
//
//    // State management
//    private val activeAccounts = HashMap<String, AccountInfo>()
//    private var currentAccountInfo: AccountInfo? = null
//    private var reconnectionInProgress = false
//    private var lastConnectionCheck = 0L
//    private val connectionCheckInterval = 30000L
//
//    // DTMF management
//    private val dtmfQueue = mutableListOf<DtmfRequest>()
//    private var isDtmfProcessing = false
//    private val dtmfMutex = Mutex()
//
//    // Call management
//    var onCallTerminated: (() -> Unit)? = null
//    private var isCallFromPush = false
//    private var callStartTimeMillis: Long = 0
//
//    // Enhanced features
//    private var isPushMode = false
//    private var currentUserAgent = config.userAgent
//    private var customHeaders = config.customHeaders.toMutableMap()
//    private var networkQualityMonitor: NetworkQualityMonitor? = null
//    private var callStatistics: CallStatistics? = null
//
//    // Scopes para corrutinas
//    private val coreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
//
//    companion object {
//        private const val WEBSOCKET_PROTOCOL = "sip"
//        private const val REGISTRATION_CHECK_INTERVAL_MS = 30 * 1000L
//
//        fun createInstance(
//            application: Application,
//            config: EddysSipLibrary.SipConfig,
//            eventDispatcher: SipEventDispatcher
//        ): SipCoreManager {
//            val audioManager = EnhancedAudioDeviceManager(application, eventDispatcher)
//            val ringtoneManager = EnhancedRingtoneManager(application, eventDispatcher)
//
//            return SipCoreManager(
//                application = application,
//                config = config,
//                eventDispatcher = eventDispatcher,
//                audioManager = audioManager,
//                ringtoneManager = ringtoneManager,
//                windowManager = WindowManager(),
//                platformInfo = PlatformInfo(),
//                settingsDataStore = SettingsDataStore(application)
//            )
//        }
//    }
//
//    // Public accessors for SipMessageHandler
//    fun setCurrentAccountInfo(accountInfo: AccountInfo) {
//        currentAccountInfo = accountInfo
//    }
//
//    fun setCallFromPush(fromPush: Boolean) {
//        isCallFromPush = fromPush
//    }
//
//    fun setCallStartTime(timeMillis: Long) {
//        callStartTimeMillis = timeMillis
//    }
//
//    fun getCallStartTime(): Long = callStartTimeMillis
//
//    fun bringToFront() {
//        windowManager.bringToFront()
//    }
//
//    fun initialize() {
//        log.d(tag = TAG) { "Initializing Enhanced SIP Core Manager" }
//
//        webRtcManager.initialize()
//        setupWebRtcEventListener()
//        setupPlatformLifecycleObservers()
//        setupEnhancedFeatures()
//        startConnectionHealthCheck()
//        startStateMonitoring()
//    }
//
//    private fun setupEnhancedFeatures() {
//        networkQualityMonitor = NetworkQualityMonitor { quality ->
//            coreScope.launch {
//                eventDispatcher.onNetworkQuality(quality)
//            }
//        }
//
//        // Inicializar dispositivos de audio
//        coreScope.launch {
//            try {
//                val inputDevices = audioManager.getAudioInputDevices()
//                val outputDevices = audioManager.getAudioOutputDevices()
//                StateManager.updateAvailableAudioDevices(inputDevices, outputDevices)
//                eventDispatcher.onAudioDevicesAvailable(inputDevices, outputDevices)
//            } catch (e: Exception) {
//                log.e(tag = TAG) { "Error initializing audio devices: ${e.message}" }
//            }
//        }
//    }
//    private fun startStateMonitoring() {
//        // Monitor call state changes - CORREGIDO
//        coreScope.launch {
//            StateManager.callStateFlow.collect { newState ->
//                // Ya no hay loop infinito porque no llamamos updateCallState aquí
//                log.d(tag = TAG) { "State monitor detected call state: $newState" }
//                handleCallStateChangeEffects(newState)
//            }
//        }
//
//        // Monitor registration state changes
//        coreScope.launch {
//            StateManager.registrationStateFlow.collect { state ->
//                log.d(tag = TAG) { "State monitor detected registration state: $state" }
//                val currentAccount = getCurrentUsername() ?: "unknown"
//                eventDispatcher.onRegistrationStateChanged(state, currentAccount)
//                handleRegistrationStateChange(state, currentAccount)
//            }
//        }
//
//        // Monitor caller number changes - NUEVO
//        coreScope.launch {
//            StateManager.callerNumberFlow.collect { number ->
//                if (number.isNotEmpty()) {
//                    log.d(tag = TAG) { "Caller number updated: $number" }
//                }
//            }
//        }
//
//        // Monitor call duration - NUEVO
//        coreScope.launch {
//            StateManager.callDurationFlow.collect { duration ->
//                if (duration > 0) {
//                    // Actualizar estadísticas de llamada cada 5 segundos
//                    if (duration % 5000 == 0L) {
//                        updateCallStatistics()
//                    }
//                }
//            }
//        }
//    }
//
////    private fun startStateMonitoring() {
////        // Monitor call state changes
////        coreScope.launch {
////            StateManager.callStateFlow.collect { newState ->
////                val oldState = StateManager.getCurrentCallState()
////                if (oldState != newState) {
////                    val callId = StateManager.getCurrentCallId()
////                    eventDispatcher.onCallStateChanged(oldState, newState, callId)
////                    handleCallStateChange(oldState, newState, callId)
////                }
////            }
////        }
////
////        // Monitor registration state changes
////        coreScope.launch {
////            StateManager.registrationStateFlow.collect { state ->
////                val currentAccount = getCurrentUsername() ?: "unknown"
////                eventDispatcher.onRegistrationStateChanged(state, currentAccount)
////                handleRegistrationStateChange(state, currentAccount)
////            }
////        }
////
////        // Monitor audio state changes
////        coreScope.launch {
////            StateManager.audioStateFlow.collect { audioState ->
////                // Handle audio state changes
////                if (audioState.currentInputDevice != null && audioState.currentOutputDevice != null) {
////                    eventDispatcher.onAudioDeviceChanged(null, audioState.currentOutputDevice!!)
////                }
////            }
////        }
////
////        // Monitor ringtone state
////        coreScope.launch {
////            StateManager.ringtoneStateFlow.collect { ringtoneState ->
////                if (!ringtoneState.isEnabled && ringtoneState.isIncomingPlaying) {
////                    ringtoneManager.stopIncomingRingtone()
////                }
////                if (!ringtoneState.isEnabled && ringtoneState.isOutgoingPlaying) {
////                    ringtoneManager.stopOutgoingRingtone()
////                }
////            }
////        }
////    }
//
//    private suspend fun handleCallStateChange(
//        oldState: CallState,
//        newState: CallState,
//        callId: String
//    ) {
//        when (newState) {
//            CallState.CONNECTED -> {
//                eventDispatcher.onCallConnected(callId, calculateCallDuration())
//                startCallStatisticsMonitoring()
//                // Detener ringtones cuando se conecta
//                ringtoneManager.stopAllRingtones()
//            }
//
//            CallState.ENDED -> {
//                val duration = calculateCallDuration()
//                eventDispatcher.onCallDisconnected(
//                    callId = callId,
//                    reason = determineEndReason(oldState),
//                    duration = duration
//                )
//                stopCallStatisticsMonitoring()
//                // Detener ringtones cuando termina
//                ringtoneManager.stopAllRingtones()
//            }
//
//            CallState.INCOMING -> {
//                val callerNumber = StateManager.getCurrentCallerNumber()
//                eventDispatcher.onIncomingCall(callerNumber, null, callId)
//                // Reproducir ringtone de entrada
//                if (config.ringtoneConfig.enableIncomingRingtone) {
//                    ringtoneManager.playIncomingRingtone()
//                }
//            }
//
//            CallState.CALLING, CallState.OUTGOING -> {
//                // Reproducir ringtone de salida
//                if (config.ringtoneConfig.enableOutgoingRingtone) {
//                    ringtoneManager.playOutgoingRingtone()
//                }
//            }
//
//            CallState.RINGING -> {
//                // Detener ringtone de salida cuando empieza a sonar
//                ringtoneManager.stopOutgoingRingtone()
//            }
//
//            else -> {}
//        }
//    }
//
//    private suspend fun handleCallStateChangeEffects(newState: CallState) {
//        when (newState) {
//            CallState.INCOMING -> {
//                // Reproducir ringtone de entrada
//                if (config.ringtoneConfig.enableIncomingRingtone) {
//                    ringtoneManager.playIncomingRingtone()
//                }
//                eventDispatcher.onIncomingCall(currentAccountInfo?.currentCallData?.from ?: "", null,
//                    currentAccountInfo?.currentCallData?.callId ?: ""
//                )
//
//                // Auto-accept si está configurado
//                if (config.autoAcceptDelay > 0) {
//                    delay(config.autoAcceptDelay)
//                    if (StateManager.getCurrentCallState() == CallState.INCOMING) {
//                        acceptCall()
//                    }
//                }
//            }
//
//            CallState.CALLING, CallState.OUTGOING -> {
//                // Reproducir ringtone de salida
//                if (config.ringtoneConfig.enableOutgoingRingtone) {
//                    ringtoneManager.playOutgoingRingtone()
//                }
//            }
//
//            CallState.CONNECTED -> {
//                // Detener todos los ringtones
//                ringtoneManager.stopAllRingtones()
//                startCallStatisticsMonitoring()
//            }
//
//            CallState.ENDED, CallState.DECLINED -> {
//                // Detener ringtones y limpiar
//                ringtoneManager.stopAllRingtones()
//                stopCallStatisticsMonitoring()
//                clearCallData()
//            }
//
//            CallState.RINGING -> {
//                // Detener ringtone de salida cuando empieza a sonar del otro lado
//                ringtoneManager.stopOutgoingRingtone()
//            }
//
//            else -> {}
//        }
//    }
//
//    private fun clearCallData() {
//        StateManager.updateCallerNumber("")
//        StateManager.updateCallId("")
//        callStatistics = null
//    }
//
//    private suspend fun handleRegistrationStateChange(state: RegistrationState, account: String) {
//        when (state) {
//            RegistrationState.OK -> {
//                eventDispatcher.onRegistrationSuccess(account, config.registrationExpiresSeconds)
//            }
//
//            RegistrationState.FAILED -> {
//                eventDispatcher.onRegistrationFailed(account, "Registration failed")
//            }
//
//            else -> {}
//        }
//    }
//
//    private fun setupWebRtcEventListener() {
//        webRtcManager.setListener(object : WebRtcEventListener {
//            override fun onIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
//                // Handle ICE candidate
//            }
//
//            override fun onConnectionStateChange(state: WebRtcConnectionState) {
//                when (state) {
//                    WebRtcConnectionState.CONNECTED -> handleWebRtcConnected()
//                    WebRtcConnectionState.CLOSED -> handleWebRtcClosed()
//                    else -> {}
//                }
//            }
//
//            override fun onRemoteAudioTrack() {
//                log.d(tag = TAG) { "Remote audio track received" }
//            }
//
//            override fun onAudioDeviceChanged(device: AudioDevice) {
//                log.d(tag = TAG) { "Audio device changed: ${device.name}" }
//                coreScope.launch {
//                    eventDispatcher.onAudioDeviceChanged(null, device)
//                }
//            }
//        })
//    }
//
//    private fun setupPlatformLifecycleObservers() {
//        platformRegistration.setupNotificationObservers(object : AppLifecycleListener {
//            override fun onEvent(event: AppLifecycleEvent) {
//                when (event) {
//                    AppLifecycleEvent.EnterBackground -> {
//                        StateManager.updateAppState(EddysSipLibrary.AppState.BACKGROUND)
//                        if (config.autoEnterPushOnBackground) {
//                            enterPushMode("App entered background")
//                        }
//                        refreshAllRegistrationsWithNewUserAgent()
//                    }
//
//                    AppLifecycleEvent.EnterForeground -> {
//                        StateManager.updateAppState(EddysSipLibrary.AppState.FOREGROUND)
//                        if (config.autoExitPushOnForeground) {
//                            exitPushMode("App entered foreground")
//                        }
//                        refreshAllRegistrationsWithNewUserAgent()
//                    }
//
//                    else -> {}
//                }
//            }
//        })
//    }
//
//    // Implementación de CallManager interface
//    override suspend fun makeCall(
//        phoneNumber: String,
//        username: String,
//        domain: String,
//        customHeaders: Map<String, String>
//    ) {
//        val accountKey = "$username@$domain"
//        val accountInfo = activeAccounts[accountKey] ?: return
//        currentAccountInfo = accountInfo
//
//        if (!accountInfo.isRegistered) {
//            log.d(tag = TAG) { "Error: Not registered with SIP server" }
////            eventDispatcher.onError(
////                EddysSipLibrary.SipError(
////                    code = 1004,
////                    message = "Not registered with SIP server",
////                    category = EddysSipLibrary.ErrorCategory.SIP_PROTOCOL
////                )
////            )
//            return
//        }
//
//        try {
//            webRtcManager.setAudioEnabled(true)
//            val sdp = webRtcManager.createOffer()
//
//            val callId = generateId()
//            val callData = CallData(
//                callId = callId,
//                to = phoneNumber,
//                from = accountInfo.username,
//                direction = CallDirections.OUTGOING,
//                inviteFromTag = generateSipTag(),
//                localSdp = sdp
//            )
//
//            accountInfo.currentCallData = callData
//            StateManager.updateCallState(CallState.CALLING)
//            StateManager.updateCallerNumber(phoneNumber)
//            StateManager.updateCallId(callId)
//
//            val allHeaders = this.customHeaders + customHeaders
//            eventDispatcher.onSipMessageSent("INVITE", "OUTGOING")
//            messageHandler.sendInvite(accountInfo, callData)
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error creating call: ${e.stackTraceToString()}" }
//            eventDispatcher.onCallFailed(generateId(), "Failed to create call: ${e.message}", 1005)
//        }
//    }
//
//    override suspend fun acceptCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        if (callData.direction != CallDirections.INCOMING ||
//            (StateManager.getCurrentCallState() != CallState.INCOMING &&
//                    StateManager.getCurrentCallState() != CallState.RINGING)
//        ) {
//            return
//        }
//
//        try {
//            // Detener ringtone inmediatamente al aceptar
//            ringtoneManager.stopAllRingtones()
//
//            if (!webRtcManager.isInitialized()) {
//                webRtcManager.initialize()
//                delay(1000)
//            }
//
//            webRtcManager.prepareAudioForIncomingCall()
//            delay(1000)
//
//            val sdp = webRtcManager.createAnswer(accountInfo, callData.remoteSdp ?: "")
//            callData.localSdp = sdp
//
//            messageHandler.sendInviteOkResponse(accountInfo, callData)
//            delay(500)
//
//            webRtcManager.setAudioEnabled(true)
//            webRtcManager.setMuted(false)
//
//            StateManager.updateCallState(CallState.ACCEPTING)
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error accepting call: ${e.message}" }
//            eventDispatcher.onCallFailed(
//                callData.callId,
//                "Failed to accept call: ${e.message}",
//                1006
//            )
//            declineCall()
//        }
//    }
//
//    override suspend fun declineCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        if (callData.direction != CallDirections.INCOMING ||
//            (StateManager.getCurrentCallState() != CallState.INCOMING &&
//                    StateManager.getCurrentCallState() != CallState.RINGING)
//        ) {
//            return
//        }
//
//        // Detener ringtone inmediatamente al declinar
//        ringtoneManager.stopAllRingtones()
//
//        if (callData.toTag?.isEmpty() == true) {
//            callData.toTag = generateId()
//        }
//
//        messageHandler.sendDeclineResponse(accountInfo, callData)
//
//        val endTime = Clock.System.now().toEpochMilliseconds()
//        callHistoryManager.addCallLog(callData, CallTypes.DECLINED, endTime)
//
//        StateManager.updateCallState(CallState.DECLINED)
//    }
//
//    override suspend fun endCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        val currentState = StateManager.getCurrentCallState()
//        if (currentState == CallState.NONE || currentState == CallState.ENDED) {
//            return
//        }
//
//        // Detener ringtones inmediatamente al terminar
//        ringtoneManager.stopAllRingtones()
//
//        val endTime = Clock.System.now().toEpochMilliseconds()
//
//        when (currentState) {
//            CallState.CONNECTED, CallState.HOLDING, CallState.ACCEPTING -> {
//                messageHandler.sendBye(accountInfo, callData)
//                callHistoryManager.addCallLog(callData, CallTypes.SUCCESS, endTime)
//            }
//
//            CallState.CALLING, CallState.RINGING, CallState.OUTGOING -> {
//                messageHandler.sendCancel(accountInfo, callData)
//                callHistoryManager.addCallLog(callData, CallTypes.ABORTED, endTime)
//            }
//
//            else -> {}
//        }
//
//        StateManager.updateCallState(CallState.ENDED)
//        webRtcManager.dispose()
//        clearDtmfQueue()
//        accountInfo.resetCallState()
//        onCallTerminated?.invoke()
//    }
//
//    override suspend fun holdCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        callHoldManager.holdCall()?.let { holdSdp ->
//            callData.localSdp = holdSdp
//            callData.isOnHold = true
//            messageHandler.sendReInvite(accountInfo, callData, holdSdp)
//            StateManager.updateCallState(CallState.HOLDING)
//            eventDispatcher.onCallHold(callData.callId, true)
//        }
//    }
//
//    override suspend fun resumeCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        callHoldManager.resumeCall()?.let { resumeSdp ->
//            callData.localSdp = resumeSdp
//            callData.isOnHold = false
//            messageHandler.sendReInvite(accountInfo, callData, resumeSdp)
//            StateManager.updateCallState(CallState.CONNECTED)
//            eventDispatcher.onCallHold(callData.callId, false)
//        }
//    }
//
//    override fun sendDtmf(digit: Char, duration: Int): Boolean {
//        val validDigits = setOf(
//            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
//            '*', '#', 'A', 'B', 'C', 'D', 'a', 'b', 'c', 'd'
//        )
//
//        if (!validDigits.contains(digit)) {
//            return false
//        }
//
//        val request = DtmfRequest(digit, duration)
//        coreScope.launch {
//            dtmfMutex.withLock {
//                dtmfQueue.add(request)
//            }
//            processDtmfQueue()
//        }
//
//        return true
//    }
//
//    override fun sendDtmfSequence(digits: String, duration: Int): Boolean {
//        if (digits.isEmpty()) return false
//
//        digits.forEach { digit ->
//            sendDtmf(digit, duration)
//        }
//
//        return true
//    }
//
//    override fun getCurrentCallState(): CallState = StateManager.getCurrentCallState()
//    override fun hasActiveCall(): Boolean =
//        getCurrentCallState() != CallState.NONE && getCurrentCallState() != CallState.ENDED
//
//    override fun isCallConnected(): Boolean = getCurrentCallState() == CallState.CONNECTED
//
//    // Métodos públicos adicionales
//    fun addEventListener(listener: SipEventListener) {
//        coreScope.launch {
//            eventDispatcher.addListener(listener)
//        }
//    }
//
//    fun removeEventListener(listener: SipEventListener) {
//        coreScope.launch {
//            eventDispatcher.removeListener(listener)
//        }
//    }
//
//    fun updateRingtoneConfig(config: RingtoneConfig) {
//        ringtoneManager.setRingtoneEnabled(config.enableIncomingRingtone || config.enableOutgoingRingtone)
//
//        if (config.customIncomingRingtoneUri != null) {
//            ringtoneManager.setCustomIncomingRingtone(config.customIncomingRingtoneUri)
//        }
//
//        if (config.customOutgoingRingtoneUri != null) {
//            ringtoneManager.setCustomOutgoingRingtone(config.customOutgoingRingtoneUri)
//        }
//
//        ringtoneManager.setRingtoneVolume(config.volume)
//        ringtoneManager.setVibrationEnabled(config.enableVibration)
//    }
//
//    fun getAudioManager(): EnhancedAudioDeviceManager = audioManager
//    fun getRingtoneManager(): EnhancedRingtoneManager = ringtoneManager
//
//    // Otros métodos existentes adaptados...
//    fun userAgent(): String = currentUserAgent
//    fun getDefaultDomain(): String? = currentAccountInfo?.domain
//    fun getCurrentUsername(): String? = currentAccountInfo?.username
//
//    private fun handleWebRtcConnected() {
//        callStartTimeMillis = Clock.System.now().toEpochMilliseconds()
//        StateManager.updateCallState(CallState.CONNECTED)
//        StateManager.updateCallDuration(callStartTimeMillis)
//    }
//
//    private fun handleWebRtcClosed() {
//        StateManager.updateCallState(CallState.ENDED)
//        currentAccountInfo?.currentCallData?.let { callData ->
//            val endTime = Clock.System.now().toEpochMilliseconds()
//            val callType = determineCallType(callData, CallState.ENDED)
//            callHistoryManager.addCallLog(callData, callType, endTime)
//        }
//    }
//
//    private fun refreshAllRegistrationsWithNewUserAgent() {
//        if (getCurrentCallState() != CallState.NONE && getCurrentCallState() != CallState.ENDED) {
//            return
//        }
//
//        activeAccounts.values.forEach { accountInfo ->
//            if (accountInfo.isRegistered) {
//                accountInfo.userAgent = userAgent()
//                messageHandler.sendRegister(
//                    accountInfo,
//                    StateManager.getCurrentAppState() == EddysSipLibrary.AppState.BACKGROUND
//                )
//            }
//        }
//    }
//
//    private fun startConnectionHealthCheck() {
//        coreScope.launch {
//            while (true) {
//                delay(connectionCheckInterval)
//                checkConnectionHealth()
//            }
//        }
//    }
//
//    private suspend fun checkConnectionHealth() {
//        activeAccounts.values.forEach { accountInfo ->
//            val webSocket = accountInfo.webSocketClient
//            if (webSocket != null && accountInfo.isRegistered) {
//                if (!webSocket.isConnected()) {
//                    eventDispatcher.onNetworkStateChanged(false, "disconnected")
//                    reconnectAccount(accountInfo)
//                } else {
//                    eventDispatcher.onNetworkStateChanged(true, "connected")
//                }
//            }
//        }
//    }
//
//    private fun reconnectAccount(accountInfo: AccountInfo) {
//        if (reconnectionInProgress) return
//
//        reconnectionInProgress = true
//        coreScope.launch {
//            try {
//                accountInfo.webSocketClient?.close()
//                accountInfo.userAgent = userAgent()
//                val headers = createHeaders()
//                val newWebSocketClient = createWebSocketClient(accountInfo, headers)
//                accountInfo.webSocketClient = newWebSocketClient
//            } catch (e: Exception) {
//                log.e(tag = TAG) { "Error during reconnection: ${e.message}" }
////                eventDispatcher.onError(
////                    EddysSipLibrary.SipError(
////                        code = 1002,
////                        message = "Reconnection failed: ${e.message}",
////                        category = EddysSipLibrary.ErrorCategory.NETWORK
////                    )
////                )
//            } finally {
//                reconnectionInProgress = false
//            }
//        }
//    }
//
//    // Métodos auxiliares
//    private fun startCallStatisticsMonitoring() {
//        coreScope.launch {
//            while (getCurrentCallState() == CallState.CONNECTED) {
//                updateCallStatistics()
//                callStatistics?.let { stats ->
//                    eventDispatcher.onCallStatistics(stats)
//                }
//                delay(5000) // Update every 5 seconds
//            }
//        }
//    }
//
//    private fun stopCallStatisticsMonitoring() {
//        callStatistics = null
//    }
//
//    private fun updateCallStatistics() {
//        val networkQuality = getNetworkQuality()
//        callStatistics = CallStatistics(
//            id = StateManager.getCurrentCallId(),
//            duration = calculateCallDuration(),
//            audioCodec = "opus",
//            networkQuality = networkQuality?.score ?: 0f,
//            packetsLost = networkQuality?.packetLoss?.toInt() ?: 0,
//            jitter = networkQuality?.jitter ?: 0L,
//            rtt = networkQuality?.latency ?: 0L,
//            bitrate = 64000,
//            audioLevel = 0.5f
//        )
//    }
//
//    fun getCurrentCallStatistics(): CallStatistics? = callStatistics
//
//    private fun calculateCallDuration(): Long {
//        return if (callStartTimeMillis > 0) {
//            Clock.System.now().toEpochMilliseconds() - callStartTimeMillis
//        } else 0L
//    }
//
//    private fun determineEndReason(previousState: CallState): EddysSipLibrary.CallEndReason {
//        return when (previousState) {
//            CallState.CALLING, CallState.OUTGOING -> EddysSipLibrary.CallEndReason.CANCELLED
//            CallState.INCOMING, CallState.RINGING -> EddysSipLibrary.CallEndReason.DECLINED
//            CallState.CONNECTED -> EddysSipLibrary.CallEndReason.USER_HANGUP
//            else -> EddysSipLibrary.CallEndReason.NETWORK_ERROR
//        }
//    }
//
//    private fun determineCallType(callData: CallData, finalState: CallState): CallTypes {
//        return when (finalState) {
//            CallState.CONNECTED, CallState.ENDED -> CallTypes.SUCCESS
//            CallState.DECLINED -> CallTypes.DECLINED
//            CallState.CALLING, CallState.RINGING -> CallTypes.ABORTED
//            else -> if (callData.direction == CallDirections.INCOMING) {
//                CallTypes.MISSED
//            } else {
//                CallTypes.ABORTED
//            }
//        }
//    }
//
//    private suspend fun processDtmfQueue() = withContext(Dispatchers.IO) {
//        dtmfMutex.withLock {
//            if (isDtmfProcessing || dtmfQueue.isEmpty()) {
//                return@withLock
//            }
//            isDtmfProcessing = true
//        }
//
//        try {
//            while (true) {
//                val request: DtmfRequest? = dtmfMutex.withLock {
//                    if (dtmfQueue.isNotEmpty()) {
//                        dtmfQueue.removeAt(0)
//                    } else {
//                        null
//                    }
//                }
//
//                if (request == null) break
//
//                val success = sendSingleDtmf(request.digit, request.duration)
//                if (success) {
//                    delay(150) // Gap between digits
//                }
//            }
//        } finally {
//            dtmfMutex.withLock {
//                isDtmfProcessing = false
//            }
//        }
//    }
//
//    private suspend fun sendSingleDtmf(digit: Char, duration: Int): Boolean {
//        val currentAccount = currentAccountInfo
//        val callData = currentAccount?.currentCallData
//
//        if (currentAccount == null || callData == null || getCurrentCallState() != CallState.CONNECTED) {
//            return false
//        }
//
//        return try {
//            webRtcManager.sendDtmfTones(
//                tones = digit.toString().uppercase(),
//                duration = duration,
//                gap = 100
//            )
//        } catch (e: Exception) {
//            false
//        }
//    }
//
//    fun clearDtmfQueue() {
//        coreScope.launch {
//            dtmfMutex.withLock {
//                dtmfQueue.clear()
//                isDtmfProcessing = false
//            }
//        }
//    }
//
//    fun getNetworkQuality(): EddysSipLibrary.NetworkQuality? {
//        return networkQualityMonitor?.getCurrentQuality()
//    }
//
//    fun enterPushMode(reason: String = "Manual") {
//        if (!isPushMode) {
//            isPushMode = true
//            coreScope.launch {
//                eventDispatcher.onPushModeChanged(true, reason)
//            }
//            currentUserAgent = "${config.userAgent} (Push)"
//            refreshAllRegistrationsWithNewUserAgent()
//        }
//    }
//
//    fun exitPushMode(reason: String = "Manual") {
//        if (isPushMode) {
//            isPushMode = false
//            coreScope.launch {
//                eventDispatcher.onPushModeChanged(false, reason)
//            }
//            currentUserAgent = config.userAgent
//            refreshAllRegistrationsWithNewUserAgent()
//        }
//    }
//
//    // Otros métodos de registro, WebSocket, etc. (adaptados del código original)
//    fun register(
//        username: String,
//        password: String,
//        domain: String,
//        provider: String,
//        token: String,
//        customHeaders: Map<String, String> = emptyMap(),
//        expires: Int = config.registrationExpiresSeconds
//    ) {
//        try {
//            val accountKey = "$username@$domain"
//            val accountInfo = AccountInfo(username, password, domain)
//            activeAccounts[accountKey] = accountInfo
//
//            accountInfo.token = token
//            accountInfo.provider = provider
//            accountInfo.userAgent = userAgent()
//
//            this.customHeaders.putAll(customHeaders)
//
//            connectWebSocketAndRegister(accountInfo)
//        } catch (e: Exception) {
//            StateManager.updateRegistrationState(RegistrationState.FAILED)
//            coreScope.launch {
////                eventDispatcher.onError(
////                    EddysSipLibrary.SipError(
////                        code = 1001,
////                        message = "Registration error: ${e.message}",
////                        category = EddysSipLibrary.ErrorCategory.AUTHENTICATION
////                    )
////                )
//            }
//            throw Exception("Registration error: ${e.message}")
//        }
//    }
//
//    private fun connectWebSocketAndRegister(accountInfo: AccountInfo) {
//        try {
//            accountInfo.webSocketClient?.close()
//            val headers = createHeaders()
//            val webSocketClient = createWebSocketClient(accountInfo, headers)
//            accountInfo.webSocketClient = webSocketClient
//        } catch (e: Exception) {
//            log.d(tag = TAG) { "Error connecting WebSocket: ${e.stackTraceToString()}" }
//        }
//    }
//
//    private fun createHeaders(): HashMap<String, String> {
//        return hashMapOf(
//            "User-Agent" to userAgent(),
//            "Origin" to "https://telephony.${config.defaultDomain}",
//            "Sec-WebSocket-Protocol" to WEBSOCKET_PROTOCOL
//        ).apply {
//            putAll(customHeaders)
//        }
//    }
//
//    private fun createWebSocketClient(
//        accountInfo: AccountInfo,
//        headers: Map<String, String>
//    ): MultiplatformWebSocket {
//        val websocket = WebSocket(config.webSocketUrl, headers)
//        setupWebSocketListeners(websocket, accountInfo)
//        websocket.connect()
//        websocket.startPingTimer(config.pingIntervalMs)
//        websocket.startRegistrationRenewalTimer(REGISTRATION_CHECK_INTERVAL_MS, 60000L)
//        return websocket
//    }
//
//    private fun setupWebSocketListeners(websocket: WebSocket, accountInfo: AccountInfo) {
//        websocket.setListener(object : MultiplatformWebSocket.Listener {
//            override fun onOpen() {
//                reconnectionInProgress = false
//                lastConnectionCheck = Clock.System.now().toEpochMilliseconds()
//                coreScope.launch {
//                    eventDispatcher.onWebSocketStateChanged(true, config.webSocketUrl)
//                }
//                messageHandler.sendRegister(
//                    accountInfo,
//                    StateManager.getCurrentAppState() == EddysSipLibrary.AppState.BACKGROUND
//                )
//            }
//
//            override fun onMessage(message: String) {
//                coreScope.launch {
//                    eventDispatcher.onSipMessageReceived(message, "INCOMING")
//                }
//                messageHandler.handleSipMessage(message, accountInfo)
//            }
//
//            override fun onClose(code: Int, reason: String) {
//                accountInfo.isRegistered = false
//                StateManager.updateRegistrationState(RegistrationState.NONE)
//                coreScope.launch {
//                    eventDispatcher.onWebSocketStateChanged(false, config.webSocketUrl)
//                }
//                if (code != 1000) {
//                    handleUnexpectedDisconnection(accountInfo)
//                }
//            }
//
//            override fun onError(error: Exception) {
//                accountInfo.isRegistered = false
//                StateManager.updateRegistrationState(RegistrationState.FAILED)
//                coreScope.launch {
//                    eventDispatcher.onWebSocketStateChanged(false, config.webSocketUrl)
////                    eventDispatcher.onError(
////                        EddysSipLibrary.SipError(
////                            code = 1003,
////                            message = "WebSocket error: ${error.message}",
////                            category = EddysSipLibrary.ErrorCategory.NETWORK
////                        )
////                    )
//                }
//                handleConnectionError(accountInfo, error)
//            }
//
//            override fun onPong(timeMs: Long) {
//                lastConnectionCheck = Clock.System.now().toEpochMilliseconds()
//            }
//
//            override fun onRegistrationRenewalRequired(accountKey: String) {
//                val account = activeAccounts[accountKey]
//                if (account != null && account.webSocketClient?.isConnected() == true) {
//                    messageHandler.sendRegister(
//                        account,
//                        StateManager.getCurrentAppState() == EddysSipLibrary.AppState.BACKGROUND
//                    )
//                } else {
//                    account?.let { reconnectAccount(it) }
//                }
//            }
//        })
//    }
//
//    private fun handleUnexpectedDisconnection(accountInfo: AccountInfo) {
//        if (!reconnectionInProgress) {
//            coreScope.launch {
//                delay(2000)
//                reconnectAccount(accountInfo)
//            }
//        }
//    }
//
//    private fun handleConnectionError(accountInfo: AccountInfo, error: Exception) {
//        lastConnectionCheck = 0L
//        when {
//            error.message?.contains("timeout") == true -> {
//                reconnectAccount(accountInfo)
//            }
//
//            else -> {
//                reconnectAccount(accountInfo)
//            }
//        }
//    }
//
//    fun dispose() {
//        networkQualityMonitor?.dispose()
//        audioManager.dispose()
//        ringtoneManager.dispose()
//        webRtcManager.dispose()
//        activeAccounts.clear()
//        coreScope.cancel()
//        uiScope.cancel()
//    }
//
////    fun getMessageHandler(): SipMessageHandler = messageHandler
//}
//
//// Data class para configuración de ringtone
//data class RingtoneConfig(
//    val enableIncomingRingtone: Boolean = true,
//    val enableOutgoingRingtone: Boolean = true,
//    val enableVibration: Boolean = true,
//    val volume: Float = 1.0f,
//    val customIncomingRingtoneUri: Uri? = null,
//    val customOutgoingRingtoneUri: Uri? = null
//)

///////////55555///////
/**
 * Enhanced SIP Core Manager con interfaces y múltiples listeners
 *
 * @author Eddys Larez
 */
class SipCoreManager private constructor(
    private val application: Application,
    private val config: EddysSipLibrary.SipConfig,
    private val audioManager: EnhancedAudioDeviceManager,
    private val ringtoneManager: EnhancedRingtoneManager,
    private val windowManager: WindowManager,
    private val platformInfo: PlatformInfo,
    private val settingsDataStore: SettingsDataStore,
) : CallManager {

    private val TAG = "EnhancedSipCoreManager"

    // Core managers
    val callHistoryManager = CallHistoryManager()
    private val messageHandler: SipMessageHandler by lazy {
        SipMessageHandler(this)
    }

    // WebRTC and networking
    val webRtcManager = WebRtcManagerFactory.createWebRtcManager(application)
    private val platformRegistration = PlatformRegistration(application)
    private val callHoldManager = CallHoldManager(webRtcManager)

    // State management
    private val activeAccounts = HashMap<String, AccountInfo>()
    private var currentAccountInfo: AccountInfo? = null
    private var reconnectionInProgress = false
    private var lastConnectionCheck = 0L
    private val connectionCheckInterval = 30000L

    // DTMF management
    private val dtmfQueue = mutableListOf<DtmfRequest>()
    private var isDtmfProcessing = false
    private val dtmfMutex = Mutex()

    // Call management
    var onCallTerminated: (() -> Unit)? = null
    private var isCallFromPush = false
    private var callStartTimeMillis: Long = 0

    // Enhanced features
    private var isPushMode = false
    private var currentUserAgent = config.userAgent
    private var customHeaders = config.customHeaders.toMutableMap()
    private var networkQualityMonitor: NetworkQualityMonitor? = null
    private var callStatistics: CallStatistics? = null

    // Scopes para corrutinas
    private val coreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val WEBSOCKET_PROTOCOL = "sip"
        private const val REGISTRATION_CHECK_INTERVAL_MS = 30 * 1000L

        fun createInstance(
            application: Application,
            config: EddysSipLibrary.SipConfig
        ): SipCoreManager {
            val audioManager = EnhancedAudioDeviceManager(application)
            val ringtoneManager = EnhancedRingtoneManager(application)

            return SipCoreManager(
                application = application,
                config = config,
                audioManager = audioManager,
                ringtoneManager = ringtoneManager,
                windowManager = WindowManager(),
                platformInfo = PlatformInfo(),
                settingsDataStore = SettingsDataStore(application)
            )
        }
    }

    // Public accessors for SipMessageHandler
    fun setCurrentAccountInfo(accountInfo: AccountInfo) {
        currentAccountInfo = accountInfo
    }

    fun setCallFromPush(fromPush: Boolean) {
        isCallFromPush = fromPush
    }

    fun setCallStartTime(timeMillis: Long) {
        callStartTimeMillis = timeMillis
    }

    fun getCallStartTime(): Long = callStartTimeMillis

    fun bringToFront() {
        windowManager.bringToFront()
    }

    fun initialize() {
        log.d(tag = TAG) { "Initializing Enhanced SIP Core Manager" }

        webRtcManager.initialize()
        setupWebRtcEventListener()
        setupPlatformLifecycleObservers()
        setupEnhancedFeatures()
        startConnectionHealthCheck()
        startStateMonitoring()
    }

    private fun setupEnhancedFeatures() {
        networkQualityMonitor = NetworkQualityMonitor { quality ->
            coreScope.launch {
                GlobalEventBus.emit(SipEvent.NetworkQuality(quality))
            }
        }

        // Inicializar dispositivos de audio
        coreScope.launch {
            try {
                val inputDevices = audioManager.getAudioInputDevices()
                val outputDevices = audioManager.getAudioOutputDevices()
                StateManager.updateAvailableAudioDevices(inputDevices, outputDevices)
                GlobalEventBus.emit(SipEvent.AudioDevicesAvailable(inputDevices, outputDevices))
            } catch (e: Exception) {
                log.e(tag = TAG) { "Error initializing audio devices: ${e.message}" }
            }
        }
    }

    private fun startStateMonitoring() {
        // Monitor call state changes
        coreScope.launch {
            StateManager.callStateFlow.collect { newState ->
                log.d(tag = TAG) { "State monitor detected call state: $newState" }
                handleCallStateChangeEffects(newState)
            }
        }

        // Monitor registration state changes
        coreScope.launch {
            StateManager.registrationStateFlow.collect { state ->
                log.d(tag = TAG) { "State monitor detected registration state: $state" }
                val currentAccount = getCurrentUsername() ?: "unknown"
                GlobalEventBus.emit(SipEvent.RegistrationStateChanged(state, currentAccount))
                handleRegistrationStateChange(state, currentAccount)
            }
        }

        // Monitor caller number changes
        coreScope.launch {
            StateManager.callerNumberFlow.collect { number ->
                if (number.isNotEmpty()) {
                    log.d(tag = TAG) { "Caller number updated: $number" }
                }
            }
        }

        // Monitor call duration
        coreScope.launch {
            StateManager.callDurationFlow.collect { duration ->
                if (duration > 0) {
                    // Actualizar estadísticas de llamada cada 5 segundos
                    if (duration % 5000 == 0L) {
                        updateCallStatistics()
                    }
                }
            }
        }
    }

    private suspend fun handleCallStateChangeEffects(newState: CallState) {
        when (newState) {
            CallState.INCOMING -> {
                // Reproducir ringtone de entrada
                if (config.ringtoneConfig.enableIncomingRingtone) {
                    ringtoneManager.playIncomingRingtone()
                }
                GlobalEventBus.emit(SipEvent.IncomingCall(
                    currentAccountInfo?.currentCallData?.from ?: "",
                    null,
                    currentAccountInfo?.currentCallData?.callId ?: ""
                ))

                // Auto-accept si está configurado
                if (config.autoAcceptDelay > 0) {
                    delay(config.autoAcceptDelay)
                    if (StateManager.getCurrentCallState() == CallState.INCOMING) {
                        acceptCall()
                    }
                }
            }

            CallState.CALLING, CallState.OUTGOING -> {
                // Reproducir ringtone de salida
                if (config.ringtoneConfig.enableOutgoingRingtone) {
                    ringtoneManager.playOutgoingRingtone()
                }
            }

            CallState.CONNECTED -> {
                // Detener todos los ringtones
                ringtoneManager.stopAllRingtones()
                startCallStatisticsMonitoring()
            }

            CallState.ENDED, CallState.DECLINED -> {
                // Detener ringtones y limpiar
                ringtoneManager.stopAllRingtones()
                stopCallStatisticsMonitoring()
                clearCallData()
            }

            CallState.RINGING -> {
                // Detener ringtone de salida cuando empieza a sonar del otro lado
                ringtoneManager.stopOutgoingRingtone()
            }

            else -> {}
        }
    }

    private fun clearCallData() {
        StateManager.updateCallerNumber("")
        StateManager.updateCallId("")
        callStatistics = null
    }

    private suspend fun handleRegistrationStateChange(state: RegistrationState, account: String) {
        when (state) {
            RegistrationState.OK -> {
                GlobalEventBus.emit(SipEvent.RegistrationSuccess(account, config.registrationExpiresSeconds))
            }

            RegistrationState.FAILED -> {
                GlobalEventBus.emit(SipEvent.RegistrationFailed(account, "Registration failed"))
            }

            else -> {}
        }
    }

    private fun setupWebRtcEventListener() {
        webRtcManager.setListener(object : WebRtcEventListener {
            override fun onIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
                // Handle ICE candidate
            }

            override fun onConnectionStateChange(state: WebRtcConnectionState) {
                when (state) {
                    WebRtcConnectionState.CONNECTED -> handleWebRtcConnected()
                    WebRtcConnectionState.CLOSED -> handleWebRtcClosed()
                    else -> {}
                }
            }

            override fun onRemoteAudioTrack() {
                log.d(tag = TAG) { "Remote audio track received" }
            }

            override fun onAudioDeviceChanged(device: AudioDevice) {
                log.d(tag = TAG) { "Audio device changed: ${device.name}" }
                coreScope.launch {
                    GlobalEventBus.emit(SipEvent.AudioDeviceChanged(null, device))
                }
            }
        })
    }

    private fun setupPlatformLifecycleObservers() {
        platformRegistration.setupNotificationObservers(object : AppLifecycleListener {
            override fun onEvent(event: AppLifecycleEvent) {
                when (event) {
                    AppLifecycleEvent.EnterBackground -> {
                        StateManager.updateAppState(EddysSipLibrary.AppState.BACKGROUND)
                        if (config.autoEnterPushOnBackground) {
                            enterPushMode("App entered background")
                        }
                        refreshAllRegistrationsWithNewUserAgent()
                    }

                    AppLifecycleEvent.EnterForeground -> {
                        StateManager.updateAppState(EddysSipLibrary.AppState.FOREGROUND)
                        if (config.autoExitPushOnForeground) {
                            exitPushMode("App entered foreground")
                        }
                        refreshAllRegistrationsWithNewUserAgent()
                    }

                    else -> {}
                }
            }
        })
    }

    // Implementación de CallManager interface
    override suspend fun makeCall(
        phoneNumber: String,
        username: String,
        domain: String,
        customHeaders: Map<String, String>
    ): String {
        val accountKey = "$username@$domain"
        val accountInfo = activeAccounts[accountKey] ?: throw SipLibraryException(
            "Account not found",
            SipError(ErrorCodes.NO_REGISTERED_ACCOUNT, "Account not found", ErrorCategory.CONFIGURATION)
        )
        currentAccountInfo = accountInfo

        if (!accountInfo.isRegistered) {
            log.d(tag = TAG) { "Error: Not registered with SIP server" }
            throw SipLibraryException(
                "Not registered with SIP server",
                SipError(ErrorCodes.NO_REGISTERED_ACCOUNT, "Not registered", ErrorCategory.AUTHENTICATION)
            )
        }

        try {
            webRtcManager.setAudioEnabled(true)
            val sdp = webRtcManager.createOffer()

            val callId = generateId()
            val callData = CallData(
                callId = callId,
                to = phoneNumber,
                from = accountInfo.username,
                direction = CallDirections.OUTGOING,
                inviteFromTag = generateSipTag(),
                localSdp = sdp
            )

            accountInfo.currentCallData = callData
            StateManager.updateCallState(CallState.CALLING)
            StateManager.updateCallerNumber(phoneNumber)
            StateManager.updateCallId(callId)

            val allHeaders = this.customHeaders + customHeaders
            GlobalEventBus.emit(SipEvent.SipMessageSent("INVITE", "OUTGOING"))
            messageHandler.sendInvite(accountInfo, callData)

            return callId
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error creating call: ${e.stackTraceToString()}" }
            GlobalEventBus.emit(SipEvent.CallFailed(generateId(), "Failed to create call: ${e.message}", 1005))
            throw SipLibraryException(
                "Failed to create call",
                SipError(ErrorCodes.CALL_INITIATION_FAILED, "Call creation failed", ErrorCategory.SIP_PROTOCOL),
                e
            )
        }
    }

    override suspend fun acceptCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        if (callData.direction != CallDirections.INCOMING ||
            (StateManager.getCurrentCallState() != CallState.INCOMING &&
                    StateManager.getCurrentCallState() != CallState.RINGING)
        ) {
            return
        }

        try {
            // Detener ringtone inmediatamente al aceptar
            ringtoneManager.stopAllRingtones()

            if (!webRtcManager.isInitialized()) {
                webRtcManager.initialize()
                delay(1000)
            }

            webRtcManager.prepareAudioForIncomingCall()
            delay(1000)

            val sdp = webRtcManager.createAnswer(accountInfo, callData.remoteSdp ?: "")
            callData.localSdp = sdp

            messageHandler.sendInviteOkResponse(accountInfo, callData)
            delay(500)

            webRtcManager.setAudioEnabled(true)
            webRtcManager.setMuted(false)

            StateManager.updateCallState(CallState.ACCEPTING)
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error accepting call: ${e.message}" }
            GlobalEventBus.emit(SipEvent.CallFailed(
                callData.callId,
                "Failed to accept call: ${e.message}",
                1006
            ))
            declineCall()
        }
    }

    override suspend fun declineCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        if (callData.direction != CallDirections.INCOMING ||
            (StateManager.getCurrentCallState() != CallState.INCOMING &&
                    StateManager.getCurrentCallState() != CallState.RINGING)
        ) {
            return
        }

        // Detener ringtone inmediatamente al declinar
        ringtoneManager.stopAllRingtones()

        if (callData.toTag?.isEmpty() == true) {
            callData.toTag = generateId()
        }

        messageHandler.sendDeclineResponse(accountInfo, callData)

        val endTime = Clock.System.now().toEpochMilliseconds()
        callHistoryManager.addCallLog(callData, CallTypes.DECLINED, endTime)

        StateManager.updateCallState(CallState.DECLINED)
    }

    override suspend fun endCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        val currentState = StateManager.getCurrentCallState()
        if (currentState == CallState.NONE || currentState == CallState.ENDED) {
            return
        }

        // Detener ringtones inmediatamente al terminar
        ringtoneManager.stopAllRingtones()

        val endTime = Clock.System.now().toEpochMilliseconds()

        when (currentState) {
            CallState.CONNECTED, CallState.HOLDING, CallState.ACCEPTING -> {
                messageHandler.sendBye(accountInfo, callData)
                callHistoryManager.addCallLog(callData, CallTypes.SUCCESS, endTime)
            }

            CallState.CALLING, CallState.RINGING, CallState.OUTGOING -> {
                messageHandler.sendCancel(accountInfo, callData)
                callHistoryManager.addCallLog(callData, CallTypes.ABORTED, endTime)
            }

            else -> {}
        }

        StateManager.updateCallState(CallState.ENDED)
        webRtcManager.dispose()
        clearDtmfQueue()
        accountInfo.resetCallState()
        onCallTerminated?.invoke()
    }

    override suspend fun holdCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        callHoldManager.holdCall()?.let { holdSdp ->
            callData.localSdp = holdSdp
            callData.isOnHold = true
            messageHandler.sendReInvite(accountInfo, callData, holdSdp)
            StateManager.updateCallState(CallState.HOLDING)
            GlobalEventBus.emit(SipEvent.CallHold(callData.callId, true))
        }
    }

    override suspend fun resumeCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        callHoldManager.resumeCall()?.let { resumeSdp ->
            callData.localSdp = resumeSdp
            callData.isOnHold = false
            messageHandler.sendReInvite(accountInfo, callData, resumeSdp)
            StateManager.updateCallState(CallState.CONNECTED)
            GlobalEventBus.emit(SipEvent.CallHold(callData.callId, false))
        }
    }

    override fun sendDtmf(digit: Char, duration: Int): Boolean {
        val validDigits = setOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '*', '#', 'A', 'B', 'C', 'D', 'a', 'b', 'c', 'd'
        )

        if (!validDigits.contains(digit)) {
            return false
        }

        val request = DtmfRequest(digit, duration)
        coreScope.launch {
            dtmfMutex.withLock {
                dtmfQueue.add(request)
            }
            processDtmfQueue()
        }

        return true
    }

    override fun sendDtmfSequence(digits: String, duration: Int): Boolean {
        if (digits.isEmpty()) return false

        digits.forEach { digit ->
            sendDtmf(digit, duration)
        }

        return true
    }

    override fun getCurrentCallState(): CallState = StateManager.getCurrentCallState()
    override fun hasActiveCall(): Boolean =
        getCurrentCallState() != CallState.NONE && getCurrentCallState() != CallState.ENDED

    override fun isCallConnected(): Boolean = getCurrentCallState() == CallState.CONNECTED

    // Métodos públicos adicionales
    fun updateRingtoneConfig(config: RingtoneConfig) {
        ringtoneManager.setRingtoneEnabled(config.enableIncomingRingtone || config.enableOutgoingRingtone)

        if (config.customIncomingRingtoneUri != null) {
            ringtoneManager.setCustomIncomingRingtone(config.customIncomingRingtoneUri)
        }

        if (config.customOutgoingRingtoneUri != null) {
            ringtoneManager.setCustomOutgoingRingtone(config.customOutgoingRingtoneUri)
        }

        ringtoneManager.setRingtoneVolume(config.volume)
        ringtoneManager.setVibrationEnabled(config.enableVibration)
    }

    fun getAudioManager(): EnhancedAudioDeviceManager = audioManager
    fun getRingtoneManager(): EnhancedRingtoneManager = ringtoneManager

    // Otros métodos existentes adaptados...
    fun userAgent(): String = currentUserAgent
    fun getDefaultDomain(): String? = currentAccountInfo?.domain
    fun getCurrentUsername(): String? = currentAccountInfo?.username

    private fun handleWebRtcConnected() {
        callStartTimeMillis = Clock.System.now().toEpochMilliseconds()
        StateManager.updateCallState(CallState.CONNECTED)
        StateManager.updateCallDuration(callStartTimeMillis)
    }

    private fun handleWebRtcClosed() {
        StateManager.updateCallState(CallState.ENDED)
        currentAccountInfo?.currentCallData?.let { callData ->
            val endTime = Clock.System.now().toEpochMilliseconds()
            val callType = determineCallType(callData, CallState.ENDED)
            callHistoryManager.addCallLog(callData, callType, endTime)
        }
    }

    private fun refreshAllRegistrationsWithNewUserAgent() {
        if (getCurrentCallState() != CallState.NONE && getCurrentCallState() != CallState.ENDED) {
            return
        }

        activeAccounts.values.forEach { accountInfo ->
            if (accountInfo.isRegistered) {
                accountInfo.userAgent = userAgent()
                messageHandler.sendRegister(
                    accountInfo,
                    StateManager.getCurrentAppState() == EddysSipLibrary.AppState.BACKGROUND
                )
            }
        }
    }

    private fun startConnectionHealthCheck() {
        coreScope.launch {
            while (true) {
                delay(connectionCheckInterval)
                checkConnectionHealth()
            }
        }
    }

    private suspend fun checkConnectionHealth() {
        activeAccounts.values.forEach { accountInfo ->
            val webSocket = accountInfo.webSocketClient
            if (webSocket != null && accountInfo.isRegistered) {
                if (!webSocket.isConnected()) {
                    GlobalEventBus.emit(SipEvent.NetworkStateChanged(false, "disconnected"))
                    reconnectAccount(accountInfo)
                } else {
                    GlobalEventBus.emit(SipEvent.NetworkStateChanged(true, "connected"))
                }
            }
        }
    }

    private fun reconnectAccount(accountInfo: AccountInfo) {
        if (reconnectionInProgress) return

        reconnectionInProgress = true
        coreScope.launch {
            try {
                accountInfo.webSocketClient?.close()
                accountInfo.userAgent = userAgent()
                val headers = createHeaders()
                val newWebSocketClient = createWebSocketClient(accountInfo, headers)
                accountInfo.webSocketClient = newWebSocketClient
            } catch (e: Exception) {
                log.e(tag = TAG) { "Error during reconnection: ${e.message}" }
                GlobalEventBus.emit(SipEvent.Error(SipError(
                    code = 1002,
                    message = "Reconnection failed: ${e.message}",
                    category = ErrorCategory.NETWORK
                )))
            } finally {
                reconnectionInProgress = false
            }
        }
    }

    // Métodos auxiliares
    private fun startCallStatisticsMonitoring() {
        coreScope.launch {
            while (getCurrentCallState() == CallState.CONNECTED) {
                updateCallStatistics()
                callStatistics?.let { stats ->
                    GlobalEventBus.emit(SipEvent.CallStatistics(stats))
                }
                delay(5000) // Update every 5 seconds
            }
        }
    }

    private fun stopCallStatisticsMonitoring() {
        callStatistics = null
    }

    private fun updateCallStatistics() {
        val networkQuality = getNetworkQuality()
        callStatistics = CallStatistics(
            id = StateManager.getCurrentCallId(),
            duration = calculateCallDuration(),
            audioCodec = "opus",
            networkQuality = networkQuality?.score ?: 0f,
            packetsLost = networkQuality?.packetLoss?.toInt() ?: 0,
            jitter = networkQuality?.jitter ?: 0L,
            rtt = networkQuality?.latency ?: 0L,
            bitrate = 64000,
            audioLevel = 0.5f
        )
    }

    fun getCurrentCallStatistics(): CallStatistics? = callStatistics

    private fun calculateCallDuration(): Long {
        return if (callStartTimeMillis > 0) {
            Clock.System.now().toEpochMilliseconds() - callStartTimeMillis
        } else 0L
    }

    private fun determineEndReason(previousState: CallState): EddysSipLibrary.CallEndReason {
        return when (previousState) {
            CallState.CALLING, CallState.OUTGOING -> EddysSipLibrary.CallEndReason.CANCELLED
            CallState.INCOMING, CallState.RINGING -> EddysSipLibrary.CallEndReason.DECLINED
            CallState.CONNECTED -> EddysSipLibrary.CallEndReason.USER_HANGUP
            else -> EddysSipLibrary.CallEndReason.NETWORK_ERROR
        }
    }

    private fun determineCallType(callData: CallData, finalState: CallState): CallTypes {
        return when (finalState) {
            CallState.CONNECTED, CallState.ENDED -> CallTypes.SUCCESS
            CallState.DECLINED -> CallTypes.DECLINED
            CallState.CALLING, CallState.RINGING -> CallTypes.ABORTED
            else -> if (callData.direction == CallDirections.INCOMING) {
                CallTypes.MISSED
            } else {
                CallTypes.ABORTED
            }
        }
    }

    private suspend fun processDtmfQueue() = withContext(Dispatchers.IO) {
        dtmfMutex.withLock {
            if (isDtmfProcessing || dtmfQueue.isEmpty()) {
                return@withLock
            }
            isDtmfProcessing = true
        }

        try {
            while (true) {
                val request: DtmfRequest? = dtmfMutex.withLock {
                    if (dtmfQueue.isNotEmpty()) {
                        dtmfQueue.removeAt(0)
                    } else {
                        null
                    }
                }

                if (request == null) break

                val success = sendSingleDtmf(request.digit, request.duration)
                if (success) {
                    delay(150) // Gap between digits
                }
            }
        } finally {
            dtmfMutex.withLock {
                isDtmfProcessing = false
            }
        }
    }

    private suspend fun sendSingleDtmf(digit: Char, duration: Int): Boolean {
        val currentAccount = currentAccountInfo
        val callData = currentAccount?.currentCallData

        if (currentAccount == null || callData == null || getCurrentCallState() != CallState.CONNECTED) {
            return false
        }

        return try {
            webRtcManager.sendDtmfTones(
                tones = digit.toString().uppercase(),
                duration = duration,
                gap = 100
            )
        } catch (e: Exception) {
            false
        }
    }

    fun clearDtmfQueue() {
        coreScope.launch {
            dtmfMutex.withLock {
                dtmfQueue.clear()
                isDtmfProcessing = false
            }
        }
    }

    fun getNetworkQuality(): EddysSipLibrary.NetworkQuality? {
        return networkQualityMonitor?.getCurrentQuality()
    }

    fun enterPushMode(reason: String = "Manual") {
        if (!isPushMode) {
            isPushMode = true
            coreScope.launch {
                GlobalEventBus.emit(SipEvent.PushModeChanged(true, reason))
            }
            currentUserAgent = "${config.userAgent} (Push)"
            refreshAllRegistrationsWithNewUserAgent()
        }
    }

    fun exitPushMode(reason: String = "Manual") {
        if (isPushMode) {
            isPushMode = false
            coreScope.launch {
                GlobalEventBus.emit(SipEvent.PushModeChanged(false, reason))
            }
            currentUserAgent = config.userAgent
            refreshAllRegistrationsWithNewUserAgent()
        }
    }

    // Otros métodos de registro, WebSocket, etc. (adaptados del código original)
    fun register(
        username: String,
        password: String,
        domain: String,
        provider: String,
        token: String,
        customHeaders: Map<String, String> = emptyMap(),
        expires: Int = config.registrationExpiresSeconds
    ) {
        try {
            val accountKey = "$username@$domain"
            val accountInfo = AccountInfo(username, password, domain)
            activeAccounts[accountKey] = accountInfo

            accountInfo.token = token
            accountInfo.provider = provider
            accountInfo.userAgent = userAgent()

            this.customHeaders.putAll(customHeaders)

            connectWebSocketAndRegister(accountInfo)
        } catch (e: Exception) {
            StateManager.updateRegistrationState(RegistrationState.FAILED)
            coreScope.launch {
                GlobalEventBus.emit(SipEvent.Error(SipError(
                    code = 1001,
                    message = "Registration error: ${e.message}",
                    category = ErrorCategory.AUTHENTICATION
                )))
            }
            throw Exception("Registration error: ${e.message}")
        }
    }

    private fun connectWebSocketAndRegister(accountInfo: AccountInfo) {
        try {
            accountInfo.webSocketClient?.close()
            val headers = createHeaders()
            val webSocketClient = createWebSocketClient(accountInfo, headers)
            accountInfo.webSocketClient = webSocketClient
        } catch (e: Exception) {
            log.d(tag = TAG) { "Error connecting WebSocket: ${e.stackTraceToString()}" }
        }
    }

    private fun createHeaders(): HashMap<String, String> {
        return hashMapOf(
            "User-Agent" to userAgent(),
            "Origin" to "https://telephony.${config.defaultDomain}",
            "Sec-WebSocket-Protocol" to WEBSOCKET_PROTOCOL
        ).apply {
            putAll(customHeaders)
        }
    }

    private fun createWebSocketClient(
        accountInfo: AccountInfo,
        headers: Map<String, String>
    ): MultiplatformWebSocket {
        val websocket = WebSocket(config.webSocketUrl, headers)
        setupWebSocketListeners(websocket, accountInfo)
        websocket.connect()
        websocket.startPingTimer(config.pingIntervalMs)
        websocket.startRegistrationRenewalTimer(REGISTRATION_CHECK_INTERVAL_MS, 60000L)
        return websocket
    }

    private fun setupWebSocketListeners(websocket: WebSocket, accountInfo: AccountInfo) {
        websocket.setListener(object : MultiplatformWebSocket.Listener {
            override fun onOpen() {
                reconnectionInProgress = false
                lastConnectionCheck = Clock.System.now().toEpochMilliseconds()
                coreScope.launch {
                    GlobalEventBus.emit(SipEvent.WebSocketStateChanged(true, config.webSocketUrl))
                }
                messageHandler.sendRegister(
                    accountInfo,
                    StateManager.getCurrentAppState() == EddysSipLibrary.AppState.BACKGROUND
                )
            }

            override fun onMessage(message: String) {
                coreScope.launch {
                    GlobalEventBus.emit(SipEvent.SipMessageReceived(message, "INCOMING"))
                }
                messageHandler.handleSipMessage(message, accountInfo)
            }

            override fun onClose(code: Int, reason: String) {
                accountInfo.isRegistered = false
                StateManager.updateRegistrationState(RegistrationState.NONE)
                coreScope.launch {
                    GlobalEventBus.emit(SipEvent.WebSocketStateChanged(false, config.webSocketUrl))
                }
                if (code != 1000) {
                    handleUnexpectedDisconnection(accountInfo)
                }
            }

            override fun onError(error: Exception) {
                accountInfo.isRegistered = false
                StateManager.updateRegistrationState(RegistrationState.FAILED)
                coreScope.launch {
                    GlobalEventBus.emit(SipEvent.WebSocketStateChanged(false, config.webSocketUrl))
                    GlobalEventBus.emit(SipEvent.Error(SipError(
                        code = 1003,
                        message = "WebSocket error: ${error.message}",
                        category = ErrorCategory.NETWORK
                    )))
                }
                handleConnectionError(accountInfo, error)
            }

            override fun onPong(timeMs: Long) {
                lastConnectionCheck = Clock.System.now().toEpochMilliseconds()
            }

            override fun onRegistrationRenewalRequired(accountKey: String) {
                val account = activeAccounts[accountKey]
                if (account != null && account.webSocketClient?.isConnected() == true) {
                    messageHandler.sendRegister(
                        account,
                        StateManager.getCurrentAppState() == EddysSipLibrary.AppState.BACKGROUND
                    )
                } else {
                    account?.let { reconnectAccount(it) }
                }
            }
        })
    }

    private fun handleUnexpectedDisconnection(accountInfo: AccountInfo) {
        if (!reconnectionInProgress) {
            coreScope.launch {
                delay(2000)
                reconnectAccount(accountInfo)
            }
        }
    }

    private fun handleConnectionError(accountInfo: AccountInfo, error: Exception) {
        lastConnectionCheck = 0L
        when {
            error.message?.contains("timeout") == true -> {
                reconnectAccount(accountInfo)
            }

            else -> {
                reconnectAccount(accountInfo)
            }
        }
    }

    fun dispose() {
        networkQualityMonitor?.dispose()
        audioManager.dispose()
        ringtoneManager.dispose()
        webRtcManager.dispose()
        activeAccounts.clear()
        coreScope.cancel()
        uiScope.cancel()
    }
}

// Data class para configuración de ringtone
data class RingtoneConfig(
    val enableIncomingRingtone: Boolean = true,
    val enableOutgoingRingtone: Boolean = true,
    val enableVibration: Boolean = true,
    val volume: Float = 1.0f,
    val customIncomingRingtoneUri: Uri? = null,
    val customOutgoingRingtoneUri: Uri? = null
)


///////555555///////
//    private val application: Application,
//    private val config: EddysSipLibrary.SipConfig,
//    val playRingtoneUseCase: PlayRingtoneUseCase,
//    val windowManager: WindowManager,
//    val platformInfo: PlatformInfo,
//    val settingsDataStore: SettingsDataStore,
//) {
//    private var eventListener: EddysSipLibrary.SipEventListener? = null
//
//    val callHistoryManager = CallHistoryManager()
//    private var registrationState = RegistrationState.NONE
//    private val activeAccounts = HashMap<String, AccountInfo>()
//    var callState = CallState.NONE
//    var callStartTimeMillis: Long = 0
//    var currentAccountInfo: AccountInfo? = null
//    var isAppInBackground = false
//    private var reconnectionInProgress = false
//    private var lastConnectionCheck = 0L
//    private val connectionCheckInterval = 30000L
//    private val dtmfQueue = mutableListOf<DtmfRequest>()
//    private var isDtmfProcessing = false
//    private val dtmfMutex = Mutex()
//    var onCallTerminated: (() -> Unit)? = null
//    var isCallFromPush = false
//
//    // Enhanced features
//    private var isPushMode = false
//    private var currentUserAgent = config.userAgent
//    private var customHeaders = config.customHeaders.toMutableMap()
//    private var networkQualityMonitor: NetworkQualityMonitor? = null
//    private var audioDeviceManager: AudioDeviceManager? = null
//    private var callStatistics: CallStatistics? = null
//
//    // WebRTC manager and other managers
//    val webRtcManager = WebRtcManagerFactory.createWebRtcManager(application)
//    private val platformRegistration = PlatformRegistration(application)
//    private val callHoldManager = CallHoldManager(webRtcManager)
//    private val messageHandler = SipMessageHandler(this).apply {
//        onCallTerminated = ::handleCallTermination
//    }
//
//    companion object {
//        private const val TAG = "SipCoreManager"
//        private const val WEBSOCKET_PROTOCOL = "sip"
//        private const val REGISTRATION_CHECK_INTERVAL_MS = 30 * 1000L
//
//        fun createInstance(
//            application: Application,
//            config: EddysSipLibrary.SipConfig,
//            eventListener: EddysSipLibrary.SipEventListener? = null
//        ): SipCoreManager {
//            return SipCoreManager(
//                application = application,
//                config = config,
//                playRingtoneUseCase = PlayRingtoneUseCase(application),
//                windowManager = WindowManager(),
//                platformInfo = PlatformInfo(),
//                settingsDataStore = SettingsDataStore(application)
//            ).apply {
//                this.eventListener = eventListener
//            }
//        }
//    }
//
//    fun userAgent(): String = currentUserAgent
//
//    fun getDefaultDomain(): String? = currentAccountInfo?.domain
//
//    fun getCurrentUsername(): String? = currentAccountInfo?.username
//
//    fun setEventListener(listener: EddysSipLibrary.SipEventListener) {
//        this.eventListener = listener
//    }
//
//    fun initialize() {
//        log.d(tag = TAG) { "Initializing Enhanced SIP Core" }
//
//        webRtcManager.initialize()
//        setupWebRtcEventListener()
//        setupPlatformLifecycleObservers()
//        setupEnhancedFeatures()
//        startConnectionHealthCheck()
//    }
//
//    private fun setupEnhancedFeatures() {
//        networkQualityMonitor = NetworkQualityMonitor { quality ->
//            eventListener?.onNetworkQuality(quality)
//        }
//
//        audioDeviceManager = AudioDeviceManager(webRtcManager) { oldDevice, newDevice ->
//            eventListener?.onAudioDeviceChanged(oldDevice, newDevice)
//        }
//
//        setupCallStateMonitoring()
//        setupRegistrationStateMonitoring()
//    }
//
//    private fun setupCallStateMonitoring() {
//        CoroutineScope(Dispatchers.IO).launch {
//            var previousState = CallState.NONE
//
//            CallStateManager.callStateFlow.collect { newState ->
//                if (previousState != newState) {
//                    val callId = CallStateManager.getCurrentCallId()
//                    eventListener?.onCallStateChanged(previousState, newState, callId)
//
//                    when (newState) {
//                        CallState.CONNECTED -> {
//                            eventListener?.onCallConnected(callId, calculateCallDuration())
//                            startCallStatisticsMonitoring()
//                        }
//                        CallState.ENDED -> {
//                            val duration = calculateCallDuration()
//                            eventListener?.onCallDisconnected(
//                                callId = callId,
//                                reason = determineEndReason(previousState),
//                                duration = duration
//                            )
//                            stopCallStatisticsMonitoring()
//                        }
//                        CallState.INCOMING -> {
//                            val callerNumber = CallStateManager.getCurrentCallerNumber()
//                            eventListener?.onIncomingCall(callerNumber, null, callId)
//                        }
//                        else -> {}
//                    }
//                    previousState = newState
//                }
//            }
//        }
//    }
//
//    private fun setupRegistrationStateMonitoring() {
//        CoroutineScope(Dispatchers.IO).launch {
//            RegistrationStateManager.registrationStateFlow.collect { state ->
//                val currentAccount = getCurrentUsername() ?: "unknown"
//                eventListener?.onRegistrationStateChanged(state, currentAccount)
//
//                when (state) {
//                    RegistrationState.OK -> {
//                        eventListener?.onRegistrationSuccess(currentAccount, config.registrationExpiresSeconds)
//                    }
//                    RegistrationState.FAILED -> {
//                        eventListener?.onRegistrationFailed(currentAccount, "Registration failed")
//                    }
//                    else -> {}
//                }
//            }
//        }
//    }
//
//    private fun startCallStatisticsMonitoring() {
//        CoroutineScope(Dispatchers.IO).launch {
//            while (callState == CallState.CONNECTED) {
//                updateCallStatistics()
//                eventListener?.onCallStatistics(callStatistics!!)
//                delay(5000) // Update every 5 seconds
//            }
//        }
//    }
//
//    private fun stopCallStatisticsMonitoring() {
//        callStatistics = null
//    }
//
//    private fun updateCallStatistics() {
//        val networkQuality = getNetworkQuality()
//        callStatistics = CallStatistics(
//            callId = CallStateManager.getCurrentCallId(),
//            duration = calculateCallDuration(),
//            audioCodec = "opus", // Get from WebRTC
//            networkQuality = networkQuality?.score ?: 0f,
//            packetsLost = networkQuality?.packetLoss?.toInt() ?: 0,
//            jitter = networkQuality?.jitter ?: 0L,
//            rtt = networkQuality?.latency ?: 0L,
//            bitrate = 64000, // Default bitrate
//            audioLevel = 0.5f // Default audio level
//        )
//    }
//
//    private fun setupWebRtcEventListener() {
//        webRtcManager.setListener(object : WebRtcEventListener {
//            override fun onIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
//                // Implementar envío de ICE candidate
//            }
//
//            override fun onConnectionStateChange(state: WebRtcConnectionState) {
//                when (state) {
//                    WebRtcConnectionState.CONNECTED -> handleWebRtcConnected()
//                    WebRtcConnectionState.CLOSED -> handleWebRtcClosed()
//                    else -> {}
//                }
//            }
//
//            override fun onRemoteAudioTrack() {
//                log.d(tag = TAG) { "Remote audio track received" }
//            }
//
//            override fun onAudioDeviceChanged(device: AudioDevice) {
//                log.d(tag = TAG) { "Audio device changed: ${device.name}" }
//                eventListener?.onAudioDeviceChanged(null, device)
//            }
//        })
//    }
//
//    private fun setupPlatformLifecycleObservers() {
//        platformRegistration.setupNotificationObservers(object : AppLifecycleListener {
//            override fun onEvent(event: AppLifecycleEvent) {
//                when (event) {
//                    AppLifecycleEvent.EnterBackground -> {
//                        isAppInBackground = true
//                        eventListener?.onAppStateChanged(
//                            EddysSipLibrary.AppState.BACKGROUND,
//                            EddysSipLibrary.AppState.FOREGROUND
//                        )
//                        if (config.autoEnterPushOnBackground) {
//                            enterPushMode("App entered background")
//                        }
//                        refreshAllRegistrationsWithNewUserAgent()
//                    }
//                    AppLifecycleEvent.EnterForeground -> {
//                        isAppInBackground = false
//                        eventListener?.onAppStateChanged(
//                            EddysSipLibrary.AppState.FOREGROUND,
//                            EddysSipLibrary.AppState.BACKGROUND
//                        )
//                        if (config.autoExitPushOnForeground) {
//                            exitPushMode("App entered foreground")
//                        }
//                        refreshAllRegistrationsWithNewUserAgent()
//                    }
//                    else -> {}
//                }
//            }
//        })
//    }
//
//    private fun handleWebRtcConnected() {
//        callStartTimeMillis = Clock.System.now().toEpochMilliseconds()
//        CallStateManager.updateCallState(CallState.CONNECTED)
//        callState = CallState.CONNECTED
//    }
//
//    private fun handleWebRtcClosed() {
//        callState = CallState.ENDED
//        currentAccountInfo?.currentCallData?.let { callData ->
//            val endTime = Clock.System.now().toEpochMilliseconds()
//            val callType = determineCallType(callData, callState)
//            callHistoryManager.addCallLog(callData, callType, endTime)
//        }
//    }
//
//    private fun handleCallTermination() {
//        onCallTerminated?.invoke()
//    }
//
//    private fun refreshAllRegistrationsWithNewUserAgent() {
//        if (callState != CallState.NONE && callState != CallState.ENDED) {
//            return
//        }
//
//        activeAccounts.values.forEach { accountInfo ->
//            if (accountInfo.isRegistered) {
//                accountInfo.userAgent = userAgent()
//                messageHandler.sendRegister(accountInfo, isAppInBackground)
//            }
//        }
//    }
//
//    private fun startConnectionHealthCheck() {
//        CoroutineScope(Dispatchers.IO).launch {
//            while (true) {
//                delay(connectionCheckInterval)
//                checkConnectionHealth()
//            }
//        }
//    }
//
//    private fun checkConnectionHealth() {
//        activeAccounts.values.forEach { accountInfo ->
//            val webSocket = accountInfo.webSocketClient
//            if (webSocket != null && accountInfo.isRegistered) {
//                if (!webSocket.isConnected()) {
//                    eventListener?.onNetworkStateChanged(false, "disconnected")
//                    reconnectAccount(accountInfo)
//                } else {
//                    eventListener?.onNetworkStateChanged(true, "connected")
//                }
//            }
//        }
//    }
//
//    private fun reconnectAccount(accountInfo: AccountInfo) {
//        if (reconnectionInProgress) return
//
//        reconnectionInProgress = true
//        try {
//            accountInfo.webSocketClient?.close()
//            accountInfo.userAgent = userAgent()
//            val headers = createHeaders()
//            val newWebSocketClient = createWebSocketClient(accountInfo, headers)
//            accountInfo.webSocketClient = newWebSocketClient
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error during reconnection: ${e.message}" }
//            eventListener?.onError(EddysSipLibrary.SipError(
//                code = 1002,
//                message = "Reconnection failed: ${e.message}",
//                category = EddysSipLibrary.ErrorCategory.NETWORK
//            ))
//        } finally {
//            reconnectionInProgress = false
//        }
//    }
//
//    fun updateRegistrationState(newState: RegistrationState) {
//        registrationState = newState
//        RegistrationStateManager.updateCallState(newState)
//    }
//
//    // Enhanced registration with extended parameters
//    fun register(
//        username: String,
//        password: String,
//        domain: String,
//        provider: String,
//        token: String,
//        customHeaders: Map<String, String> = emptyMap(),
//        expires: Int = config.registrationExpiresSeconds
//    ) {
//        try {
//            val accountKey = "$username@$domain"
//            val accountInfo = AccountInfo(username, password, domain)
//            activeAccounts[accountKey] = accountInfo
//
//            accountInfo.token = token
//            accountInfo.provider = provider
//            accountInfo.userAgent = userAgent()
//
//            // Merge custom headers
//            this.customHeaders.putAll(customHeaders)
//
//            connectWebSocketAndRegister(accountInfo)
//        } catch (e: Exception) {
//            updateRegistrationState(RegistrationState.FAILED)
//            eventListener?.onError(EddysSipLibrary.SipError(
//                code = 1001,
//                message = "Registration error: ${e.message}",
//                category = EddysSipLibrary.ErrorCategory.AUTHENTICATION
//            ))
//            throw Exception("Registration error: ${e.message}")
//        }
//    }
//
//    fun unregister(username: String, domain: String) {
//        val accountKey = "$username@$domain"
//        val accountInfo = activeAccounts[accountKey] ?: return
//
//        try {
//            messageHandler.sendUnregister(accountInfo)
//            accountInfo.webSocketClient?.close()
//            activeAccounts.remove(accountKey)
//        } catch (e: Exception) {
//            log.d(tag = TAG) { "Error unregistering account: ${e.message}" }
//        }
//    }
//
//    private fun connectWebSocketAndRegister(accountInfo: AccountInfo) {
//        try {
//            accountInfo.webSocketClient?.close()
//            val headers = createHeaders()
//            val webSocketClient = createWebSocketClient(accountInfo, headers)
//            accountInfo.webSocketClient = webSocketClient
//        } catch (e: Exception) {
//            log.d(tag = TAG) { "Error connecting WebSocket: ${e.stackTraceToString()}" }
//        }
//    }
//
//    private fun createHeaders(): HashMap<String, String> {
//        return hashMapOf(
//            "User-Agent" to userAgent(),
//            "Origin" to "https://telephony.${config.defaultDomain}",
//            "Sec-WebSocket-Protocol" to WEBSOCKET_PROTOCOL
//        ).apply {
//            putAll(customHeaders)
//        }
//    }
//
//    private fun createWebSocketClient(
//        accountInfo: AccountInfo,
//        headers: Map<String, String>
//    ): MultiplatformWebSocket {
//        val websocket = WebSocket(config.webSocketUrl, headers)
//        setupWebSocketListeners(websocket, accountInfo)
//        websocket.connect()
//        websocket.startPingTimer(config.pingIntervalMs)
//        websocket.startRegistrationRenewalTimer(REGISTRATION_CHECK_INTERVAL_MS, 60000L)
//        return websocket
//    }
//
//    private fun setupWebSocketListeners(websocket: WebSocket, accountInfo: AccountInfo) {
//        websocket.setListener(object : MultiplatformWebSocket.Listener {
//            override fun onOpen() {
//                reconnectionInProgress = false
//                lastConnectionCheck = Clock.System.now().toEpochMilliseconds()
//                eventListener?.onWebSocketStateChanged(true, config.webSocketUrl)
//                messageHandler.sendRegister(accountInfo, isAppInBackground)
//            }
//
//            override fun onMessage(message: String) {
//                eventListener?.onSipMessageReceived(message, "INCOMING")
//                messageHandler.handleSipMessage(message, accountInfo)
//            }
//
//            override fun onClose(code: Int, reason: String) {
//                accountInfo.isRegistered = false
//                updateRegistrationState(RegistrationState.NONE)
//                eventListener?.onWebSocketStateChanged(false, config.webSocketUrl)
//                if (code != 1000) {
//                    handleUnexpectedDisconnection(accountInfo)
//                }
//            }
//
//            override fun onError(error: Exception) {
//                accountInfo.isRegistered = false
//                updateRegistrationState(RegistrationState.FAILED)
//                eventListener?.onWebSocketStateChanged(false, config.webSocketUrl)
//                eventListener?.onError(EddysSipLibrary.SipError(
//                    code = 1003,
//                    message = "WebSocket error: ${error.message}",
//                    category = EddysSipLibrary.ErrorCategory.NETWORK
//                ))
//                handleConnectionError(accountInfo, error)
//            }
//
//            override fun onPong(timeMs: Long) {
//                lastConnectionCheck = Clock.System.now().toEpochMilliseconds()
//            }
//
//            override fun onRegistrationRenewalRequired(accountKey: String) {
//                val account = activeAccounts[accountKey]
//                if (account != null && account.webSocketClient?.isConnected() == true) {
//                    messageHandler.sendRegister(account, isAppInBackground)
//                } else {
//                    account?.let { reconnectAccount(it) }
//                }
//            }
//        })
//    }
//
//    private fun handleUnexpectedDisconnection(accountInfo: AccountInfo) {
//        if (!reconnectionInProgress) {
//            CoroutineScope(Dispatchers.IO).launch {
//                delay(2000)
//                reconnectAccount(accountInfo)
//            }
//        }
//    }
//
//    private fun handleConnectionError(accountInfo: AccountInfo, error: Exception) {
//        lastConnectionCheck = 0L
//        when {
//            error.message?.contains("timeout") == true -> {
//                forceReconnectAccount(accountInfo)
//            }
//            else -> {
//                reconnectAccount(accountInfo)
//            }
//        }
//    }
//
//    private fun forceReconnectAccount(accountInfo: AccountInfo) {
//        reconnectAccount(accountInfo)
//    }
//
//    fun makeCall(
//        phoneNumber: String,
//        sipName: String,
//        domain: String,
//        customHeaders: Map<String, String> = emptyMap()
//    ) {
//        val accountKey = "$sipName@$domain"
//        val accountInfo = activeAccounts[accountKey] ?: return
//        currentAccountInfo = accountInfo
//
//        if (!accountInfo.isRegistered) {
//            log.d(tag = TAG) { "Error: Not registered with SIP server" }
//            eventListener?.onError(EddysSipLibrary.SipError(
//                code = 1004,
//                message = "Not registered with SIP server",
//                category = EddysSipLibrary.ErrorCategory.SIP_PROTOCOL
//            ))
//            return
//        }
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                webRtcManager.setAudioEnabled(true)
//                val sdp = webRtcManager.createOffer()
//
//                val callId = generateId()
//                val callData = CallData(
//                    callId = callId,
//                    to = phoneNumber,
//                    from = accountInfo.username,
//                    direction = CallDirections.OUTGOING,
//                    inviteFromTag = generateSipTag(),
//                    localSdp = sdp
//                )
//
//                accountInfo.currentCallData = callData
//                CallStateManager.updateCallState(CallState.CALLING)
//                callState = CallState.CALLING
//                CallStateManager.callerNumber(phoneNumber)
//
//                // Merge custom headers
//                val allHeaders = this@SipCoreManager.customHeaders + customHeaders
//                eventListener?.onSipMessageSent("INVITE", "OUTGOING")
//                messageHandler.sendInvite(accountInfo, callData)
//            } catch (e: Exception) {
//                log.e(tag = TAG) { "Error creating call: ${e.stackTraceToString()}" }
//                eventListener?.onCallFailed(generateId(), "Failed to create call: ${e.message}", 1005)
//            }
//        }
//    }
//
//    fun endCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        if (callState == CallState.NONE || callState == CallState.ENDED) {
//            return
//        }
//
//        val endTime = Clock.System.now().toEpochMilliseconds()
//
//        when (callState) {
//            CallState.CONNECTED, CallState.HOLDING, CallState.ACCEPTING -> {
//                messageHandler.sendBye(accountInfo, callData)
//                callHistoryManager.addCallLog(callData, CallTypes.SUCCESS, endTime)
//            }
//            CallState.CALLING, CallState.RINGING, CallState.OUTGOING -> {
//                messageHandler.sendCancel(accountInfo, callData)
//                callHistoryManager.addCallLog(callData, CallTypes.ABORTED, endTime)
//            }
//            else -> {}
//        }
//
//        CallStateManager.updateCallState(CallState.ENDED)
//        callState = CallState.ENDED
//        webRtcManager.dispose()
//        clearDtmfQueue()
//        accountInfo.resetCallState()
//        onCallTerminated?.invoke()
//    }
//
//    fun acceptCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        if (callData.direction != CallDirections.INCOMING ||
//            (callState != CallState.INCOMING && callState != CallState.RINGING)) {
//            return
//        }
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                if (!webRtcManager.isInitialized()) {
//                    webRtcManager.initialize()
//                    delay(1000)
//                }
//
//                webRtcManager.prepareAudioForIncomingCall()
//                delay(1000)
//
//                val sdp = webRtcManager.createAnswer(accountInfo, callData.remoteSdp ?: "")
//                callData.localSdp = sdp
//
//                messageHandler.sendInviteOkResponse(accountInfo, callData)
//                delay(500)
//
//                webRtcManager.setAudioEnabled(true)
//                webRtcManager.setMuted(false)
//
//                callState = CallState.ACCEPTING
//            } catch (e: Exception) {
//                log.e(tag = TAG) { "Error accepting call: ${e.message}" }
//                eventListener?.onCallFailed(callData.callId, "Failed to accept call: ${e.message}", 1006)
//                rejectCall()
//            }
//        }
//    }
//
//    fun declineCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        if (callData.direction != CallDirections.INCOMING ||
//            (callState != CallState.INCOMING && callState != CallState.RINGING)) {
//            return
//        }
//
//        if (callData.toTag?.isEmpty() == true) {
//            callData.toTag = generateId()
//        }
//
//        messageHandler.sendDeclineResponse(accountInfo, callData)
//
//        val endTime = Clock.System.now().toEpochMilliseconds()
//        callHistoryManager.addCallLog(callData, CallTypes.DECLINED, endTime)
//
//        CallStateManager.updateCallState(CallState.DECLINED)
//        callState = CallState.DECLINED
//    }
//
//    fun rejectCall() = declineCall()
//
//    fun mute() {
//        webRtcManager.setMuted(!webRtcManager.isMuted())
//        eventListener?.onMuteStateChanged(webRtcManager.isMuted())
//    }
//
//    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
//        val validDigits = setOf(
//            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
//            '*', '#', 'A', 'B', 'C', 'D', 'a', 'b', 'c', 'd'
//        )
//
//        if (!validDigits.contains(digit)) {
//            return false
//        }
//
//        val request = DtmfRequest(digit, duration)
//        CoroutineScope(Dispatchers.IO).launch {
//            dtmfMutex.withLock {
//                dtmfQueue.add(request)
//            }
//            processDtmfQueue()
//        }
//
//        return true
//    }
//
//    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
//        if (digits.isEmpty()) return false
//
//        digits.forEach { digit ->
//            sendDtmf(digit, duration)
//        }
//
//        return true
//    }
//
//    private suspend fun processDtmfQueue() = withContext(Dispatchers.IO) {
//        dtmfMutex.withLock {
//            if (isDtmfProcessing || dtmfQueue.isEmpty()) {
//                return@withLock
//            }
//            isDtmfProcessing = true
//        }
//
//        try {
//            while (true) {
//                val request: DtmfRequest? = dtmfMutex.withLock {
//                    if (dtmfQueue.isNotEmpty()) {
//                        dtmfQueue.removeAt(0)
//                    } else {
//                        null
//                    }
//                }
//
//                if (request == null) break
//
//                val success = sendSingleDtmf(request.digit, request.duration)
//                if (success) {
//                    delay(150) // Gap between digits
//                }
//            }
//        } finally {
//            dtmfMutex.withLock {
//                isDtmfProcessing = false
//            }
//        }
//    }
//
//    private suspend fun sendSingleDtmf(digit: Char, duration: Int): Boolean {
//        val currentAccount = currentAccountInfo
//        val callData = currentAccount?.currentCallData
//
//        if (currentAccount == null || callData == null || callState != CallState.CONNECTED) {
//            return false
//        }
//
//        return try {
//            // Usar WebRTC para DTMF en Android
//            webRtcManager.sendDtmfTones(
//                tones = digit.toString().uppercase(),
//                duration = duration,
//                gap = 100
//            )
//        } catch (e: Exception) {
//            false
//        }
//    }
//
//    fun clearDtmfQueue() {
//        CoroutineScope(Dispatchers.IO).launch {
//            dtmfMutex.withLock {
//                dtmfQueue.clear()
//                isDtmfProcessing = false
//            }
//        }
//    }
//
//    fun holdCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        CoroutineScope(Dispatchers.IO).launch {
//            callHoldManager.holdCall()?.let { holdSdp ->
//                callData.localSdp = holdSdp
//                callData.isOnHold = true
//                messageHandler.sendReInvite(accountInfo, callData, holdSdp)
//                callState = CallState.HOLDING
//                eventListener?.onCallHold(callData.callId, true)
//            }
//        }
//    }
//
//    fun resumeCall() {
//        val accountInfo = currentAccountInfo ?: return
//        val callData = accountInfo.currentCallData ?: return
//
//        CoroutineScope(Dispatchers.IO).launch {
//            callHoldManager.resumeCall()?.let { resumeSdp ->
//                callData.localSdp = resumeSdp
//                callData.isOnHold = false
//                messageHandler.sendReInvite(accountInfo, callData, resumeSdp)
//                callState = CallState.CONNECTED
//                eventListener?.onCallHold(callData.callId, false)
//            }
//        }
//    }
//
//    // Enhanced audio device management
//    fun changeAudioDeviceByType(deviceType: EddysSipLibrary.AudioDeviceType): Boolean {
//        return audioDeviceManager?.changeDeviceByType(deviceType) ?: false
//    }
//
//    fun getAllAudioDevicesWithCallback(): Pair<List<AudioDevice>, List<AudioDevice>> {
//        val devices = webRtcManager.getAllAudioDevices()
//        eventListener?.onAudioDevicesAvailable(devices.first, devices.second)
//        return devices
//    }
//
//    // Enhanced configuration management
//    fun updateUserAgent(newUserAgent: String) {
//        currentUserAgent = newUserAgent
//        activeAccounts.values.forEach { accountInfo ->
//            accountInfo.userAgent = newUserAgent
//        }
//    }
//
//    fun updatePushConfiguration(token: String, provider: String, customParams: Map<String, String> = emptyMap()) {
//        activeAccounts.values.forEach { accountInfo ->
//            accountInfo.token = token
//            accountInfo.provider = provider
//        }
//        eventListener?.onPushTokenUpdated(token, provider)
//    }
//
//    fun updateConfig(newConfig: EddysSipLibrary.SipConfig) {
//        // Update internal configuration
//        customHeaders.clear()
//        customHeaders.putAll(newConfig.customHeaders)
//        currentUserAgent = newConfig.userAgent
//    }
//
//    // Enhanced push mode management
////    fun enterPushMode(reason: String = "Manual") {
////        if (!isPushMode) {
////            isPushMode = true
////            eventListener?.onPushModeChanged(true, reason)
////            // Update user agent for push mode
////            currentUserAgent = "${config.userAgent} (Push)"
////            refreshAllRegistrationsWithNewUserAgent()
////        }
////    }
//
//    fun exitPushMode(reason: String = "Manual") {
//        if (isPushMode) {
//            isPushMode = false
//            eventListener?.onPushModeChanged(false, reason)
//            // Restore normal user agent
//            currentUserAgent = config.userAgent
//            refreshAllRegistrationsWithNewUserAgent()
//        }
//    }
//
//    // Enhanced statistics and monitoring
//    fun getCurrentCallStatistics(): CallStatistics? {
//        return callStatistics
//    }
//
//    fun getNetworkQuality(): EddysSipLibrary.NetworkQuality? {
//        return networkQualityMonitor?.getCurrentQuality()
//    }
//
//    private fun calculateCallDuration(): Long {
//        return if (callStartTimeMillis > 0) {
//            Clock.System.now().toEpochMilliseconds() - callStartTimeMillis
//        } else 0L
//    }
//
//    private fun determineEndReason(previousState: CallState): EddysSipLibrary.CallEndReason {
//        return when (previousState) {
//            CallState.CALLING, CallState.OUTGOING -> EddysSipLibrary.CallEndReason.CANCELLED
//            CallState.INCOMING, CallState.RINGING -> EddysSipLibrary.CallEndReason.DECLINED
//            CallState.CONNECTED -> EddysSipLibrary.CallEndReason.USER_HANGUP
//            else -> EddysSipLibrary.CallEndReason.NETWORK_ERROR
//        }
//    }
//
//    // Legacy methods maintained for compatibility
//    fun clearCallLogs() = callHistoryManager.clearCallLogs()
//    fun callLogs(): List<CallLog> = callHistoryManager.getAllCallLogs()
//    fun getCallStatistics() = callHistoryManager.getCallStatistics()
//    fun getMissedCalls(): List<CallLog> = callHistoryManager.getMissedCalls()
//    fun getCallLogsForNumber(phoneNumber: String): List<CallLog> =
//        callHistoryManager.getCallLogsForNumber(phoneNumber)
//
//    fun getRegistrationState(): RegistrationState = registrationState
//    fun currentCall(): Boolean = callState != CallState.NONE && callState != CallState.ENDED
//    fun currentCallConnected(): Boolean = callState == CallState.CONNECTED
//
//    fun isSipCoreManagerHealthy(): Boolean {
//        return try {
//            webRtcManager.isInitialized() &&
//                    activeAccounts.isNotEmpty() &&
//                    !reconnectionInProgress
//        } catch (e: Exception) {
//            false
//        }
//    }
//
//    fun getSystemHealthReport(): String {
//        return buildString {
//            appendLine("=== Enhanced SIP Core Manager Health Report ===")
//            appendLine("Overall Health: ${if (isSipCoreManagerHealthy()) "✅ HEALTHY" else "❌ UNHEALTHY"}")
//            appendLine("WebRTC Initialized: ${webRtcManager.isInitialized()}")
//            appendLine("Active Accounts: ${activeAccounts.size}")
//            appendLine("Current Call State: $callState")
//            appendLine("Registration State: $registrationState")
//            appendLine("Push Mode: $isPushMode")
//            appendLine("User Agent: $currentUserAgent")
//            appendLine("Network Quality: ${getNetworkQuality()?.score ?: "N/A"}")
//            appendLine("Call Statistics: ${callStatistics != null}")
//        }
//    }
//
//    fun enterPushMode(token: String? = null) {
//        token?.let { newToken ->
//            activeAccounts.values.forEach { accountInfo ->
//                accountInfo.token = newToken
//            }
//        }
//        enterPushMode("Token updated")
//    }
//
//    private fun determineCallType(callData: CallData, finalState: CallState): CallTypes {
//        return when (finalState) {
//            CallState.CONNECTED, CallState.ENDED -> CallTypes.SUCCESS
//            CallState.DECLINED -> CallTypes.DECLINED
//            CallState.CALLING, CallState.RINGING -> CallTypes.ABORTED
//            else -> if (callData.direction == CallDirections.INCOMING) {
//                CallTypes.MISSED
//            } else {
//                CallTypes.ABORTED
//            }
//        }
//    }
//
//    fun dispose() {
//        networkQualityMonitor?.dispose()
//        audioDeviceManager?.dispose()
//        webRtcManager.dispose()
//        activeAccounts.clear()
//    }
//
//    fun getMessageHandler(): SipMessageHandler = messageHandler
//}
//
///**
// * Monitor de calidad de red
// */
//class NetworkQualityMonitor(
//    private val callback: (EddysSipLibrary.NetworkQuality) -> Unit
//) {
//    private var currentQuality: EddysSipLibrary.NetworkQuality? = null
//    private val monitorJob = CoroutineScope(Dispatchers.IO).launch {
//        while (true) {
//            updateNetworkQuality()
//            delay(5000) // Actualizar cada 5 segundos
//        }
//    }
//
//    private fun updateNetworkQuality() {
//        // Simular medición de calidad de red
//        val latency = (50..200).random().toLong()
//        val packetLoss = (0..5).random().toFloat() / 100f
//        val jitter = (5..50).random().toLong()
//        val score = calculateScore(latency, packetLoss, jitter)
//
//        currentQuality = EddysSipLibrary.NetworkQuality(
//            score = score,
//            latency = latency,
//            packetLoss = packetLoss,
//            jitter = jitter
//        )
//
//        currentQuality?.let { callback(it) }
//    }
//
//    private fun calculateScore(latency: Long, packetLoss: Float, jitter: Long): Float {
//        val latencyScore = when {
//            latency < 100 -> 1.0f
//            latency < 200 -> 0.8f
//            latency < 300 -> 0.6f
//            else -> 0.4f
//        }
//
//        val packetLossScore = when {
//            packetLoss < 0.01f -> 1.0f
//            packetLoss < 0.03f -> 0.8f
//            packetLoss < 0.05f -> 0.6f
//            else -> 0.4f
//        }
//
//        val jitterScore = when {
//            jitter < 20 -> 1.0f
//            jitter < 40 -> 0.8f
//            jitter < 60 -> 0.6f
//            else -> 0.4f
//        }
//
//        return (latencyScore + packetLossScore + jitterScore) / 3f
//    }
//
//    fun getCurrentQuality(): EddysSipLibrary.NetworkQuality? = currentQuality
//
//    fun dispose() {
//        monitorJob.cancel()
//    }
//}
//
///**
// * Manager de dispositivos de audio mejorado
// */
//class AudioDeviceManager(
//    private val webRtcManager: WebRtcManager,
//    private val callback: (AudioDevice?, AudioDevice) -> Unit
//) {
//    private var currentDevice: AudioDevice? = null
//
//    fun changeDeviceByType(deviceType: EddysSipLibrary.AudioDeviceType): Boolean {
//        val (inputDevices, outputDevices) = webRtcManager.getAllAudioDevices()
//        val targetDevice = findDeviceByType(outputDevices, deviceType)
//
//        return if (targetDevice != null) {
//            val oldDevice = currentDevice
//            val success = webRtcManager.changeAudioOutputDeviceDuringCall(targetDevice)
//            if (success) {
//                currentDevice = targetDevice
//                callback(oldDevice, targetDevice)
//            }
//            success
//        } else {
//            false
//        }
//    }
//
//    private fun findDeviceByType(devices: List<AudioDevice>, type: EddysSipLibrary.AudioDeviceType): AudioDevice? {
//        return when (type) {
//            EddysSipLibrary.AudioDeviceType.EARPIECE -> devices.find { it.descriptor == "earpiece" }
//            EddysSipLibrary.AudioDeviceType.SPEAKER -> devices.find { it.descriptor == "speaker" }
//            EddysSipLibrary.AudioDeviceType.BLUETOOTH -> devices.find { it.descriptor.startsWith("bluetooth_") }
//            EddysSipLibrary.AudioDeviceType.WIRED_HEADSET -> devices.find { it.descriptor == "wired_headset" }
//            EddysSipLibrary.AudioDeviceType.AUTO -> selectBestDevice(devices)
//        }
//    }
//
//    private fun selectBestDevice(devices: List<AudioDevice>): AudioDevice? {
//        // Prioridad: Bluetooth > Wired Headset > Earpiece > Speaker
//        return devices.find { it.descriptor.startsWith("bluetooth_") }
//            ?: devices.find { it.descriptor == "wired_headset" }
//            ?: devices.find { it.descriptor == "earpiece" }
//            ?: devices.find { it.descriptor == "speaker" }
//    }
//
//    fun dispose() {
//        // Cleanup if needed
//    }
//}
//
///**
// * Estadísticas de llamada extendidas
// */
//data class CallStatistics(
//    val callId: String,
//    val duration: Long,
//    val audioCodec: String,
//    val networkQuality: Float,
//    val packetsLost: Int,
//    val jitter: Long,
//    val rtt: Long,
//    val bitrate: Int = 0,
//    val audioLevel: Float = 0f
//)