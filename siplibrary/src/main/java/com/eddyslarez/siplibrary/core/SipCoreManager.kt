package com.eddyslarez.siplibrary.core

import android.app.Application
import android.content.Context
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.*
import com.eddyslarez.siplibrary.data.services.audio.AudioDevice
import com.eddyslarez.siplibrary.data.services.audio.CallHoldManager
import com.eddyslarez.siplibrary.data.services.audio.PlayRingtoneUseCase
import com.eddyslarez.siplibrary.data.services.audio.WebRtcConnectionState
import com.eddyslarez.siplibrary.data.services.audio.WebRtcEventListener
import com.eddyslarez.siplibrary.data.services.audio.WebRtcManager
import com.eddyslarez.siplibrary.data.services.audio.WebRtcManagerFactory
import com.eddyslarez.siplibrary.data.services.sip.SipMessageHandler
import com.eddyslarez.siplibrary.data.services.websocket.MultiplatformWebSocket
import com.eddyslarez.siplibrary.data.services.websocket.WebSocket
import com.eddyslarez.siplibrary.data.store.SettingsDataStore
import com.eddyslarez.siplibrary.platform.PlatformInfo
import com.eddyslarez.siplibrary.platform.PlatformRegistration
import com.eddyslarez.siplibrary.platform.WindowManager
import com.eddyslarez.siplibrary.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Gestor principal del core SIP - Versión mejorada con configuración avanzada
 *
 * @author Eddys Larez
 */
class SipCoreManager private constructor(
    private val application: Application,
    private var config: EddysSipLibrary.SipConfig,
    val playRingtoneUseCase: PlayRingtoneUseCase,
    val windowManager: WindowManager,
    val platformInfo: PlatformInfo,
    val settingsDataStore: SettingsDataStore,
) {
    internal var eventListener: EddysSipLibrary.SipEventListener? = null
    private var internalCallbacks: EddysSipLibrary.SipInternalCallbacks? = null

    val callHistoryManager = CallHistoryManager()
    private var registrationState = RegistrationState.NONE
    private val activeAccounts = HashMap<String, AccountInfo>()
    var callState = CallState.NONE
    var callStartTimeMillis: Long = 0
    var currentAccountInfo: AccountInfo? = null
    var isAppInBackground = false
    private var reconnectionInProgress = false
    private var lastConnectionCheck = 0L
    private val connectionCheckInterval = 30000L
    private val dtmfQueue = mutableListOf<DtmfRequest>()
    private var isDtmfProcessing = false
    private val dtmfMutex = Mutex()
    var onCallTerminated: (() -> Unit)? = null
    var isCallFromPush = false
    private var currentOperationMode = "foreground"
    private var reconnectAttempts = 0

    // WebRTC manager and other managers
    val webRtcManager = WebRtcManagerFactory.createWebRtcManager(application)
    private val platformRegistration = PlatformRegistration(application)
    private val callHoldManager = CallHoldManager(webRtcManager)
    private val messageHandler = SipMessageHandler(this).apply {
        onCallTerminated = ::handleCallTermination
    }

    companion object {
        private const val TAG = "SipCoreManager"
        private const val WEBSOCKET_PROTOCOL = "sip"
        private const val REGISTRATION_CHECK_INTERVAL_MS = 30 * 1000L

        fun createInstance(
            application: Application,
            config: EddysSipLibrary.SipConfig
        ): SipCoreManager {
            return SipCoreManager(
                application = application,
                config = config,
                playRingtoneUseCase = PlayRingtoneUseCase(application),
                windowManager = WindowManager(),
                platformInfo = PlatformInfo(),
                settingsDataStore = SettingsDataStore(application)
            )
        }
    }

    // ===================== CONFIGURACIÓN Y INICIALIZACIÓN =====================

    fun updateConfiguration(newConfig: EddysSipLibrary.SipConfig) {
        this.config = newConfig
        log.d(tag = TAG) { "Configuration updated" }
    }

    fun setEventListener(listener: EddysSipLibrary.SipEventListener) {
        this.eventListener = listener
    }

    fun setInternalCallbacks(callbacks: EddysSipLibrary.SipInternalCallbacks) {
        this.internalCallbacks = callbacks
    }

    fun userAgent(): String = config.userAgent

    fun getDefaultDomain(): String? = config.defaultDomain.takeIf { it.isNotEmpty() }

    fun getCurrentUsername(): String? = currentAccountInfo?.username

    fun initialize() {
        log.d(tag = TAG) { "Initializing SIP Core with enhanced configuration" }

        webRtcManager.initialize()
        setupWebRtcEventListener()
        setupPlatformLifecycleObservers()
        startConnectionHealthCheck()

        // Configurar audio automático si está habilitado
        if (config.enableAutoAudioRouting) {
            setupAutoAudioRouting()
        }
    }

    // ===================== GESTIÓN DE MODO DE OPERACIÓN =====================

    fun switchToForegroundMode() {
        currentOperationMode = "foreground"
        isAppInBackground = false
        reconnectAttempts = 0

        if (config.autoReconnectWebSocketOnForeground) {
            reconnectAllAccounts()
        }

        refreshAllRegistrationsWithNewUserAgent()
        log.d(tag = TAG) { "Switched to foreground mode" }
    }

    fun switchToPushMode(pushToken: String) {
        currentOperationMode = "push"

        activeAccounts.values.forEach { accountInfo ->
            accountInfo.token = pushToken
            accountInfo.provider = config.defaultPushProvider
        }

        refreshAllRegistrationsWithNewUserAgent()
        log.d(tag = TAG) { "Switched to push mode with token: ${pushToken.take(10)}..." }
    }

    fun disconnectAll() {
        currentOperationMode = "disconnected"

        activeAccounts.values.forEach { accountInfo ->
            accountInfo.webSocketClient?.close()
        }

        updateRegistrationState(RegistrationState.NONE)
        log.d(tag = TAG) { "Disconnected all connections" }
    }

    fun handleAppMovedToBackground() {
        isAppInBackground = true
        eventListener?.onAppMovedToBackground()

        if (config.autoSwitchToPushOnBackground && activeAccounts.values.any { it.token.isNotEmpty() }) {
            val token = activeAccounts.values.first { it.token.isNotEmpty() }.token
            switchToPushMode(token)
        }
    }

    fun handleAppMovedToForeground() {
        isAppInBackground = false
        eventListener?.onAppMovedToForeground()

        if (config.autoSwitchToForegroundOnResume) {
            switchToForegroundMode()
        }
    }

    fun isInPushMode(): Boolean = currentOperationMode == "push"
    fun isConnected(): Boolean = activeAccounts.values.any { it.webSocketClient?.isConnected() == true }

    // ===================== REGISTRO DE CUENTAS MEJORADO =====================

    fun register(
        username: String,
        password: String,
        domain: String,
        provider: String,
        token: String,
        customHeaders: Map<String, String> = emptyMap(),
        expirationSeconds: Int = config.registrationExpirationSeconds
    ) {
        try {
            val accountKey = "$username@$domain"
            val accountInfo = AccountInfo(username, password, domain).apply {
                this.token = token
                this.provider = provider
                this.userAgent = userAgent()
                this.customHeaders = customHeaders
                this.expirationSeconds = expirationSeconds
            }

            activeAccounts[accountKey] = accountInfo
            connectWebSocketAndRegister(accountInfo)

        } catch (e: Exception) {
            updateRegistrationState(RegistrationState.FAILED)
            internalCallbacks?.onError("Registration error: ${e.message}", e)
            throw Exception("Registration error: ${e.message}")
        }
    }

    fun unregister(username: String, domain: String) {
        val accountKey = "$username@$domain"
        val accountInfo = activeAccounts[accountKey] ?: return

        try {
            messageHandler.sendUnregister(accountInfo)
            accountInfo.webSocketClient?.close()
            activeAccounts.remove(accountKey)

            if (activeAccounts.isEmpty()) {
                updateRegistrationState(RegistrationState.NONE)
            }

        } catch (e: Exception) {
            log.d(tag = TAG) { "Error unregistering account: ${e.message}" }
        }
    }

    // ===================== GESTIÓN DE LLAMADAS MEJORADA =====================

    fun makeCall(
        phoneNumber: String,
        sipName: String,
        domain: String,
        customHeaders: Map<String, String> = emptyMap()
    ) {
        val accountKey = "$sipName@$domain"
        val accountInfo = activeAccounts[accountKey] ?: return
        currentAccountInfo = accountInfo

        if (!accountInfo.isRegistered) {
            val error = "Not registered with SIP server"
            eventListener?.onCallFailed("", error)
            log.d(tag = TAG) { "Error: $error" }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
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
                ).apply {
                    this.customHeaders = customHeaders
                }

                accountInfo.currentCallData = callData
                CallStateManager.updateCallState(CallState.CALLING)
                callState = CallState.CALLING
                CallStateManager.callerNumber(phoneNumber)

                // Notificar cambio de estado
                eventListener?.onCallStateChanged(CallState.CALLING, callId)
                internalCallbacks?.onCallStateChanged(CallState.CALLING, callId)

                messageHandler.sendInvite(accountInfo, callData)

            } catch (e: Exception) {
                val errorMsg = "Error creating call: ${e.message}"
                eventListener?.onCallFailed("", errorMsg)
                log.e(tag = TAG) { errorMsg }
            }
        }
    }

    fun acceptCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        if (callData.direction != CallDirections.INCOMING ||
            (callState != CallState.INCOMING && callState != CallState.RINGING)) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
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

                callState = CallState.ACCEPTING
                eventListener?.onCallStateChanged(CallState.ACCEPTING, callData.callId)

            } catch (e: Exception) {
                log.e(tag = TAG) { "Error accepting call: ${e.message}" }
                eventListener?.onCallFailed(callData.callId, "Error accepting call: ${e.message}")
                rejectCall()
            }
        }
    }

    fun declineCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        if (callData.direction != CallDirections.INCOMING ||
            (callState != CallState.INCOMING && callState != CallState.RINGING)) {
            return
        }

        if (callData.toTag?.isEmpty() == true) {
            callData.toTag = generateId()
        }

        messageHandler.sendDeclineResponse(accountInfo, callData)

        val endTime = Clock.System.now().toEpochMilliseconds()
        callHistoryManager.addCallLog(callData, CallTypes.DECLINED, endTime)

        CallStateManager.updateCallState(CallState.DECLINED)
        callState = CallState.DECLINED

        eventListener?.onCallStateChanged(CallState.DECLINED, callData.callId)
        eventListener?.onCallDisconnected(callData.callId, "Call declined", 0)
    }

    fun endCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        if (callState == CallState.NONE || callState == CallState.ENDED) {
            return
        }

        val endTime = Clock.System.now().toEpochMilliseconds()
        val duration = if (callStartTimeMillis > 0) endTime - callStartTimeMillis else 0

        when (callState) {
            CallState.CONNECTED, CallState.HOLDING, CallState.ACCEPTING -> {
                messageHandler.sendBye(accountInfo, callData)
                callHistoryManager.addCallLog(callData, CallTypes.SUCCESS, endTime)
                eventListener?.onCallDisconnected(callData.callId, "Call ended", duration)
            }
            CallState.CALLING, CallState.RINGING, CallState.OUTGOING -> {
                messageHandler.sendCancel(accountInfo, callData)
                callHistoryManager.addCallLog(callData, CallTypes.ABORTED, endTime)
                eventListener?.onCallDisconnected(callData.callId, "Call cancelled", duration)
            }
            else -> {}
        }

        CallStateManager.updateCallState(CallState.ENDED)
        callState = CallState.ENDED
        eventListener?.onCallStateChanged(CallState.ENDED, callData.callId)

        webRtcManager.dispose()
        clearDtmfQueue()
        accountInfo.resetCallState()
        onCallTerminated?.invoke()
    }

    // ===================== GESTIÓN DE AUDIO MEJORADA =====================

    private fun setupAutoAudioRouting() {
        config.preferredAudioDevice?.let { preferred ->
            CoroutineScope(Dispatchers.IO).launch {
                val devices = webRtcManager.getAllAudioDevices()
                val targetDevice = devices.second.find { it.descriptor == preferred }
                targetDevice?.let { device ->
                    webRtcManager.changeAudioOutputDeviceDuringCall(device)
                    eventListener?.onAudioDeviceSelected(device)
                }
            }
        }
    }

    fun toggleMute() {
        val wasTrue = webRtcManager.isMuted()
        webRtcManager.setMuted(!wasTrue)
        val isMuted = webRtcManager.isMuted()
        eventListener?.onMuteStateChanged(isMuted)
    }

    // ===================== GESTIÓN DE CONEXIÓN MEJORADA =====================

    private fun setupWebRtcEventListener() {
        webRtcManager.setListener(object : WebRtcEventListener {
            override fun onIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
                // Implementar envío de ICE candidate
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
                eventListener?.onAudioDeviceSelected(device)
                log.d(tag = TAG) { "Audio device changed: ${device.name}" }
            }
        })
    }

    private fun handleWebRtcConnected() {
        callStartTimeMillis = Clock.System.now().toEpochMilliseconds()
        CallStateManager.updateCallState(CallState.CONNECTED)
        callState = CallState.CONNECTED

        currentAccountInfo?.currentCallData?.let { callData ->
            eventListener?.onCallConnected(callData.callId, 0)
            eventListener?.onCallStateChanged(CallState.CONNECTED, callData.callId)
        }
    }

    private fun handleWebRtcClosed() {
        callState = CallState.ENDED
        currentAccountInfo?.currentCallData?.let { callData ->
            val endTime = Clock.System.now().toEpochMilliseconds()
            val duration = if (callStartTimeMillis > 0) endTime - callStartTimeMillis else 0
            val callType = determineCallType(callData, callState)
            callHistoryManager.addCallLog(callData, callType, endTime)
            eventListener?.onCallDisconnected(callData.callId, "Connection closed", duration)
        }
    }

    private fun connectWebSocketAndRegister(accountInfo: AccountInfo) {
        try {
            accountInfo.webSocketClient?.close()
            val headers = createHeaders()
            val webSocketClient = createWebSocketClient(accountInfo, headers)
            accountInfo.webSocketClient = webSocketClient
        } catch (e: Exception) {
            reconnectAttempts++
            if (reconnectAttempts < config.maxReconnectAttempts) {
                val delay = if (config.exponentialBackoff) {
                    config.reconnectDelayMs * (1 shl (reconnectAttempts - 1))
                } else {
                    config.reconnectDelayMs
                }

                CoroutineScope(Dispatchers.IO).launch {
                    delay(delay)
                    connectWebSocketAndRegister(accountInfo)
                }
            } else {
                internalCallbacks?.onError("Max reconnection attempts reached", e)
            }

            log.d(tag = TAG) { "Error connecting WebSocket: ${e.stackTraceToString()}" }
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
        websocket.startRegistrationRenewalTimer(REGISTRATION_CHECK_INTERVAL_MS, config.renewBeforeExpirationMs)
        return websocket
    }

    private fun setupWebSocketListeners(websocket: WebSocket, accountInfo: AccountInfo) {
        websocket.setListener(object : MultiplatformWebSocket.Listener {
            override fun onOpen() {
                reconnectionInProgress = false
                reconnectAttempts = 0
                lastConnectionCheck = Clock.System.now().toEpochMilliseconds()
                messageHandler.sendRegister(accountInfo, isAppInBackground)
                internalCallbacks?.onWebSocketConnected()
                eventListener?.onWebSocketConnected()
            }

            override fun onMessage(message: String) {
                messageHandler.handleSipMessage(message, accountInfo)
            }

            override fun onClose(code: Int, reason: String) {
                accountInfo.isRegistered = false
                updateRegistrationState(RegistrationState.NONE)
                internalCallbacks?.onWebSocketDisconnected(code, reason)
                eventListener?.onWebSocketDisconnected(code, reason)

                if (code != 1000 && config.enableAutoReconnect) {
                    handleUnexpectedDisconnection(accountInfo)
                }
            }

            override fun onError(error: Exception) {
                accountInfo.isRegistered = false
                updateRegistrationState(RegistrationState.FAILED)
                internalCallbacks?.onError("WebSocket error: ${error.message}", error)
                eventListener?.onWebSocketError(error.message ?: "Unknown WebSocket error")
                handleConnectionError(accountInfo, error)
            }

            override fun onPong(timeMs: Long) {
                lastConnectionCheck = Clock.System.now().toEpochMilliseconds()
            }

            override fun onRegistrationRenewalRequired(accountKey: String) {
                val account = activeAccounts[accountKey]
                if (account != null && account.webSocketClient?.isConnected() == true) {
                    messageHandler.sendRegister(account, isAppInBackground)
                } else {
                    account?.let { reconnectAccount(it) }
                }
            }
        })
    }

    // ===================== MÉTODOS DE UTILIDAD Y ESTADO =====================

    fun getWebSocketStatus(): String {
        return buildString {
            appendLine("=== WebSocket Status ===")
            activeAccounts.forEach { (key, account) ->
                appendLine("Account: $key")
                appendLine("  Connected: ${account.webSocketClient?.isConnected()}")
                appendLine("  Registered: ${account.isRegistered}")
                appendLine("  Mode: $currentOperationMode")
            }
        }
    }

    fun isSystemHealthy(): Boolean {
        return try {
            webRtcManager.isInitialized() &&
                    activeAccounts.isNotEmpty() &&
                    !reconnectionInProgress &&
                    reconnectAttempts < config.maxReconnectAttempts
        } catch (e: Exception) {
            false
        }
    }

    fun getSystemHealthReport(): String {
        return buildString {
            appendLine("=== SIP Core Manager Health Report ===")
            appendLine("Overall Health: ${if (isSystemHealthy()) "✅ HEALTHY" else "❌ UNHEALTHY"}")
            appendLine("WebRTC Initialized: ${webRtcManager.isInitialized()}")
            appendLine("Active Accounts: ${activeAccounts.size}")
            appendLine("Current Call State: $callState")
            appendLine("Registration State: $registrationState")
            appendLine("Current Mode: $currentOperationMode")
            appendLine("Reconnect Attempts: $reconnectAttempts/${config.maxReconnectAttempts}")
            appendLine("Auto Features:")
            appendLine("  - Auto Push on Background: ${config.autoSwitchToPushOnBackground}")
            appendLine("  - Auto Foreground on Resume: ${config.autoSwitchToForegroundOnResume}")
            appendLine("  - Auto Disconnect on Background: ${config.autoDisconnectWebSocketOnBackground}")
            appendLine("  - Auto Audio Routing: ${config.enableAutoAudioRouting}")
        }
    }

    fun updatePushToken(token: String, provider: String = "fcm") {
        activeAccounts.values.forEach { accountInfo ->
            accountInfo.token = token
            accountInfo.provider = provider
        }

        if (currentOperationMode == "push") {
            refreshAllRegistrationsWithNewUserAgent()
        }
    }

    fun forceReconnect() {
        reconnectAttempts = 0
        reconnectAllAccounts()
    }

    fun disconnectWebSocket() {
        activeAccounts.values.forEach { accountInfo ->
            accountInfo.webSocketClient?.close(1000, "App moved to background")
        }
    }

    private fun reconnectAllAccounts() {
        activeAccounts.values.forEach { accountInfo ->
            reconnectAccount(accountInfo)
        }
    }

    // ===================== MÉTODOS HEREDADOS =====================

    private fun setupPlatformLifecycleObservers() {
        platformRegistration.setupNotificationObservers(object : AppLifecycleListener {
            override fun onEvent(event: AppLifecycleEvent) {
                when (event) {
                    AppLifecycleEvent.EnterBackground -> {
                        isAppInBackground = true
                        if (config.autoSwitchToPushOnBackground) {
                            handleAppMovedToBackground()
                        }
                    }
                    AppLifecycleEvent.EnterForeground -> {
                        isAppInBackground = false
                        if (config.autoSwitchToForegroundOnResume) {
                            handleAppMovedToForeground()
                        }
                    }
                    else -> {}
                }
            }
        })
    }

    private fun refreshAllRegistrationsWithNewUserAgent() {
        if (callState != CallState.NONE && callState != CallState.ENDED) {
            return
        }

        activeAccounts.values.forEach { accountInfo ->
            if (accountInfo.isRegistered) {
                accountInfo.userAgent = userAgent()
                messageHandler.sendRegister(accountInfo, isAppInBackground)
            }
        }
    }

    private fun startConnectionHealthCheck() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(connectionCheckInterval)
                if (config.enableAutoReconnect) {
                    checkConnectionHealth()
                }
            }
        }
    }

    private fun checkConnectionHealth() {
        activeAccounts.values.forEach { accountInfo ->
            val webSocket = accountInfo.webSocketClient
            if (webSocket != null && accountInfo.isRegistered) {
                if (!webSocket.isConnected()) {
                    reconnectAccount(accountInfo)
                }
            }
        }
    }

    private fun reconnectAccount(accountInfo: AccountInfo) {
        if (reconnectionInProgress || reconnectAttempts >= config.maxReconnectAttempts) return

        reconnectionInProgress = true
        try {
            accountInfo.webSocketClient?.close()
            accountInfo.userAgent = userAgent()
            val headers = createHeaders()
            val newWebSocketClient = createWebSocketClient(accountInfo, headers)
            accountInfo.webSocketClient = newWebSocketClient
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error during reconnection: ${e.message}" }
        } finally {
            reconnectionInProgress = false
        }
    }

    private fun handleUnexpectedDisconnection(accountInfo: AccountInfo) {
        if (!reconnectionInProgress && reconnectAttempts < config.maxReconnectAttempts) {
            CoroutineScope(Dispatchers.IO).launch {
                val delay = if (config.exponentialBackoff) {
                    config.reconnectDelayMs * (1 shl reconnectAttempts)
                } else {
                    config.reconnectDelayMs
                }
                delay(delay)
                reconnectAccount(accountInfo)
            }
        }
    }

    private fun handleConnectionError(accountInfo: AccountInfo, error: Exception) {
        lastConnectionCheck = 0L
        when {
            error.message?.contains("timeout") == true -> {
                forceReconnectAccount(accountInfo)
            }
            else -> {
                reconnectAccount(accountInfo)
            }
        }
    }

    private fun forceReconnectAccount(accountInfo: AccountInfo) {
        reconnectAccount(accountInfo)
    }

    private fun handleCallTermination() {
        onCallTerminated?.invoke()
    }

    fun updateRegistrationState(newState: RegistrationState) {
        registrationState = newState
        RegistrationStateManager.updateCallState(newState)
        internalCallbacks?.onRegistrationStateChanged(newState)
    }

    private fun createHeaders(): HashMap<String, String> {
        return hashMapOf(
            "User-Agent" to userAgent(),
            "Origin" to "https://telephony.${config.defaultDomain}",
            "Sec-WebSocket-Protocol" to WEBSOCKET_PROTOCOL
        ).apply {
            // Agregar headers personalizados
            putAll(config.customSipHeaders)
        }
    }

    // ===================== MÉTODOS DTMF HEREDADOS =====================

    fun sendDtmf(digit: Char, duration: Int = 160): Boolean {
        val validDigits = setOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '*', '#', 'A', 'B', 'C', 'D', 'a', 'b', 'c', 'd'
        )

        if (!validDigits.contains(digit)) {
            return false
        }

        val request = DtmfRequest(digit, duration)
        CoroutineScope(Dispatchers.IO).launch {
            dtmfMutex.withLock {
                dtmfQueue.add(request)
            }
            processDtmfQueue()
        }

        return true
    }

    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean {
        if (digits.isEmpty()) return false

        digits.forEach { digit ->
            sendDtmf(digit, duration)
        }

        return true
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
                eventListener?.onDtmfSent(request.digit, success)

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

        if (currentAccount == null || callData == null || callState != CallState.CONNECTED) {
            return false
        }

        return try {
            // Usar WebRTC para DTMF en Android
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
        CoroutineScope(Dispatchers.IO).launch {
            dtmfMutex.withLock {
                dtmfQueue.clear()
                isDtmfProcessing = false
            }
        }
    }

    // ===================== MÉTODOS DE LLAMADA HEREDADOS =====================

    fun holdCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        CoroutineScope(Dispatchers.IO).launch {
            callHoldManager.holdCall()?.let { holdSdp ->
                callData.localSdp = holdSdp
                callData.isOnHold = true
                messageHandler.sendReInvite(accountInfo, callData, holdSdp)
                callState = CallState.HOLDING
                eventListener?.onCallHeld(callData.callId)
                eventListener?.onCallStateChanged(CallState.HOLDING, callData.callId)
            }
        }
    }

    fun resumeCall() {
        val accountInfo = currentAccountInfo ?: return
        val callData = accountInfo.currentCallData ?: return

        CoroutineScope(Dispatchers.IO).launch {
            callHoldManager.resumeCall()?.let { resumeSdp ->
                callData.localSdp = resumeSdp
                callData.isOnHold = false
                messageHandler.sendReInvite(accountInfo, callData, resumeSdp)
                callState = CallState.CONNECTED
                eventListener?.onCallResumed(callData.callId)
                eventListener?.onCallStateChanged(CallState.CONNECTED, callData.callId)
            }
        }
    }

    private fun rejectCall() = declineCall()

    // ===================== MÉTODOS DE ACCESO HEREDADOS =====================

    fun clearCallLogs() = callHistoryManager.clearCallLogs()
    fun callLogs(): List<CallLog> = callHistoryManager.getAllCallLogs()
    fun getCallStatistics() = callHistoryManager.getCallStatistics()
    fun getMissedCalls(): List<CallLog> = callHistoryManager.getMissedCalls()
    fun getCallLogsForNumber(phoneNumber: String): List<CallLog> =
        callHistoryManager.getCallLogsForNumber(phoneNumber)

    fun getRegistrationState(): RegistrationState = registrationState
    fun currentCall(): Boolean = callState != CallState.NONE && callState != CallState.ENDED
    fun currentCallConnected(): Boolean = callState == CallState.CONNECTED
    fun getCurrentCallState(): CallState = callState

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

    fun dispose() {
        webRtcManager.dispose()
        activeAccounts.clear()
    }

    fun getMessageHandler(): SipMessageHandler = messageHandler
}
//
///**
// * Gestor principal del core SIP - Adaptado para Android
// *
// * @author Eddys Larez
// */
//class SipCoreManager private constructor(
//    private val application: Application,
//    private val config: EddysSipLibrary.SipConfig,
//    val playRingtoneUseCase: PlayRingtoneUseCase,
//    val windowManager: WindowManager,
//    val platformInfo: PlatformInfo,
//    val settingsDataStore: SettingsDataStore,
//) {
//    private var sipCallbacks: EddysSipLibrary.SipCallbacks? = null
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
//            config: EddysSipLibrary.SipConfig
//        ): SipCoreManager {
//            return SipCoreManager(
//                application = application,
//                config = config,
//                playRingtoneUseCase = PlayRingtoneUseCase(application),
//                windowManager = WindowManager(),
//                platformInfo = PlatformInfo(),
//                settingsDataStore = SettingsDataStore(application)
//            )
//        }
//    }
//
//    fun userAgent(): String = config.userAgent
//
//    fun getDefaultDomain(): String? = currentAccountInfo?.domain
//
//    fun getCurrentUsername(): String? = currentAccountInfo?.username
//
//    fun initialize() {
//        log.d(tag = TAG) { "Initializing SIP Core" }
//
//        webRtcManager.initialize()
//        setupWebRtcEventListener()
//        setupPlatformLifecycleObservers()
//        startConnectionHealthCheck()
//    }
//
//    fun setCallbacks(callbacks: EddysSipLibrary.SipCallbacks) {
//        this.sipCallbacks = callbacks
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
//                        refreshAllRegistrationsWithNewUserAgent()
//                    }
//                    AppLifecycleEvent.EnterForeground -> {
//                        isAppInBackground = false
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
//                    reconnectAccount(accountInfo)
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
//    fun register(
//        username: String,
//        password: String,
//        domain: String,
//        provider: String,
//        token: String
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
//            connectWebSocketAndRegister(accountInfo)
//        } catch (e: Exception) {
//            updateRegistrationState(RegistrationState.FAILED)
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
//        )
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
//                messageHandler.sendRegister(accountInfo, isAppInBackground)
//            }
//
//            override fun onMessage(message: String) {
//                messageHandler.handleSipMessage(message, accountInfo)
//            }
//
//            override fun onClose(code: Int, reason: String) {
//                accountInfo.isRegistered = false
//                updateRegistrationState(RegistrationState.NONE)
//                if (code != 1000) {
//                    handleUnexpectedDisconnection(accountInfo)
//                }
//            }
//
//            override fun onError(error: Exception) {
//                accountInfo.isRegistered = false
//                updateRegistrationState(RegistrationState.FAILED)
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
//    fun makeCall(phoneNumber: String, sipName: String, domain: String) {
//        val accountKey = "$sipName@$domain"
//        val accountInfo = activeAccounts[accountKey] ?: return
//        currentAccountInfo = accountInfo
//
//        if (!accountInfo.isRegistered) {
//            log.d(tag = TAG) { "Error: Not registered with SIP server" }
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
//                messageHandler.sendInvite(accountInfo, callData)
//            } catch (e: Exception) {
//                log.e(tag = TAG) { "Error creating call: ${e.stackTraceToString()}" }
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
//            }
//        }
//    }
//
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
//            activeAccounts.isNotEmpty() &&
//            !reconnectionInProgress
//        } catch (e: Exception) {
//            false
//        }
//    }
//
//    fun getSystemHealthReport(): String {
//        return buildString {
//            appendLine("=== SIP Core Manager Health Report ===")
//            appendLine("Overall Health: ${if (isSipCoreManagerHealthy()) "✅ HEALTHY" else "❌ UNHEALTHY"}")
//            appendLine("WebRTC Initialized: ${webRtcManager.isInitialized()}")
//            appendLine("Active Accounts: ${activeAccounts.size}")
//            appendLine("Current Call State: $callState")
//            appendLine("Registration State: $registrationState")
//        }
//    }
//
//    fun enterPushMode(token: String? = null) {
//        token?.let { newToken ->
//            activeAccounts.values.forEach { accountInfo ->
//                accountInfo.token = newToken
//            }
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
//    fun dispose() {
//        webRtcManager.dispose()
//        activeAccounts.clear()
//    }
//
//    fun getMessageHandler(): SipMessageHandler = messageHandler
//}