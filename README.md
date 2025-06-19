# EddysSipLibrary - Biblioteca SIP/VoIP Optimizada para Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org)
[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Version](https://img.shields.io/badge/Version-2.0.0-green.svg)](https://github.com/eddyslarez/sip-library)

Una biblioteca SIP/VoIP completa y optimizada para Android desarrollada por **Eddys Larez**, que proporciona funcionalidades avanzadas de comunicación con soporte para WebRTC, WebSocket y gestión inteligente de audio.

## 🚀 Características Principales

### ✨ Funcionalidades Principales
- **Llamadas SIP/VoIP** con calidad HD
- **WebRTC** integrado para audio de alta calidad
- **WebSocket** para comunicación en tiempo real
- **Gestión inteligente de dispositivos de audio**
- **Sistema de ringtones configurable**
- **Push notifications** para llamadas entrantes
- **Detección automática de dispositivos Bluetooth**
- **DTMF** (tonos de marcación) durante llamadas

### 🎯 Arquitectura Optimizada v2.0
- **Interfaces modulares** - Sistema de listeners múltiples por categoría
- **StateFlow reactivo** - Estado reactivo con Kotlin Flow
- **Gestión automática de ringtones** - Control inteligente de sonidos
- **Detección mejorada de audio** - Reconocimiento automático de dispositivos
- **Corrutinas estructuradas** - Concurrencia optimizada
- **Gestión de ciclo de vida** - Manejo automático de recursos

### 🔧 Nuevas Mejoras v2.0

#### Sistema de Listeners Modulares
```kotlin
// Antes: Un solo listener monolítico
sipLibrary.setEventListener(object : SipEventListener { ... })

// Ahora: Múltiples listeners específicos
sipLibrary.registerEventListener<CallEventListener>(callListener)
sipLibrary.registerEventListener<AudioEventListener>(audioListener)
sipLibrary.registerEventListener<ConnectivityEventListener>(networkListener)
```

#### StateFlow Reactivo
```kotlin
// Observar estados de manera reactiva
sipLibrary.getCallStateFlow().collect { state ->
    when (state) {
        CallState.INCOMING -> showIncomingCallUI()
        CallState.CONNECTED -> showActiveCallUI()
        CallState.ENDED -> showCallEndedUI()
    }
}

// Estados combinados
sipLibrary.getCanMakeCallFlow().collect { canCall ->
    callButton.isEnabled = canCall
}
```

#### Gestión Automática de Ringtones
```kotlin
// Configuración avanzada de ringtones
val ringtoneConfig = RingtoneConfig(
    enableIncomingRingtone = true,
    enableOutgoingRingtone = true,
    enableVibration = true,
    incomingRingtoneTimeoutMs = 30000L,
    customIncomingRingtone = customRingtoneUri
)

sipLibrary.updateRingtoneConfig(ringtoneConfig)
```

## 📦 Instalación

### Gradle (Project level)
```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Gradle (Module level)
```kotlin
dependencies {
    implementation 'com.github.eddyslarez:sip-library:2.0.0'
}
```

### Permisos Requeridos
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## 🛠️ Uso Básico

### Inicialización

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var sipLibrary: EddysSipLibraryOptimized
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configuración de la biblioteca
        val config = EddysSipLibraryOptimized.SipConfig(
            defaultDomain = "tu-servidor-sip.com",
            webSocketUrl = "wss://tu-servidor-sip.com:443",
            userAgent = "MiApp/1.0.0",
            ringtoneConfig = RingtoneConfig(
                enableIncomingRingtone = true,
                enableOutgoingRingtone = true,
                enableVibration = true
            )
        )
        
        // Crear listeners específicos
        val listeners = listOf(
            createCallEventListener(),
            createAudioEventListener(),
            createRegistrationEventListener()
        )
        
        // Inicializar biblioteca
        sipLibrary = EddysSipLibraryOptimized.getInstance()
        sipLibrary.initialize(
            application = application,
            config = config,
            initialListeners = listeners
        )
    }
}
```

### Listeners Específicos

```kotlin
// Listener para eventos de llamadas
private fun createCallEventListener() = object : CallEventListener {
    override fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
        showIncomingCallNotification(callerNumber, callerName)
    }
    
    override fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) {
        updateCallUI(newState)
    }
    
    override fun onCallConnected(callId: String, duration: Long) {
        startCallTimer()
    }
    
    override fun onCallDisconnected(callId: String, reason: CallEndReason, duration: Long) {
        hideCallUI()
        logCallDuration(duration)
    }
}

// Listener para eventos de audio
private fun createAudioEventListener() = object : AudioEventListener {
    override fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {
        updateAudioDeviceIndicator(newDevice)
    }
    
    override fun onAudioDevicesAvailable(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {
        populateAudioDeviceMenu(outputDevices)
    }
    
    override fun onMuteStateChanged(isMuted: Boolean) {
        updateMuteButton(isMuted)
    }
}

// Listener para eventos de registro
private fun createRegistrationEventListener() = object : RegistrationEventListener {
    override fun onRegistrationSuccess(account: String, expiresIn: Int) {
        showRegistrationStatus("Conectado")
    }
    
    override fun onRegistrationFailed(account: String, reason: String) {
        showRegistrationError(reason)
    }
}
```

### Observación de Estados con StateFlow

```kotlin
class CallViewModel : ViewModel() {
    private val sipLibrary = EddysSipLibraryOptimized.getInstance()
    
    // Estados reactivos
    val callState = sipLibrary.getCallStateFlow()
    val audioState = sipLibrary.getAudioStateFlow()
    val canMakeCall = sipLibrary.getCanMakeCallFlow()
    val currentCall = sipLibrary.getCurrentCallFlow()
    
    // Estado combinado personalizado
    val callInfo = combine(
        callState,
        currentCall,
        audioState
    ) { state, call, audio ->
        CallUIState(
            isActive = state != CallState.NONE,
            duration = call?.duration ?: 0,
            isMuted = audio.isMuted,
            currentDevice = audio.currentOutputDevice?.name
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = CallUIState()
    )
}

// En tu Composable
@Composable
fun CallScreen(viewModel: CallViewModel) {
    val callInfo by viewModel.callInfo.collectAsState()
    val canMakeCall by viewModel.canMakeCall.collectAsState()
    
    Column {
        // UI reactiva basada en estados
        if (callInfo.isActive) {
            ActiveCallUI(
                duration = callInfo.duration,
                isMuted = callInfo.isMuted,
                deviceName = callInfo.currentDevice
            )
        }
        
        Button(
            onClick = { makeCall() },
            enabled = canMakeCall
        ) {
            Text("Llamar")
        }
    }
}
```

## 📞 Gestión de Llamadas

### Registro de Cuenta SIP

```kotlin
// Registro básico
sipLibrary.registerAccount(
    username = "usuario",
    password = "contraseña",
    domain = "servidor-sip.com"
)

// Registro con push notifications
sipLibrary.registerAccount(
    username = "usuario",
    password = "contraseña",
    domain = "servidor-sip.com",
    pushToken = firebaseToken,
    pushProvider = "fcm"
)
```

### Realizar Llamadas

```kotlin
// Llamada básica
sipLibrary.makeCall("1234567890")

// Llamada con headers personalizados
sipLibrary.makeCall(
    phoneNumber = "1234567890",
    customHeaders = mapOf(
        "X-Custom-Header" to "valor",
        "X-Priority" to "high"
    )
)
```

### Gestionar Llamadas Entrantes

```kotlin
// En el listener de llamadas
override fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
    // El ringtone se reproduce automáticamente
    showIncomingCallDialog(
        number = callerNumber,
        name = callerName,
        onAccept = { sipLibrary.acceptCall() },
        onDecline = { sipLibrary.declineCall() }
    )
}

// Aceptar llamada
sipLibrary.acceptCall() // Ringtone se detiene automáticamente

// Rechazar llamada
sipLibrary.declineCall() // Ringtone se detiene automáticamente
```

## 🔊 Gestión de Audio

### Dispositivos de Audio

```kotlin
// Obtener dispositivos disponibles (automático con StateFlow)
sipLibrary.getAudioStateFlow().collect { audioState ->
    val availableDevices = audioState.availableOutputDevices
    updateDeviceList(availableDevices)
}

// Cambiar dispositivo de audio
val bluetoothDevice = audioState.availableOutputDevices
    .find { it.type == AudioDeviceType.BLUETOOTH }

bluetoothDevice?.let { device ->
    sipLibrary.changeAudioDevice(device)
}

// Alternar silencio
val isMuted = sipLibrary.toggleMute()
```

### Configuración Avanzada de Audio

```kotlin
// Listener detallado de audio
val audioListener = object : AudioEventListener {
    override fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {
        log("Audio cambiado de ${oldDevice?.name} a ${newDevice.name}")
    }
    
    override fun onAudioDevicesAvailable(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {
        // Dispositivos detectados automáticamente:
        // - Auricular (Earpiece)
        // - Altavoz (Speaker)  
        // - Auriculares con cable (Wired Headset)
        // - Dispositivos Bluetooth (Bluetooth Headset)
        // - Dispositivos USB (USB Headset)
        
        outputDevices.forEach { device ->
            log("Dispositivo disponible: ${device.name} (${device.type})")
        }
    }
    
    override fun onAudioQualityChanged(quality: AudioQuality) {
        if (quality.score < 0.5f) {
            showAudioQualityWarning()
        }
    }
}
```

## 🎵 Sistema de Ringtones Mejorado

### Configuración de Ringtones

```kotlin
val ringtoneConfig = RingtoneConfig(
    enableIncomingRingtone = true,        // Activar ringtone entrante
    enableOutgoingRingtone = true,        // Activar ringtone saliente
    enableNotificationSounds = true,      // Sonidos de notificación
    enableVibration = true,               // Vibración
    incomingVolume = 1.0f,               // Volumen entrante (0.0-1.0)
    outgoingVolume = 0.7f,               // Volumen saliente (0.0-1.0)
    incomingRingtoneTimeoutMs = 30000L,  // Timeout automático
    customIncomingRingtone = customUri,   // Ringtone personalizado
    customCallEndedSound = endSoundUri   // Sonido de finalización
)

// Aplicar configuración
sipLibrary.updateRingtoneConfig(ringtoneConfig)
```

### Control Manual de Ringtones

```kotlin
// La biblioteca maneja automáticamente los ringtones según el estado:
// - INCOMING -> Reproduce ringtone entrante automáticamente
// - OUTGOING -> Reproduce ringtone saliente automáticamente  
// - CONNECTED -> Detiene todos los ringtones automáticamente
// - ENDED -> Detiene ringtones y reproduce sonido de finalización

// Si necesitas control manual:
val ringtoneController = RingtoneController(context, config.ringtoneConfig)

// Reproducir manualmente
ringtoneController.playIncomingRingtone()
ringtoneController.playOutgoingRingtone()

// Detener manualmente
ringtoneController.stopAllRingtones()

// Estados reactivos de ringtones
ringtoneController.isPlayingIncoming.collect { isPlaying ->
    updateRingtoneIndicator(isPlaying)
}
```

## 📱 DTMF (Tonos de Marcación)

```kotlin
// Enviar dígito DTMF individual
val success = sipLibrary.sendDtmf('1', duration = 160)

// Enviar secuencia DTMF
val success = sipLibrary.sendDtmfSequence("123*456#", duration = 160)

// Listener para confirmación
val callListener = object : CallEventListener {
    override fun onDtmfSent(callId: String, digit: Char, success: Boolean) {
        if (success) {
            log("DTMF '$digit' enviado correctamente")
            // Reproducir sonido de confirmación automáticamente
        }
    }
}
```

## 🔄 Gestión de Estados Avanzada

### Estados Principales

```kotlin
// Estados individuales
sipLibrary.getCallStateFlow() // CallState: NONE, INCOMING, OUTGOING, CONNECTED, etc.
sipLibrary.getRegistrationStateFlow() // RegistrationState: NONE, OK, FAILED, etc.
sipLibrary.getAudioStateFlow() // AudioState: dispositivos, volumen, calidad
sipLibrary.getNetworkStateFlow() // NetworkState: conectividad, calidad de red

// Estados combinados útiles
sipLibrary.getIsCallActiveFlow() // Boolean: hay llamada activa
sipLibrary.getCanMakeCallFlow() // Boolean: puede realizar llamadas

// Información detallada
sipLibrary.getCallerInfoFlow() // CallerInfo?: información del llamador
sipLibrary.getCurrentCallFlow() // CallInfo?: información de llamada actual
```

### Casos de Uso Avanzados

```kotlin
class AdvancedCallManager {
    private val sipLibrary = EddysSipLibraryOptimized.getInstance()
    
    // Combinación de múltiples estados
    val callStatus = combine(
        sipLibrary.getCallStateFlow(),
        sipLibrary.getCurrentCallFlow(),
        sipLibrary.getAudioStateFlow(),
        sipLibrary.getNetworkStateFlow()
    ) { callState, currentCall, audioState, networkState ->
        CallStatus(
            state = callState,
            duration = currentCall?.duration ?: 0,
            quality = networkState.quality?.score ?: 0f,
            audioDevice = audioState.currentOutputDevice?.name ?: "Unknown",
            isMuted = audioState.isMuted,
            isNetworkStable = networkState.isConnected
        )
    }
    
    // Reacciones automáticas a cambios de estado
    init {
        // Auto-manage audio device based on availability
        sipLibrary.getAudioStateFlow().collect { audioState ->
            if (audioState.availableOutputDevices.any { it.type == AudioDeviceType.BLUETOOTH }) {
                // Cambiar automáticamente a Bluetooth si está disponible
                val bluetoothDevice = audioState.availableOutputDevices
                    .find { it.type == AudioDeviceType.BLUETOOTH }
                bluetoothDevice?.let { sipLibrary.changeAudioDevice(it) }
            }
        }
    }
}
```

## ⚡ Optimizaciones de Rendimiento

### Gestión de Listeners

```kotlin
// Registrar múltiples listeners de manera eficiente
class CallActivity : ComponentActivity() {
    private val callListener = object : CallEventListener { /* ... */ }
    private val audioListener = object : AudioEventListener { /* ... */ }
    
    override fun onResume() {
        super.onResume()
        // Registrar listeners solo cuando sea necesario
        sipLibrary.registerEventListener(callListener)
        sipLibrary.registerEventListener(audioListener)
    }
    
    override fun onPause() {
        super.onPause()
        // Desregistrar para evitar memory leaks
        sipLibrary.unregisterEventListener(callListener)
        sipLibrary.unregisterEventListener(audioListener)
    }
}
```

### Estadísticas y Monitoreo

```kotlin
// Obtener estadísticas de rendimiento
val stats = sipLibrary.getEventStatistics()
stats.forEach { (event, count) ->
    log("Evento $event ejecutado $count veces")
}

// Conteo de listeners activos
val listenerCounts = sipLibrary.getListenerCounts()
log("Listeners activos: $listenerCounts")

// Reporte completo del sistema
val healthReport = sipLibrary.getSystemHealthReport()
log(healthReport)
```

## 🐛 Depuración y Diagnóstico

### Logging Avanzado

```kotlin
// Listener para debugging
val debugListener = object : ErrorEventListener {
    override fun onError(error: SipError) {
        log("Error SIP [${error.code}]: ${error.message}")
        when (error.category) {
            ErrorCategory.NETWORK -> handleNetworkError(error)
            ErrorCategory.AUDIO -> handleAudioError(error)
            ErrorCategory.AUTHENTICATION -> handleAuthError(error)
            else -> handleGenericError(error)
        }
    }
    
    override fun onDebugInfo(info: String, level: DebugLevel) {
        when (level) {
            DebugLevel.ERROR -> Log.e("SipLibrary", info)
            DebugLevel.WARNING -> Log.w("SipLibrary", info)
            DebugLevel.INFO -> Log.i("SipLibrary", info)
            DebugLevel.DEBUG -> Log.d("SipLibrary", info)
            DebugLevel.VERBOSE -> Log.v("SipLibrary", info)
        }
    }
}
```

### Reporte de Salud del Sistema

```kotlin
// Generar reporte completo
val healthReport = sipLibrary.getSystemHealthReport()

// El reporte incluye:
// - Estado de inicialización
// - Listeners activos por categoría
// - Estado actual de llamadas
// - Estado de registro SIP
// - Conectividad de red
// - Dispositivos de audio detectados
// - Estado de ringtones
// - Estadísticas de eventos
```

## 🔧 Configuración Avanzada

### Configuración Completa

```kotlin
val advancedConfig = EddysSipLibraryOptimized.SipConfig(
    // Configuración básica
    defaultDomain = "mi-servidor.com",
    webSocketUrl = "wss://mi-servidor.com:443",
    userAgent = "MiApp/2.0.0 Android",
    enableLogs = BuildConfig.DEBUG,
    
    // Reconexión automática
    enableAutoReconnect = true,
    pingIntervalMs = 30000L,
    registrationExpiresSeconds = 3600,
    
    // Push notifications
    autoEnterPushOnBackground = true,
    autoExitPushOnForeground = true,
    pushReconnectDelayMs = 2000L,
    
    // Configuración de audio
    autoSelectAudioDevice = true,
    preferredAudioDevice = AudioDeviceType.BLUETOOTH,
    enableEchoCancellation = true,
    enableNoiseSuppression = true,
    
    // Configuración de ringtones
    ringtoneConfig = RingtoneConfig(
        enableIncomingRingtone = true,
        enableOutgoingRingtone = true,
        enableVibration = true,
        incomingRingtoneTimeoutMs = 45000L,
        customIncomingRingtone = Uri.parse("android.resource://com.miapp/raw/custom_ringtone")
    ),
    
    // Configuración de llamadas
    callTimeoutSeconds = 60,
    maxConcurrentCalls = 1,
    enableCallRecording = false,
    
    // Headers personalizados
    customHeaders = mapOf(
        "X-App-Version" to "2.0.0",
        "X-Device-Type" to "Android"
    ),
    
    // Calidad y estadísticas
    enableAdaptiveQuality = true,
    enableCallStatistics = true,
    statisticsUpdateIntervalMs = 5000L
)
```

## 📊 Ejemplo Completo de Implementación

### ViewModel con StateFlow

```kotlin
class SipViewModel : ViewModel() {
    private val sipLibrary = EddysSipLibraryOptimized.getInstance()
    
    // Estados públicos
    val callState = sipLibrary.getCallStateFlow()
    val registrationState = sipLibrary.getRegistrationStateFlow()
    val audioState = sipLibrary.getAudioStateFlow()
    val canMakeCall = sipLibrary.getCanMakeCallFlow()
    
    // UI State combinado
    val uiState = combine(
        callState,
        registrationState,
        audioState,
        sipLibrary.getCurrentCallFlow()
    ) { call, registration, audio, currentCall ->
        SipUiState(
            isRegistered = registration == RegistrationState.OK,
            isCallActive = call != CallState.NONE,
            callDuration = currentCall?.duration ?: 0,
            isMuted = audio.isMuted,
            currentAudioDevice = audio.currentOutputDevice?.name,
            availableAudioDevices = audio.availableOutputDevices,
            canMakeCall = registration == RegistrationState.OK && call == CallState.NONE
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SipUiState()
    )
    
    // Listeners
    private val callListener = object : CallEventListener {
        override fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
            // Manejar llamada entrante
            _incomingCall.value = IncomingCallInfo(callerNumber, callerName, callId)
        }
        
        override fun onCallConnected(callId: String, duration: Long) {
            startCallTimer()
        }
        
        override fun onCallDisconnected(callId: String, reason: CallEndReason, duration: Long) {
            stopCallTimer()
            _incomingCall.value = null
        }
    }
    
    private val audioListener = object : AudioEventListener {
        override fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {
            Log.d("SipViewModel", "Audio device changed to: ${newDevice.name}")
        }
    }
    
    init {
        // Registrar listeners
        sipLibrary.registerEventListener(callListener)
        sipLibrary.registerEventListener(audioListener)
    }
    
    // Funciones públicas
    fun makeCall(number: String) {
        sipLibrary.makeCall(number)
    }
    
    fun acceptCall() {
        sipLibrary.acceptCall()
    }
    
    fun declineCall() {
        sipLibrary.declineCall()
    }
    
    fun endCall() {
        sipLibrary.endCall()
    }
    
    fun toggleMute() {
        sipLibrary.toggleMute()
    }
    
    fun changeAudioDevice(device: AudioDevice) {
        sipLibrary.changeAudioDevice(device)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Limpiar listeners
        sipLibrary.unregisterEventListener(callListener)
        sipLibrary.unregisterEventListener(audioListener)
    }
}

data class SipUiState(
    val isRegistered: Boolean = false,
    val isCallActive: Boolean = false,
    val callDuration: Long = 0,
    val isMuted: Boolean = false,
    val currentAudioDevice: String? = null,
    val availableAudioDevices: List<AudioDevice> = emptyList(),
    val canMakeCall: Boolean = false
)
```

### UI con Jetpack Compose

```kotlin
@Composable
fun SipScreen(viewModel: SipViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Estado de registro
        StatusCard(
            title = "Estado SIP",
            status = if (uiState.isRegistered) "Conectado" else "Desconectado",
            isConnected = uiState.isRegistered
        )
        
        // Información de llamada activa
        if (uiState.isCallActive) {
            CallCard(
                duration = uiState.callDuration,
                isMuted = uiState.isMuted,
                audioDevice = uiState.currentAudioDevice,
                onMuteToggle = { viewModel.toggleMute() },
                onEndCall = { viewModel.endCall() }
            )
        }
        
        // Selector de dispositivo de audio
        AudioDeviceSelector(
            devices = uiState.availableAudioDevices,
            currentDevice = uiState.currentAudioDevice,
            onDeviceSelected = { device -> viewModel.changeAudioDevice(device) }
        )
        
        // Teclado para llamadas
        DialPad(
            enabled = uiState.canMakeCall,
            onCall = { number -> viewModel.makeCall(number) }
        )
    }
}

@Composable
private fun CallCard(
    duration: Long,
    isMuted: Boolean,
    audioDevice: String?,
    onMuteToggle: () -> Unit,
    onEndCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Llamada Activa",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodyLarge
            )
            
            Text(
                text = "Audio: ${audioDevice ?: "Desconocido"}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = onMuteToggle,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Activar micrófono" else "Silenciar"
                    )
                }
                
                IconButton(
                    onClick = onEndCall,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Colgar"
                    )
                }
            }
        }
    }
}
```

## 📈 Migración desde v1.0

Si estás migrando desde la versión anterior:

### Cambios Principales

```kotlin
// v1.0 - Listener único
sipLibrary.setEventListener(object : SipEventListener {
    override fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) { }
    override fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) { }
    // ... muchos más métodos
})

// v2.0 - Listeners específicos
sipLibrary.registerEventListener<CallEventListener>(object : CallEventListener {
    override fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) { }
})

sipLibrary.registerEventListener<AudioEventListener>(object : AudioEventListener {
    override fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) { }
})
```

### Nuevas Características

1. **StateFlow reactivo** - Estados observables con Flow
2. **Gestión automática de ringtones** - No más control manual
3. **Detección mejorada de audio** - Reconocimiento automático de dispositivos
4. **Interfaces modulares** - Listeners específicos por categoría
5. **Mejor gestión de recursos** - Limpieza automática de memoria

## 🤝 Contribución

Las contribuciones son bienvenidas. Por favor:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## 📝 Licencia

Este proyecto está licenciado bajo la Licencia Apache 2.0 - mira el archivo [LICENSE](LICENSE) para más detalles.

## 👨‍💻 Autor

**Eddys Larez** - [GitHub](https://github.com/eddyslarez)

## 🙏 Agradecimientos

- [WebRTC KMP](https://github.com/shepeliev/webrtc-kmp) por el soporte WebRTC multiplataforma
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) por la implementación WebSocket
- Comunidad Android por el feedback y sugerencias

## 📞 Soporte

Para soporte técnico:
- Crea un [Issue](https://github.com/eddyslarez/sip-library/issues) en GitHub
- Consulta la [documentación completa](https://github.com/eddyslarez/sip-library/wiki)
- Revisa los [ejemplos](https://github.com/eddyslarez/sip-library/tree/main/examples)

---

⭐ **¡Si te gusta el proyecto, no olvides darle una estrella en GitHub!** ⭐