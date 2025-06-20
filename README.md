# EddysSipLibrary v3.0.0

Una biblioteca SIP/VoIP moderna y completa para Android con traducción en tiempo real integrada.

## 🚀 Características Principales

### Core SIP/VoIP
- ✅ **Registro de cuentas SIP** con autenticación completa
- ✅ **Llamadas entrantes y salientes** con manejo completo del ciclo de vida
- ✅ **Gestión de audio** avanzada con múltiples dispositivos
- ✅ **DTMF** (tonos de marcado) durante llamadas
- ✅ **Hold/Resume** de llamadas
- ✅ **Ringtones personalizables** para llamadas entrantes y salientes
- ✅ **WebSocket** con reconexión automática
- ✅ **Push notifications** para llamadas cuando la app está en background
- ✅ **Historial de llamadas** con estadísticas

### Traducción en Tiempo Real 🌐
- ✅ **OpenAI Realtime API** integrada
- ✅ **30+ idiomas soportados**
- ✅ **8 voces diferentes** (Alloy, Ash, Ballad, Coral, Echo, Sage, Shimmer, Verse)
- ✅ **Traducción bidireccional** automática
- ✅ **Inicialización independiente** - no requiere la instancia principal
- ✅ **Detección automática de idioma**
- ✅ **Calidad de audio optimizada**

### Sistema de Eventos Mejorado 📡
- ✅ **EventBus global independiente** - agregar listeners desde cualquier parte
- ✅ **30+ tipos de eventos** con type safety
- ✅ **Flows reactivos** para observación de estado
- ✅ **Thread-safe** y optimizado para concurrencia

### Manejo de Errores Avanzado 🛡️
- ✅ **Códigos de error estándar** categorizados
- ✅ **Mensajes user-friendly** automáticos
- ✅ **Sugerencias de recuperación** para cada tipo de error
- ✅ **Errores recuperables vs no recuperables**
- ✅ **Result types** para manejo seguro de errores

## 📦 Instalación

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("com.eddyslarez:siplibrary:3.0.0")
}
```

### Gradle (Groovy)
```groovy
dependencies {
    implementation 'com.eddyslarez:siplibrary:3.0.0'
}
```

## 🚀 Inicio Rápido

### 1. Inicialización Básica

```kotlin
import com.eddyslarez.siplibrary.*
import com.eddyslarez.siplibrary.extensions.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización simple
        lifecycleScope.launch {
            val result = SipLibraryExtensions.initializeSipLibrary(
                application = application,
                domain = "your-sip-domain.com",
                webSocketUrl = "wss://your-sip-server.com/ws"
            )

            result.fold(
                onSuccess = {
                    Log.d("SIP", "Library initialized successfully")
                },
                onFailure = { error ->
                    Log.e("SIP", "Initialization failed: ${error.message}")
                }
            )
        }
    }
}
```

### 2. Configuración Avanzada con DSL

```kotlin
lifecycleScope.launch {
    val config = sipConfig {
        domain("your-domain.com")
        webSocketUrl("wss://your-server.com/ws")
        userAgent("MyApp/1.0.0")
        enableLogs(true)
        autoReconnect(true)
        customHeaders(mapOf(
            "X-App-Version" to "1.0.0",
            "X-Platform" to "Android"
        ))
    }

    val result = EddysSipLibrary.getInstance().initialize(application, config)
    // Manejar resultado...
}
```

## 📡 Sistema de Eventos Independiente

### Agregar Listeners desde Cualquier Parte

```kotlin
class CallActivity : AppCompatActivity() {

    private val sipListener = object : SipEventListener {
        override fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
            runOnUiThread {
                showIncomingCallUI(callerNumber, callId)
            }
        }

        override fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) {
            Log.d("Call", "State changed: $oldState -> $newState")

            when (newState) {
                CallState.CONNECTED -> {
                    startCallTimer()
                    enableCallControls()
                }
                CallState.ENDED -> {
                    finish()
                }
                else -> { /* otros estados */ }
            }
        }

        override fun onError(error: SipError) {
            showErrorDialog(error.getUserFriendlyMessage())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Agregar listener desde cualquier parte
        lifecycleScope.launch {
            SipEventBusExtensions.addListener(sipListener)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Remover listener
        lifecycleScope.launch {
            SipEventBusExtensions.removeListener(sipListener)
        }
    }
}
```

### Observación Reactiva de Eventos

```kotlin
class CallViewModel : ViewModel() {

    private val _callState = MutableLiveData<CallState>()
    val callState: LiveData<CallState> = _callState

    private val _incomingCalls = MutableLiveData<SipEvent.IncomingCall>()
    val incomingCalls: LiveData<SipEvent.IncomingCall> = _incomingCalls

    init {
        // Observar cambios de estado reactivamente
        viewModelScope.launch {
            SipEventBusExtensions.observeCallStateChanges()
                .collect { newState ->
                    _callState.postValue(newState)
                }
        }

        // Observar llamadas entrantes
        viewModelScope.launch {
            SipEventBusExtensions.observeIncomingCalls()
                .collect { incomingCall ->
                    _incomingCalls.postValue(incomingCall)
                }
        }

        // Observar errores específicos
        viewModelScope.launch {
            SipEventBusExtensions.observeErrors()
                .collect { error ->
                    handleError(error)
                }
        }
    }

    private fun handleError(error: SipError) {
        when (error.category) {
            ErrorCategory.NETWORK -> {
                // Manejar errores de red
                if (error.isRecoverable()) {
                    // Intentar reconexión
                    retryConnection()
                } else {
                    // Mostrar error crítico
                    showCriticalError(error.getUserFriendlyMessage())
                }
            }
            ErrorCategory.AUDIO -> {
                // Manejar errores de audio
                showAudioSettings()
            }
            // Otros tipos de errores...
        }
    }
}
```

## 📞 Gestión de Llamadas

### Registro de Cuenta

```kotlin
lifecycleScope.launch {
    val result = EddysSipLibrary.getInstance().registerAccount(
        username = "user123",
        password = "password",
        domain = "sip.example.com",
        pushToken = "fcm_token_here", // Opcional para push notifications
        pushProvider = "fcm"
    )

    ErrorExtensions.handleSipResult(
        result = result,
        onSuccess = {
            Log.d("SIP", "Account registered successfully")
        },
        onError = { error ->
            Log.e("SIP", "Registration failed: ${error.getUserFriendlyMessage()}")

            // Mostrar sugerencias de recuperación
            error.getRecoverySuggestions().forEach { suggestion ->
                Log.d("SIP", "Suggestion: $suggestion")
            }
        }
    )
}
```

### Realizar Llamadas

```kotlin
lifecycleScope.launch {
    val result = EddysSipLibrary.getInstance().makeCall(
        phoneNumber = "+1234567890",
        customHeaders = mapOf("X-Call-Type" to "voice")
    )

    result.fold(
        onSuccess = { callId ->
            Log.d("Call", "Call initiated with ID: $callId")
        },
        onFailure = { error ->
            when (error) {
                is SipLibraryException -> {
                    showCallFailedDialog(error.getUserFriendlyMessage())
                }
            }
        }
    )
}
```

### Controles de Llamada

```kotlin
class CallControlsFragment : Fragment() {

    fun setupCallControls() {
        binding.btnAccept.setOnClickListener {
            lifecycleScope.launch {
                EddysSipLibrary.getInstance().acceptCall()
            }
        }

        binding.btnDecline.setOnClickListener {
            lifecycleScope.launch {
                EddysSipLibrary.getInstance().declineCall()
            }
        }

        binding.btnHangup.setOnClickListener {
            lifecycleScope.launch {
                EddysSipLibrary.getInstance().endCall()
            }
        }

        binding.btnMute.setOnClickListener {
            lifecycleScope.launch {
                val result = EddysSipLibrary.getInstance().toggleMute()
                result.getOrNull()?.let { isMuted ->
                    updateMuteButton(isMuted)
                }
            }
        }

        binding.btnHold.setOnClickListener {
            lifecycleScope.launch {
                if (isCallOnHold) {
                    EddysSipLibrary.getInstance().resumeCall()
                } else {
                    EddysSipLibrary.getInstance().holdCall()
                }
            }
        }
    }
}
```

## 🎵 Gestión de Audio

### Obtener Dispositivos Disponibles

```kotlin
lifecycleScope.launch {
    val result = EddysSipLibrary.getInstance().getAudioDevices()

    result.fold(
        onSuccess = { (inputDevices, outputDevices) ->
            Log.d("Audio", "Input devices: ${inputDevices.size}")
            Log.d("Audio", "Output devices: ${outputDevices.size}")

            inputDevices.forEach { device ->
                Log.d("Audio", "Input: ${device.name} - ${device.descriptor}")
            }

            outputDevices.forEach { device ->
                Log.d("Audio", "Output: ${device.name} - ${device.descriptor}")
            }

            // Actualizar UI con dispositivos disponibles
            updateAudioDevicesUI(inputDevices, outputDevices)
        },
        onFailure = { error ->
            Log.e("Audio", "Failed to get audio devices: ${error.message}")
        }
    )
}
```

### Cambiar Dispositivo de Audio

```kotlin
fun selectAudioDevice(device: AudioDevice) {
    lifecycleScope.launch {
        val result = if (device.isOutput) {
            EddysSipLibrary.getInstance().changeAudioOutputDevice(device)
        } else {
            EddysSipLibrary.getInstance().changeAudioInputDevice(device)
        }

        ErrorExtensions.handleSipResult(
            result = result,
            onSuccess = {
                Log.d("Audio", "Audio device changed to: ${device.name}")
                updateSelectedDeviceUI(device)
            },
            onError = { error ->
                showAudioErrorDialog(error.getUserFriendlyMessage())
            }
        )
    }
}
```

## 🌐 Traducción en Tiempo Real Independiente

### Inicialización de Traducción (Independiente)

```kotlin
class TranslationService : Service() {

    private lateinit var translationManager: TranslationManager

    override fun onCreate() {
        super.onCreate()

        // Inicializar traducción de manera independiente
        lifecycleScope.launch {
            val result = TranslationExtensions.initializeTranslation(
                apiKey = "your-openai-api-key",
                sourceLanguage = TranslationLanguage.SPANISH,
                targetLanguage = TranslationLanguage.ENGLISH,
                voiceStyle = VoiceStyle.ALLOY
            )

            result.fold(
                onSuccess = {
                    Log.d("Translation", "Translation initialized successfully")
                    setupTranslationListener()
                },
                onFailure = { error ->
                    Log.e("Translation", "Translation init failed: ${error.message}")
                }
            )
        }
    }

    private fun setupTranslationListener() {
        lifecycleScope.launch {
            // Observar eventos de traducción
            SipEventBusExtensions.observeTranslationStateChanges()
                .collect { event ->
                    Log.d("Translation", "Translation state: active=${event.isActive}")

                    if (event.isActive) {
                        startTranslationIndicator()
                    } else {
                        stopTranslationIndicator()
                    }
                }
        }
    }
}
```

### Configuración Avanzada de Traducción

```kotlin
class TranslationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración avanzada con DSL
        val translationConfig = translationConfig {
            enable(true)
            apiKey("your-openai-api-key")
            sourceLanguage(TranslationLanguage.AUTO_DETECT)
            targetLanguage(TranslationLanguage.FRENCH)
            voiceStyle(VoiceStyle.CORAL)
            bidirectional(true)
        }

        lifecycleScope.launch {
            val result = TranslationManager.getInstance().initialize(translationConfig)

            ErrorExtensions.handleSipResult(
                result = result,
                onSuccess = {
                    setupTranslationControls()
                },
                onError = { error ->
                    showTranslationError(error)
                }
            )
        }
    }

    private fun setupTranslationControls() {
        binding.btnStartTranslation.setOnClickListener {
            lifecycleScope.launch {
                TranslationExtensions.startTranslation()
            }
        }

        binding.btnStopTranslation.setOnClickListener {
            lifecycleScope.launch {
                TranslationExtensions.stopTranslation()
            }
        }

        // Cambiar idiomas dinámicamente
        binding.spinnerSourceLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = getLanguageFromPosition(position)
                val targetLanguage = getCurrentTargetLanguage()

                lifecycleScope.launch {
                    TranslationManager.getInstance().changeLanguages(selectedLanguage, targetLanguage)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
```

### Usar Traducción Durante Llamadas

```kotlin
class CallWithTranslationActivity : AppCompatActivity() {

    private val translationListener = object : SipEventListener {
        override fun onCallConnected(callId: String, duration: Long) {
            // Iniciar traducción automáticamente cuando se conecta la llamada
            if (TranslationExtensions.isTranslationAvailable()) {
                lifecycleScope.launch {
                    TranslationExtensions.startTranslation()
                }
            }
        }

        override fun onCallDisconnected(callId: String, reason: EddysSipLibrary.CallEndReason, duration: Long) {
            // Detener traducción cuando termina la llamada
            lifecycleScope.launch {
                TranslationExtensions.stopTranslation()
            }
        }

        override fun onTranslationCompleted(originalText: String?, translatedText: String?) {
            runOnUiThread {
                showTranslationResult(originalText, translatedText)
            }
        }

        override fun onTranslationError(error: TranslationError) {
            runOnUiThread {
                showTranslationError(error.getUserFriendlyMessage())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            SipEventBusExtensions.addListener(translationListener)
        }
    }
}
```

## 🎯 DTMF (Tonos de Marcado)

```kotlin
class DTMFFragment : Fragment() {

    fun setupDTMFKeypad() {
        val dtmfButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9, binding.btnStar, binding.btnHash
        )

        dtmfButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val digit = when (index) {
                    10 -> '*'
                    11 -> '#'
                    else -> index.toString().first()
                }

                sendDTMF(digit)
            }
        }
    }

    private fun sendDTMF(digit: Char) {
        lifecycleScope.launch {
            val result = EddysSipLibrary.getInstance().sendDtmf(digit)

            ErrorExtensions.handleSipResult(
                result = result,
                onSuccess = {
                    // Reproducir feedback visual/auditivo
                    animateDTMFButton(digit)
                },
                onError = { error ->
                    Log.e("DTMF", "Failed to send DTMF: ${error.message}")
                }
            )
        }
    }

    // Enviar secuencia de DTMF
    private fun sendDTMFSequence(sequence: String) {
        lifecycleScope.launch {
            val result = EddysSipLibrary.getInstance().sendDtmfSequence(sequence)

            result.fold(
                onSuccess = {
                    Log.d("DTMF", "DTMF sequence sent: $sequence")
                },
                onFailure = { error ->
                    Log.e("DTMF", "Failed to send DTMF sequence: ${error.message}")
                }
            )
        }
    }
}
```

## 📊 Monitoreo y Estadísticas

### Flujos Reactivos de Estado

```kotlin
class StatusViewModel : ViewModel() {

    private val sipLibrary = EddysSipLibrary.getInstance()

    // Estados reactivos
    val callState = sipLibrary.getCallStateFlow().asLiveData()
    val registrationState = sipLibrary.getRegistrationStateFlow().asLiveData()
    val audioState = sipLibrary.getAudioStateFlow().asLiveData()

    // Estado combinado
    val connectionStatus = combine(
        sipLibrary.getCallStateFlow(),
        sipLibrary.getRegistrationStateFlow()
    ) { callState, regState ->
        ConnectionStatus(
            hasActiveCall = callState != CallState.NONE,
            isRegistered = regState == RegistrationState.OK,
            overallHealthy = callState != CallState.ERROR && regState != RegistrationState.FAILED
        )
    }.asLiveData()

    fun getSystemHealth(): String {
        return sipLibrary.getSystemHealthReport()
    }
}
```

### Monitoreo de Calidad de Red

```kotlin
class NetworkQualityMonitor {

    private val sipListener = object : SipEventListener {
        override fun onNetworkQuality(quality: EddysSipLibrary.NetworkQuality) {
            updateNetworkQualityUI(quality)
        }

        override fun onNetworkStateChanged(isConnected: Boolean, networkType: String) {
            Log.d("Network", "Network: connected=$isConnected, type=$networkType")

            if (!isConnected) {
                showNetworkDisconnectedWarning()
            }
        }

        override fun onCallStatistics(stats: CallStatistics) {
            updateCallQualityIndicators(stats)
        }
    }

    private fun updateNetworkQualityUI(quality: EddysSipLibrary.NetworkQuality) {
        val qualityText = when {
            quality.score >= 0.8f -> "Excellent"
            quality.score >= 0.6f -> "Good"
            quality.score >= 0.4f -> "Fair"
            else -> "Poor"
        }

        binding.tvNetworkQuality.text = qualityText
        binding.tvLatency.text = "${quality.latency}ms"
        binding.tvPacketLoss.text = "${(quality.packetLoss * 100).toInt()}%"
        binding.tvJitter.text = "${quality.jitter}ms"
    }
}
```

## 🛡️ Manejo Avanzado de Errores

### Manejo Específico por Categoría

```kotlin
class ErrorHandler {

    fun handleSipError(error: SipError) {
        when (error.category) {
            ErrorCategory.NETWORK -> handleNetworkError(error)
            ErrorCategory.AUTHENTICATION -> handleAuthError(error)
            ErrorCategory.AUDIO -> handleAudioError(error)
            ErrorCategory.SIP_PROTOCOL -> handleProtocolError(error)
            ErrorCategory.WEBRTC -> handleWebRtcError(error)
            ErrorCategory.CONFIGURATION -> handleConfigError(error)
            ErrorCategory.TRANSLATION -> handleTranslationError(error)
            ErrorCategory.PERMISSION -> handlePermissionError(error)
        }
    }

    private fun handleNetworkError(error: SipError) {
        if (error.isRecoverable()) {
            // Mostrar diálogo con opción de reintentar
            showRetryDialog(
                title = "Network Error",
                message = error.getUserFriendlyMessage(),
                suggestions = error.getRecoverySuggestions(),
                onRetry = {
                    // Implementar lógica de reintento
                    retryLastOperation()
                }
            )
        } else {
            // Error crítico de red
            showCriticalErrorDialog(error.getUserFriendlyMessage())
        }
    }

    private fun handleAuthError(error: SipError) {
        // Redirigir a pantalla de configuración de cuenta
        showAccountConfigurationScreen(error.getUserFriendlyMessage())
    }

    private fun handleAudioError(error: SipError) {
        when (error.code) {
            ErrorCodes.AUDIO_PERMISSION_DENIED -> {
                requestAudioPermissions()
            }
            ErrorCodes.AUDIO_DEVICE_CHANGE_FAILED -> {
                showAudioDeviceSelectionDialog()
            }
            else -> {
                showGenericAudioError(error.getUserFriendlyMessage())
            }
        }
    }
}
```

### Recuperación Automática de Errores

```kotlin
class AutoRecoveryManager {

    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelay = 2000L

    private val recoveryListener = object : SipEventListener {
        override fun onError(error: SipError) {
            if (error.isRecoverable() && retryCount < maxRetries) {
                scheduleRetry(error)
            } else {
                notifyPermanentFailure(error)
            }
        }

        override fun onRegistrationSuccess(account: String, expiresIn: Int) {
            retryCount = 0 // Reset counter on success
        }

        override fun onCallConnected(callId: String, duration: Long) {
            retryCount = 0 // Reset counter on successful call
        }
    }

    private fun scheduleRetry(error: SipError) {
        retryCount++

        GlobalScope.launch {
            delay(retryDelay * retryCount) // Exponential backoff

            when (error.category) {
                ErrorCategory.NETWORK -> attemptNetworkRecovery()
                ErrorCategory.AUTHENTICATION -> attemptReregistration()
                else -> {
                    // No automatic recovery for other categories
                    notifyManualRecoveryRequired(error)
                }
            }
        }
    }

    private suspend fun attemptNetworkRecovery() {
        // Implementar lógica de recuperación de red
        EddysSipLibrary.getInstance().registerAccount(
            // Usar credenciales guardadas
            username = getStoredUsername(),
            password = getStoredPassword(),
            domain = getStoredDomain()
        )
    }
}
```

## 📱 Integración con UI

### Compose Integration

```kotlin
@Composable
fun CallScreen() {
    val sipLibrary = remember { EddysSipLibrary.getInstance() }
    val callState by sipLibrary.getCallStateFlow().collectAsState()
    val audioState by sipLibrary.getAudioStateFlow().collectAsState()

    LaunchedEffect(Unit) {
        // Agregar listener para Compose
        val listener = object : SipEventListener {
            override fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
                // Manejar llamada entrante en Compose
            }

            override fun onError(error: SipError) {
                // Mostrar error en Compose
            }
        }

        SipEventBusExtensions.addListener(listener)

        // Cleanup
        awaitCancellation()
        SipEventBusExtensions.removeListener(listener)
    }

    Column {
        CallStateIndicator(callState = callState)

        AudioControls(
            isMuted = audioState.isMuted,
            onMuteToggle = {
                sipLibrary.toggleMute()
            }
        )

        CallControls(
            callState = callState,
            onAccept = { sipLibrary.acceptCall() },
            onDecline = { sipLibrary.declineCall() },
            onHangup = { sipLibrary.endCall() }
        )
    }
}

@Composable
fun CallStateIndicator(callState: CallState) {
    val stateText = when (callState) {
        CallState.NONE -> "No call"
        CallState.INCOMING -> "Incoming call"
        CallState.CALLING -> "Calling..."
        CallState.CONNECTED -> "Connected"
        CallState.HOLDING -> "On hold"
        CallState.ENDED -> "Call ended"
        else -> "Unknown"
    }

    Text(
        text = stateText,
        style = MaterialTheme.typography.h6
    )
}
```

### Fragment/Activity Integration

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sipLibrary = EddysSipLibrary.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSipLibrary()
        observeStates()
        setupUI()
    }

    private fun setupSipLibrary() {
        lifecycleScope.launch {
            val config = sipConfig {
                domain("your-domain.com")
                webSocketUrl("wss://your-server.com/ws")
                enableLogs(BuildConfig.DEBUG)
            }

            sipLibrary.initialize(application, config)
        }
    }

    private fun observeStates() {
        // Observar estado de llamada
        sipLibrary.getCallStateFlow()
            .flowWithLifecycle(lifecycle)
            .onEach { callState ->
                updateUIForCallState(callState)
            }
            .launchIn(lifecycleScope)

        // Observar estado de registro
        sipLibrary.getRegistrationStateFlow()
            .flowWithLifecycle(lifecycle)
            .onEach { regState ->
                updateRegistrationStatus(regState)
            }
            .launchIn(lifecycleScope)
    }
}
```

## 🔧 Configuración Avanzada

### Configuración Completa

```kotlin
val advancedConfig = sipConfig {
    domain("your-domain.com")
    webSocketUrl("wss://your-server.com/ws")
    userAgent("MyVoIPApp/2.0.0 (Android)")
    enableLogs(true)
    autoReconnect(true)
    customHeaders(mapOf(
        "X-App-Version" to BuildConfig.VERSION_NAME,
        "X-Device-ID" to getDeviceId(),
        "X-Platform" to "Android ${Build.VERSION.RELEASE}"
    ))
}

// Configurar ringtones personalizados
val ringtoneConfig = RingtoneConfig(
    enableIncomingRingtone = true,
    enableOutgoingRingtone = true,
    enableVibration = true,
    volume = 0.8f,
    customIncomingRingtoneUri = getCustomRingtoneUri(),
    customOutgoingRingtoneUri = getCustomOutgoingUri()
)

val fullConfig = EddysSipLibrary.SipConfig(
    defaultDomain = "your-domain.com",
    webSocketUrl = "wss://your-server.com/ws",
    userAgent = "MyVoIPApp/2.0.0",
    enableLogs = true,
    enableAutoReconnect = true,
    pingIntervalMs = 30000L,
    registrationExpiresSeconds = 3600,
    autoEnterPushOnBackground = true,
    autoExitPushOnForeground = true,
    pushReconnectDelayMs = 2000L,
    preferredAudioDevice = EddysSipLibrary.AudioDeviceType.EARPIECE,
    enableEchoCancellation = true,
    enableNoiseSuppression = true,
    autoAcceptDelay = 0L,
    callTimeoutSeconds = 60,
    ringtoneConfig = ringtoneConfig,
    customHeaders = mapOf(
        "X-App-Version" to BuildConfig.VERSION_NAME
    )
)
```

## 🔒 Permisos Requeridos

Agrega estos permisos a tu `AndroidManifest.xml`:

```xml
<!-- Permisos básicos -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <!-- Para vibración en ringtones -->
<uses-permission android:name="android.permission.VIBRATE" />

    <!-- Para llamadas telefónicas (opcional) -->
<uses-permission android:name="android.permission.CALL_PHONE" />

    <!-- Para Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- Para push notifications -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Para audio focus -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

## 🚀 Mejores Prácticas

### 1. Inicialización

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Inicializar en Application para disponibilidad global
        lifecycleScope.launch {
            SipLibraryExtensions.initializeSipLibrary(
                application = this@MyApplication,
                domain = getString(R.string.sip_domain),
                webSocketUrl = getString(R.string.sip_websocket_url)
            )
        }
    }
}
```

### 2. Manejo de Lifecycle

```kotlin
class CallActivity : AppCompatActivity() {

    private var sipListener: SipEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crear listener
        sipListener = createSipListener()

        // Agregar listener
        lifecycleScope.launch {
            sipListener?.let {
                SipEventBusExtensions.addListener(it)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // IMPORTANTE: Remover listener para evitar memory leaks
        lifecycleScope.launch {
            sipListener?.let {
                SipEventBusExtensions.removeListener(it)
            }
        }
    }
}
```

### 3. Manejo de Errores Consistente

```kotlin
class SipOperations {

    suspend fun performSipOperation(operation: suspend () -> Result<Unit>) {
        ErrorExtensions.handleSipResult(
            result = operation(),
            onSuccess = {
                // Operación exitosa
                showSuccessMessage()
            },
            onError = { error ->
                when {
                    error.isRecoverable() -> {
                        showRetryDialog(error.getUserFriendlyMessage()) {
                            // Reintentar operación
                            lifecycleScope.launch {
                                performSipOperation(operation)
                            }
                        }
                    }
                    else -> {
                        showCriticalError(error.getUserFriendlyMessage())
                    }
                }
            }
        )
    }
}
```

### 4. Configuración Basada en Entorno

```kotlin
object SipConfiguration {

    fun createConfig(context: Context): EddysSipLibrary.SipConfig {
        return when (BuildConfig.BUILD_TYPE) {
            "debug" -> createDebugConfig()
            "staging" -> createStagingConfig()
            "release" -> createProductionConfig()
            else -> createDefaultConfig()
        }
    }

    private fun createDebugConfig() = sipConfig {
        domain("dev-sip.example.com")
        webSocketUrl("wss://dev-sip.example.com/ws")
        enableLogs(true)
        userAgent("MyApp-Debug/1.0.0")
    }

    private fun createProductionConfig() = sipConfig {
        domain("sip.example.com")
        webSocketUrl("wss://sip.example.com/ws")
        enableLogs(false)
        userAgent("MyApp/1.0.0")
    }
}
```

## 🌟 Características Avanzadas

### Push Notifications para Llamadas

```kotlin
class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (remoteMessage.data["type"] == "incoming_call") {
            handleIncomingCallPush(remoteMessage.data)
        }
    }

    private fun handleIncomingCallPush(data: Map<String, String>) {
        val callerNumber = data["caller_number"] ?: return
        val callId = data["call_id"] ?: return

        // Despertar la aplicación y preparar para llamada entrante
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("caller_number", callerNumber)
            putExtra("call_id", callId)
            putExtra("from_push", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        startActivity(intent)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Actualizar token en la biblioteca SIP
        lifecycleScope.launch {
            EddysSipLibrary.getInstance().updatePushConfiguration(
                token = token,
                provider = "fcm"
            )
        }
    }
}
```

### Grabación de Llamadas

```kotlin
class CallRecordingManager {

    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null

    fun startRecording(callId: String): Result<String> {
        return try {
            if (isRecording) {
                return Result.failure(SipLibraryException(
                    "Recording already in progress",
                    ErrorCodes.UNEXPECTED_ERROR,
                    ErrorCategory.AUDIO
                ))
            }

            val outputFile = createRecordingFile(callId)

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Result.success(outputFile.absolutePath)

        } catch (e: Exception) {
            Result.failure(SipLibraryException(
                "Failed to start recording",
                ErrorCodes.AUDIO_DEVICE_CHANGE_FAILED,
                ErrorCategory.AUDIO,
                e
            ))
        }
    }

    fun stopRecording(): Result<Unit> {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SipLibraryException(
                "Failed to stop recording",
                ErrorCodes.AUDIO_DEVICE_CHANGE_FAILED,
                ErrorCategory.AUDIO,
                e
            ))
        }
    }
}
```

## 📈 Performance y Optimización

### Optimización de Memoria

```kotlin
class SipMemoryManager {

    fun optimizeForLowMemory() {
        // Configurar biblioteca para dispositivos con poca memoria
        val config = sipConfig {
            domain("your-domain.com")
            webSocketUrl("wss://your-server.com/ws")
            enableLogs(false) // Deshabilitar logs en producción
            // Reducir intervalos para ahorrar batería
            customHeaders(mapOf(
                "X-Memory-Mode" to "low"
            ))
        }
    }

    fun cleanupResources() {
        // Limpiar listeners no utilizados
        lifecycleScope.launch {
            SipEventBusExtensions.clearListeners()
        }

        // Limpiar traducción si no se usa
        if (!TranslationExtensions.isTranslationAvailable()) {
            TranslationManager.getInstance().dispose()
        }
    }
}
```

### Monitoreo de Performance

```kotlin
class PerformanceMonitor {

    private val performanceListener = object : SipEventListener {
        override fun onCallStatistics(stats: CallStatistics) {
            analyzeCallQuality(stats)
        }

        override fun onNetworkQuality(quality: EddysSipLibrary.NetworkQuality) {
            if (quality.score < 0.5f) {
                suggestQualityImprovements(quality)
            }
        }
    }

    private fun analyzeCallQuality(stats: CallStatistics) {
        when {
            stats.packetsLost > 100 -> {
                recommendNetworkOptimization()
            }
            stats.jitter > 50 -> {
                recommendAudioOptimization()
            }
            stats.rtt > 200 -> {
                recommendServerOptimization()
            }
        }
    }
}
```

## 🧪 Testing

### Unit Tests

```kotlin
class SipLibraryTest {

    @Test
    fun `test library initialization`() = runTest {
        val application = mock<Application>()
        val config = sipConfig {
            domain("test.com")
            webSocketUrl("wss://test.com/ws")
        }

        val result = EddysSipLibrary.getInstance().initialize(application, config)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `test call flow`() = runTest {
        // Setup
        initializeLibrary()

        // Test make call
        val callResult = EddysSipLibrary.getInstance().makeCall("+1234567890")
        assertTrue(callResult.isSuccess)

        // Test call state
        assertEquals(CallState.CALLING, EddysSipLibrary.getInstance().getCurrentCallState())
    }
}
```

### Integration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class SipIntegrationTest {

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Setup real SIP library
    }

    @Test
    fun testFullCallFlow() {
        // Test complete call flow with real SIP server
    }
}
```

## 🔧 Solución de Problemas

### Problemas Comunes

1. **Error de conexión WebSocket**
   ```kotlin
   // Verificar configuración de red
   val healthReport = EddysSipLibrary.getInstance().getSystemHealthReport()
   Log.d("Debug", healthReport)
   ```

2. **Problemas de audio**
   ```kotlin
   // Diagnosticar audio
   val audioDiagnosis = EddysSipLibrary.getInstance().diagnoseAudioIssues()
   Log.d("Audio", audioDiagnosis)
   ```

3. **Errores de traducción**
   ```kotlin
   // Verificar estado de traducción
   val translationState = TranslationExtensions.getTranslationState()
   Log.d("Translation", "State: $translationState")
   ```

### Debug Avanzado

```kotlin
class SipDebugger {

    fun enableVerboseLogging() {
        val debugListener = object : SipEventListener {
            override fun onSipMessageReceived(message: String, messageType: String) {
                Log.v("SIP_MSG_IN", "$messageType: $message")
            }

            override fun onSipMessageSent(message: String, messageType: String) {
                Log.v("SIP_MSG_OUT", "$messageType: $message")
            }

            override fun onError(error: SipError) {
                Log.e("SIP_ERROR", "Code: ${error.code}, Message: ${error.message}")
                Log.e("SIP_ERROR", "Category: ${error.category}")
                Log.e("SIP_ERROR", "Recoverable: ${error.isRecoverable()}")
                Log.e("SIP_ERROR", "Suggestions: ${error.getRecoverySuggestions()}")
            }
        }

        lifecycleScope.launch {
            SipEventBusExtensions.addListener(debugListener)
        }
    }
}
```

## 📚 API Reference Completa

### Principales Clases

- **`EddysSipLibrary`** - Clase principal de la biblioteca
- **`GlobalEventBus`** - Sistema de eventos global
- **`TranslationManager`** - Gestor de traducción independiente
- **`SipEventListener`** - Interface para eventos
- **`SipError`** - Manejo de errores avanzado

### Extensiones Útiles

- **`SipEventBusExtensions`** - Helpers para eventos
- **`TranslationExtensions`** - Helpers para traducción
- **`ErrorExtensions`** - Helpers para errores
- **`SipLibraryExtensions`** - Helpers generales

### DSL Builders

- **`sipConfig { }`** - Constructor de configuración SIP
- **`translationConfig { }`** - Constructor de configuración de traducción

## 🎯 Roadmap

### v3.1.0
- [ ] Soporte para video llamadas
- [ ] Mejoras en calidad de audio
- [ ] Más idiomas de traducción

### v3.2.0
- [ ] Conferencias múltiples
- [ ] Grabación de llamadas mejorada
- [ ] Analytics avanzados

### v4.0.0
- [ ] Compose completo
- [ ] Multiplatform (iOS)
- [ ] IA integrada

## 📄 Licencia

```
Copyright 2024 Eddys Larez

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Por favor, lee las [guías de contribución](CONTRIBUTING.md) antes de enviar un PR.

## 📞 Soporte

- **Email**: [eddys.larez@example.com](mailto:eddys.larez@example.com)
- **Documentation**: [https://docs.eddyslarez.com/siplibrary](https://docs.eddyslarez.com/siplibrary)
- **Issues**: [GitHub Issues](https://github.com/eddyslarez/siplibrary/issues)

---

**EddysSipLibrary v3.0.0** - La biblioteca SIP más completa y fácil de usar para Android con traducción en tiempo real integrada.