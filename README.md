# EddysSipLibrary - Biblioteca SIP/VoIP para Android

Una biblioteca completa para implementar funcionalidad SIP/VoIP en aplicaciones Android con soporte para WebRTC, WebSocket, y manejo automático de estados.

## 🚀 Características Principales

### ✅ Gestión Completa de Eventos
- **Estados de Registro**: Monitoreo en tiempo real del estado de registro SIP
- **Estados de Llamada**: Seguimiento completo del ciclo de vida de las llamadas
- **Eventos de Audio**: Detección y cambio automático de dispositivos de audio
- **Conectividad**: Monitoreo de estado de red y WebSocket
- **Modo Push**: Transición automática entre foreground y push mode

### ✅ Configuración Automática
- **Push Automático**: Cambio automático a modo push cuando la app pasa a segundo plano
- **Reconexión Automática**: Reconexión inteligente en caso de pérdida de conectividad
- **Selección de Audio**: Selección automática del mejor dispositivo de audio disponible
- **User Agent Dinámico**: Cambio dinámico de user agent según el estado de la aplicación

### ✅ Audio Avanzado
- **Múltiples Dispositivos**: Soporte para earpiece, speaker, Bluetooth, y headsets
- **Cambio Automático**: Detección y cambio automático entre dispositivos
- **Calidad de Audio**: Cancelación de eco y supresión de ruido
- **DTMF**: Soporte completo para tonos DTMF

## 📱 Instalación

### Gradle (build.gradle.kts)
```kotlin
dependencies {
    implementation("com.github.eddyslarez:sip-library:1.0.0")
}
```

### Permisos Requeridos
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## 🛠️ Uso Básico

### 1. Inicialización

```kotlin
class MyApplication : Application() {
    
    private lateinit var sipLibrary: EddysSipLibrary
    
    override fun onCreate() {
        super.onCreate()
        
        // Configuración completa
        val config = EddysSipLibrary.SipConfig(
            defaultDomain = "mi-servidor-sip.com",
            webSocketUrl = "wss://mi-servidor-sip.com:8443",
            userAgent = "MiApp/1.0.0 (Android)",
            
            // Configuración automática de push
            autoEnterPushOnBackground = true,
            autoExitPushOnForeground = true,
            autoDisconnectWebSocketOnBackground = false,
            
            // Configuración de audio
            autoSelectAudioDevice = true,
            preferredAudioDevice = EddysSipLibrary.AudioDeviceType.AUTO,
            
            // Headers personalizados
            customHeaders = mapOf(
                "X-App-Version" to "1.0.0",
                "X-Device-Model" to Build.MODEL
            )
        )
        
        // Inicializar
        sipLibrary = EddysSipLibrary.getInstance()
        sipLibrary.initialize(this, config, eventListener)
    }
}
```

### 2. Event Listener Completo

```kotlin
private val eventListener = object : EddysSipLibrary.SipEventListener {
    
    // Eventos de Registro
    override fun onRegistrationStateChanged(state: RegistrationState, account: String) {
        Log.d("SIP", "Registration: $account -> $state")
    }
    
    override fun onRegistrationSuccess(account: String, expiresIn: Int) {
        Log.d("SIP", "Registered successfully: $account (expires in ${expiresIn}s)")
    }
    
    override fun onRegistrationFailed(account: String, reason: String) {
        Log.e("SIP", "Registration failed: $account - $reason")
    }
    
    // Eventos de Llamada
    override fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
        Log.d("SIP", "📞 Incoming call from: $callerNumber")
        showIncomingCallUI(callerNumber, callerName, callId)
    }
    
    override fun onCallStateChanged(oldState: CallState, newState: CallState, callId: String) {
        Log.d("SIP", "Call state: $oldState -> $newState")
        updateCallUI(newState)
    }
    
    override fun onCallConnected(callId: String, duration: Long) {
        Log.d("SIP", "🎉 Call connected!")
        startCallTimer()
    }
    
    override fun onCallDisconnected(callId: String, reason: EddysSipLibrary.CallEndReason, duration: Long) {
        Log.d("SIP", "📴 Call ended: $reason (${duration}ms)")
        hideCallUI()
    }
    
    // Eventos de Audio
    override fun onAudioDeviceChanged(oldDevice: AudioDevice?, newDevice: AudioDevice) {
        Log.d("SIP", "🔊 Audio: ${oldDevice?.name} -> ${newDevice.name}")
        updateAudioButtonUI(newDevice)
    }
    
    override fun onAudioDevicesAvailable(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {
        updateAudioDevicesList(outputDevices)
    }
    
    // Eventos de Conectividad
    override fun onNetworkStateChanged(isConnected: Boolean, networkType: String) {
        Log.d("SIP", "Network: $isConnected ($networkType)")
        updateNetworkIndicator(isConnected)
    }
    
    override fun onWebSocketStateChanged(isConnected: Boolean, url: String) {
        Log.d("SIP", "WebSocket: $isConnected")
    }
    
    // Eventos de Push
    override fun onPushModeChanged(isInPushMode: Boolean, reason: String) {
        Log.d("SIP", "Push mode: $isInPushMode - $reason")
    }
    
    // Eventos de App
    override fun onAppStateChanged(appState: EddysSipLibrary.AppState, previousState: EddysSipLibrary.AppState) {
        Log.d("SIP", "App state: $previousState -> $appState")
    }
    
    // Eventos de Calidad
    override fun onNetworkQuality(quality: EddysSipLibrary.NetworkQuality) {
        updateQualityIndicator(quality)
    }
    
    // Eventos de Error
    override fun onError(error: EddysSipLibrary.SipError) {
        Log.e("SIP", "[${error.code}] ${error.category}: ${error.message}")
        showErrorToUser(error)
    }
    
    override fun onWarning(warning: EddysSipLibrary.SipWarning) {
        Log.w("SIP", "${warning.category}: ${warning.message}")
    }
}
```

### 3. Operaciones Básicas

```kotlin
class CallManager {
    
    private val sipLibrary = EddysSipLibrary.getInstance()
    
    // Registrar cuenta
    fun registerAccount() {
        sipLibrary.registerAccount(
            username = "usuario123",
            password = "mi_password",
            domain = "mi-servidor-sip.com",
            pushToken = getFCMToken(),
            pushProvider = "fcm"
        )
    }
    
    // Realizar llamada
    fun makeCall(phoneNumber: String) {
        if (phoneNumber.isValidPhoneNumber()) {
            sipLibrary.makeCall(phoneNumber)
        } else {
            showError("Número de teléfono inválido")
        }
    }
    
    // Responder llamada
    fun answerCall() {
        sipLibrary.acceptCall()
    }
    
    // Rechazar llamada
    fun declineCall() {
        sipLibrary.declineCall()
    }
    
    // Terminar llamada
    fun endCall() {
        sipLibrary.endCall()
    }
    
    // Poner en espera
    fun holdCall() {
        sipLibrary.holdCall()
    }
    
    // Reanudar llamada
    fun resumeCall() {
        sipLibrary.resumeCall()
    }
    
    // Silenciar/desilenciar
    fun toggleMute() {
        sipLibrary.toggleMute()
    }
    
    // Cambiar a altavoz
    fun switchToSpeaker() {
        sipLibrary.changeAudioDevice(EddysSipLibrary.AudioDeviceType.SPEAKER)
    }
    
    // Enviar DTMF
    fun sendDtmf(digit: Char) {
        sipLibrary.sendDtmf(digit)
    }
}
```

## 🔧 Configuración Avanzada

### Configuración Completa

```kotlin
val config = EddysSipLibrary.SipConfig(
    // Configuración básica
    defaultDomain = "sip.miempresa.com",
    webSocketUrl = "wss://sip.miempresa.com:8443",
    userAgent = "MiApp/1.0.0 (Android ${Build.VERSION.RELEASE})",
    enableLogs = BuildConfig.DEBUG,
    enableAutoReconnect = true,
    pingIntervalMs = 30000L,
    registrationExpiresSeconds = 3600,
    
    // Push automático
    autoEnterPushOnBackground = true,
    autoExitPushOnForeground = true,
    autoDisconnectWebSocketOnBackground = false,
    pushReconnectDelayMs = 2000L,
    
    // Audio
    autoSelectAudioDevice = true,
    preferredAudioDevice = EddysSipLibrary.AudioDeviceType.AUTO,
    enableEchoCancellation = true,
    enableNoiseSuppression = true,
    
    // Llamadas
    autoAcceptDelay = 0L, // 0 = manual, >0 = auto-respuesta
    callTimeoutSeconds = 60,
    enableCallRecording = false,
    
    // Headers personalizados
    customHeaders = mapOf(
        "X-App-Version" to BuildConfig.VERSION_NAME,
        "X-Device-Model" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "X-Android-Version" to Build.VERSION.RELEASE
    ),
    
    customContactParams = mapOf(
        "app" to "miapp",
        "platform" to "android"
    )
)
```

### Manejo de Push Notifications

```kotlin
class FCMService : FirebaseMessagingService() {
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        // Actualizar token en la biblioteca
        if (EddysSipLibrary.getInstance().isSystemHealthy()) {
            EddysSipLibrary.getInstance().updatePushToken(token, "fcm")
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Manejar llamada entrante desde push
        val callData = remoteMessage.data
        if (callData.containsKey("sip_call")) {
            handleIncomingSipCall(callData)
        }
    }
    
    private fun handleIncomingSipCall(data: Map<String, String>) {
        val callerNumber = data["caller"] ?: return
        val callId = data["call_id"] ?: return
        
        // La biblioteca manejará automáticamente la llamada entrante
        // cuando se reconecte el WebSocket
    }
}
```

### Control Manual de Estados

```kotlin
class SipController {
    
    private val sipLibrary = EddysSipLibrary.getInstance()
    
    // Desactivar modo automático
    fun setupManualControl() {
        sipLibrary.setAutoPushMode(false)
    }
    
    // Control manual de push
    fun enterPushModeManually() {
        sipLibrary.enterPushMode("Usuario solicitó modo push")
    }
    
    fun exitPushModeManually() {
        sipLibrary.exitPushMode("Usuario solicitó modo normal")
    }
    
    // Actualizar configuración dinámicamente
    fun updateUserAgent() {
        sipLibrary.updateUserAgent("MiApp/2.0.0 (Android)")
    }
    
    // Obtener estadísticas
    fun getCallStatistics() {
        val stats = sipLibrary.getCurrentCallStatistics()
        stats?.let {
            Log.d("Stats", """
                Duration: ${it.duration.toCallDuration()}
                Quality: ${it.networkQuality}
                Codec: ${it.audioCodec}
                Packets Lost: ${it.packetsLost}
            """.trimIndent())
        }
    }
    
    // Monitorear calidad de red
    fun monitorNetworkQuality() {
        val quality = sipLibrary.getNetworkQuality()
        quality?.let {
            when {
                it.score >= 0.8f -> showGoodQualityIndicator()
                it.score >= 0.6f -> showMediumQualityIndicator()
                else -> showPoorQualityIndicator()
            }
        }
    }
}
```

## 📊 Monitoreo y Estadísticas

```kotlin
// Obtener estadísticas de llamada
val stats = sipLibrary.getCurrentCallStatistics()
stats?.let {
    println("Duración: ${it.duration.toCallDuration()}")
    println("Calidad: ${it.networkQuality}")
    println("Latencia: ${it.rtt}ms")
    println("Pérdida de paquetes: ${it.packetsLost}")
}

// Obtener historial de llamadas
val callLogs = sipLibrary.getCallLogs()
callLogs.forEach { log ->
    println("${log.formattedStartDate}: ${log.from} -> ${log.to} (${log.duration}s)")
}

// Reportar salud del sistema
val healthReport = sipLibrary.getSystemHealthReport()
println(healthReport)

// Verificar si el sistema está saludable
if (!sipLibrary.isSystemHealthy()) {
    // Tomar acciones correctivas
    restartSipLibrary()
}
```

## 🎯 Casos de Uso Comunes

### 1. App de Llamadas Simple
```kotlin
// Solo eventos de llamada necesarios
val simpleListener = eventListener.onlyCallEvents()
sipLibrary.setEventListener(simpleListener)
```

### 2. Configuración para Testing
```kotlin
val testConfig = config.forTesting()
sipLibrary.updateConfig(testConfig)
```

### 3. Registro Rápido
```kotlin
sipLibrary.registerWithAutoConfig("usuario", "password")
```

### 4. Llamada Rápida
```kotlin
sipLibrary.makeQuickCall("+1234567890")
```

## ⚠️ Consideraciones Importantes

### Permisos de Audio
- La biblioteca solicita automáticamente permisos de audio cuando es necesario
- Asegúrate de manejar la negación de permisos en tu UI

### Optimización de Batería
- La biblioteca detecta automáticamente si la optimización de batería está activa
- Considera guiar al usuario para desactivar la optimización para tu app

### Conectividad
- La biblioteca maneja automáticamente reconexiones en caso de pérdida de red
- Los eventos de conectividad te permiten informar al usuario sobre el estado

### Push Notifications
- El modo push automático funciona mejor con Firebase Cloud Messaging (FCM)
- Asegúrate de configurar correctamente los certificados push en tu servidor SIP

## 🆘 Resolución de Problemas

### Audio No Funciona
```kotlin
// Verificar permisos
if (!hasAudioPermission()) {
    requestAudioPermission()
}

// Diagnosticar problemas de audio
val audioDiagnosis = sipLibrary.webRtcManager.diagnoseAudioIssues()
Log.d("Audio", audioDiagnosis)

// Forzar reinicialización de audio
sipLibrary.getAudioDevices() // Esto fuerza una reinicialización
```

### Problemas de Registro
```kotlin
// Verificar conectividad
val isHealthy = sipLibrary.isSystemHealthy()
if (!isHealthy) {
    val report = sipLibrary.getSystemHealthReport()
    Log.e("SIP", report)
}

// Verificar configuración
val config = sipLibrary.getCurrentConfig()
Log.d("Config", "Domain: ${config.defaultDomain}, URL: ${config.webSocketUrl}")
```

### Llamadas No Se Conectan
```kotlin
// Verificar estado de registro
val registrationState = sipLibrary.getRegistrationState()
if (registrationState != RegistrationState.OK) {
    // Re-registrar
    sipLibrary.registerAccount(/* parámetros */)
}

// Verificar estado de llamada
val callState = sipLibrary.getCurrentCallState()
Log.d("Call", "Current state: $callState")
```

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

## 👨‍💻 Autor

**Eddys Larez**
- Email: eddyslarez@gmail.com
- GitHub: [@eddyslarez](https://github.com/eddyslarez)

---

¿Necesitas ayuda? Abre un [issue](https://github.com/eddyslarez/sip-library/issues) en GitHub.

[//]: # (# Eddys Larez SIP Library)

[//]: # ()
[//]: # ()
[//]: # (Una biblioteca SIP/VoIP para Android desarrollada por Eddys Larez, que proporciona funcionalidades completas para realizar y recibir llamadas SIP usando WebRTC y WebSocket.)

[//]: # ()
[//]: # ()
[//]: # (## 🚀 Características)

[//]: # ()
[//]: # ()
[//]: # (- ✅ Llamadas SIP entrantes y salientes)

[//]: # ()
[//]: # (- ✅ Soporte para WebRTC)

[//]: # ()
[//]: # (- ✅ Conexión WebSocket robusta con reconexión automática)

[//]: # ()
[//]: # (- ✅ Soporte para DTMF)

[//]: # ()
[//]: # (- ✅ Gestión de dispositivos de audio &#40;altavoz, auriculares, Bluetooth&#41;)

[//]: # ()
[//]: # (- ✅ Historial de llamadas)

[//]: # ()
[//]: # (- ✅ Notificaciones push)

[//]: # ()
[//]: # (- ✅ Estados de llamada reactivos con Flow)

[//]: # ()
[//]: # (- ✅ Arquitectura moderna con Kotlin)

[//]: # ()
[//]: # ()
[//]: # (## 📱 Instalación)

[//]: # ()
[//]: # ()
[//]: # (### Usando JitPack)

[//]: # ()
[//]: # ()
[//]: # (1. Agrega JitPack en tu `settings.gradle.kts` &#40;nivel proyecto&#41;:)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (dependencyResolutionManagement {)

[//]: # ()
[//]: # (    repositories {)

[//]: # ()
[//]: # (        google&#40;&#41;)

[//]: # ()
[//]: # (        mavenCentral&#40;&#41;)

[//]: # ()
[//]: # (        maven { url = uri&#40;"https://jitpack.io"&#41; })

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (})

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (2. Agrega la dependencia en tu `build.gradle.kts` &#40;nivel app&#41;:)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (dependencies {)

[//]: # ()
[//]: # (    implementation&#40;"com.github.eddyslarez:sip-library:1.0.0"&#41;)

[//]: # ()
[//]: # (})

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### Desde GitHub directamente)

[//]: # ()
[//]: # ()
[//]: # (También puedes clonar el repositorio e incluir el módulo en tu proyecto:)

[//]: # ()
[//]: # ()
[//]: # (```bash)

[//]: # ()
[//]: # (git clone https://github.com/eddyslarez/sip-library.git)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (## 🛠️ Configuración)

[//]: # ()
[//]: # ()
[//]: # (### 1. Permisos)

[//]: # ()
[//]: # ()
[//]: # (Agrega estos permisos en tu `AndroidManifest.xml`:)

[//]: # ()
[//]: # ()
[//]: # (```xml)

[//]: # ()
[//]: # (<uses-permission android:name="android.permission.INTERNET" />)

[//]: # ()
[//]: # (<uses-permission android:name="android.permission.RECORD_AUDIO" />)

[//]: # ()
[//]: # (<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />)

[//]: # ()
[//]: # (<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />)

[//]: # ()
[//]: # (<uses-permission android:name="android.permission.BLUETOOTH" />)

[//]: # ()
[//]: # (<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### 2. Inicialización)

[//]: # ()
[//]: # ()
[//]: # (En tu `Application` clase:)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (class MyApplication : Application&#40;&#41; {)

[//]: # ()
[//]: # (    override fun onCreate&#40;&#41; {)

[//]: # ()
[//]: # (        super.onCreate&#40;&#41;)

[//]: # ()
[//]: # (        )
[//]: # (        // Configuración personalizada &#40;opcional&#41;)

[//]: # ()
[//]: # (        val config = EddysSipLibrary.SipConfig&#40;)

[//]: # ()
[//]: # (            defaultDomain = "tu-dominio.com",)

[//]: # ()
[//]: # (            webSocketUrl = "wss://tu-servidor:puerto/",)

[//]: # ()
[//]: # (            userAgent = "MiApp/1.0.0",)

[//]: # ()
[//]: # (            enableLogs = true)

[//]: # ()
[//]: # (        &#41;)

[//]: # ()
[//]: # (        )
[//]: # (        // Inicializar la biblioteca)

[//]: # ()
[//]: # (        EddysSipLibrary.getInstance&#40;&#41;.initialize&#40;this, config&#41;)

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (})

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (## 📋 Uso Básico)

[//]: # ()
[//]: # ()
[//]: # (### Registrar una cuenta SIP)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (val sipLibrary = EddysSipLibrary.getInstance&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (sipLibrary.registerAccount&#40;)

[//]: # ()
[//]: # (    username = "usuario",)

[//]: # ()
[//]: # (    password = "contraseña",)

[//]: # ()
[//]: # (    domain = "mi-dominio.com", // opcional, usa el configurado por defecto)

[//]: # ()
[//]: # (    pushToken = "token_fcm", // opcional)

[//]: # ()
[//]: # (    pushProvider = "fcm" // fcm o apns)

[//]: # ()
[//]: # (&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### Realizar una llamada)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (sipLibrary.makeCall&#40;"1234567890"&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### Responder/Rechazar llamadas)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (// Aceptar llamada entrante)

[//]: # ()
[//]: # (sipLibrary.acceptCall&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Rechazar llamada entrante)

[//]: # ()
[//]: # (sipLibrary.declineCall&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Terminar llamada actual)

[//]: # ()
[//]: # (sipLibrary.endCall&#40;&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### Funciones durante la llamada)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (// Silenciar/desmute)

[//]: # ()
[//]: # (sipLibrary.toggleMute&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Verificar si está silenciado)

[//]: # ()
[//]: # (val isMuted = sipLibrary.isMuted&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Enviar DTMF)

[//]: # ()
[//]: # (sipLibrary.sendDtmf&#40;'1'&#41;)

[//]: # ()
[//]: # (sipLibrary.sendDtmfSequence&#40;"123*"&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Poner en espera)

[//]: # ()
[//]: # (sipLibrary.holdCall&#40;&#41;)

[//]: # ()
[//]: # (sipLibrary.resumeCall&#40;&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### Gestión de audio)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (// Obtener dispositivos disponibles)

[//]: # ()
[//]: # (val &#40;inputDevices, outputDevices&#41; = sipLibrary.getAudioDevices&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Cambiar dispositivo de salida)

[//]: # ()
[//]: # (outputDevices.forEach { device ->)

[//]: # ()
[//]: # (    if &#40;device.name.contains&#40;"Bluetooth"&#41;&#41; {)

[//]: # ()
[//]: # (        sipLibrary.changeAudioOutput&#40;device&#41;)

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (})

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (## 🔄 Observar Estados)

[//]: # ()
[//]: # ()
[//]: # (### Estados de llamada)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (class MainActivity : ComponentActivity&#40;&#41; {)

[//]: # ()
[//]: # (    private lateinit var sipLibrary: EddysSipLibrary)

[//]: # ()
[//]: # (    )
[//]: # (    override fun onCreate&#40;savedInstanceState: Bundle?&#41; {)

[//]: # ()
[//]: # (        super.onCreate&#40;savedInstanceState&#41;)

[//]: # ()
[//]: # (        )
[//]: # (        sipLibrary = EddysSipLibrary.getInstance&#40;&#41;)

[//]: # ()
[//]: # (        )
[//]: # (        // Observar cambios de estado de llamada)

[//]: # ()
[//]: # (        lifecycleScope.launch {)

[//]: # ()
[//]: # (            sipLibrary.getCallStateFlow&#40;&#41;.collect { callState ->)

[//]: # ()
[//]: # (                when &#40;callState&#41; {)

[//]: # ()
[//]: # (                    CallState.INCOMING -> {)

[//]: # ()
[//]: # (                        // Llamada entrante)

[//]: # ()
[//]: # (                        showIncomingCallUI&#40;&#41;)

[//]: # ()
[//]: # (                    })

[//]: # ()
[//]: # (                    CallState.CONNECTED -> {)

[//]: # ()
[//]: # (                        // Llamada conectada)

[//]: # ()
[//]: # (                        showInCallUI&#40;&#41;)

[//]: # ()
[//]: # (                    })

[//]: # ()
[//]: # (                    CallState.ENDED -> {)

[//]: # ()
[//]: # (                        // Llamada terminada)

[//]: # ()
[//]: # (                        showMainUI&#40;&#41;)

[//]: # ()
[//]: # (                    })

[//]: # ()
[//]: # (                    else -> {)

[//]: # ()
[//]: # (                        // Otros estados)

[//]: # ()
[//]: # (                    })

[//]: # ()
[//]: # (                })

[//]: # ()
[//]: # (            })

[//]: # ()
[//]: # (        })

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (})

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### Estados de registro)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (lifecycleScope.launch {)

[//]: # ()
[//]: # (    sipLibrary.getRegistrationStateFlow&#40;&#41;.collect { registrationState ->)

[//]: # ()
[//]: # (        when &#40;registrationState&#41; {)

[//]: # ()
[//]: # (            RegistrationState.OK -> {)

[//]: # ()
[//]: # (                // Registrado exitosamente)

[//]: # ()
[//]: # (                updateUI&#40;"Conectado"&#41;)

[//]: # ()
[//]: # (            })

[//]: # ()
[//]: # (            RegistrationState.FAILED -> {)

[//]: # ()
[//]: # (                // Error en registro)

[//]: # ()
[//]: # (                updateUI&#40;"Error de conexión"&#41;)

[//]: # ()
[//]: # (            })

[//]: # ()
[//]: # (            else -> {)

[//]: # ()
[//]: # (                // Otros estados)

[//]: # ()
[//]: # (            })

[//]: # ()
[//]: # (        })

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (})

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (## 📞 Historial de Llamadas)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (// Obtener todas las llamadas)

[//]: # ()
[//]: # (val callLogs = sipLibrary.getCallLogs&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Obtener solo llamadas perdidas)

[//]: # ()
[//]: # (val missedCalls = sipLibrary.getMissedCalls&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Limpiar historial)

[//]: # ()
[//]: # (sipLibrary.clearCallLogs&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Buscar llamadas de un número específico)

[//]: # ()
[//]: # (val callsFromNumber = sipLibrary.getCallLogsForNumber&#40;"1234567890"&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (## 🔧 Configuración Avanzada)

[//]: # ()
[//]: # ()
[//]: # (### Callbacks personalizados)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (sipLibrary.setCallbacks&#40;object : EddysSipLibrary.SipCallbacks {)

[//]: # ()
[//]: # (    override fun onCallTerminated&#40;&#41; {)

[//]: # ()
[//]: # (        // Llamada terminada)

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (    )
[//]: # (    override fun onCallStateChanged&#40;state: CallState&#41; {)

[//]: # ()
[//]: # (        // Estado de llamada cambió)

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (    )
[//]: # (    override fun onRegistrationStateChanged&#40;state: RegistrationState&#41; {)

[//]: # ()
[//]: # (        // Estado de registro cambió)

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (    )
[//]: # (    override fun onIncomingCall&#40;callerNumber: String, callerName: String?&#41; {)

[//]: # ()
[//]: # (        // Llamada entrante)

[//]: # ()
[//]: # (        showNotification&#40;"Llamada de $callerNumber"&#41;)

[//]: # ()
[//]: # (    })

[//]: # ()
[//]: # (}&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### Diagnóstico y salud del sistema)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (// Verificar si el sistema está saludable)

[//]: # ()
[//]: # (val isHealthy = sipLibrary.isSystemHealthy&#40;&#41;)

[//]: # ()
[//]: # ()
[//]: # (// Obtener reporte detallado)

[//]: # ()
[//]: # (val healthReport = sipLibrary.getSystemHealthReport&#40;&#41;)

[//]: # ()
[//]: # (println&#40;healthReport&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (## 🌟 Características Avanzadas)

[//]: # ()
[//]: # ()
[//]: # (### Soporte para múltiples dominios)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (// Configurar diferentes servidores según el dominio)

[//]: # ()
[//]: # (val config = EddysSipLibrary.SipConfig&#40;)

[//]: # ()
[//]: # (    defaultDomain = "dominio",)

[//]: # ()
[//]: # (    webSocketUrl = "wss://dominio:XXXXXX/")

[//]: # ()
[//]: # (&#41;)

[//]: # ()
[//]: # ()
[//]: # (// registro)

[//]: # ()
[//]: # (sipLibrary.registerAccount&#40;)

[//]: # ()
[//]: # (    username = "usuario",)

[//]: # ()
[//]: # (    password = "contraseña",)

[//]: # ()
[//]: # (    domain = "dominio")

[//]: # ()
[//]: # (&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (### Reconexión automática)

[//]: # ()
[//]: # ()
[//]: # (La biblioteca maneja automáticamente:)

[//]: # ()
[//]: # (- ✅ Reconexión de WebSocket)

[//]: # ()
[//]: # (- ✅ Re-registro SIP)

[//]: # ()
[//]: # (- ✅ Manejo de cambios de red)

[//]: # ()
[//]: # (- ✅ Keepalive con ping/pong)

[//]: # ()
[//]: # ()
[//]: # (### Soporte para notificaciones push)

[//]: # ()
[//]: # ()
[//]: # (```kotlin)

[//]: # ()
[//]: # (// Actualizar token de push)

[//]: # ()
[//]: # (sipLibrary.updatePushToken&#40;"nuevo_token_fcm", "fcm"&#41;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # ()
[//]: # (## 🐛 Solución de Problemas)

[//]: # ()
[//]: # ()
[//]: # (### Problemas comunes)

[//]: # ()
[//]: # ()
[//]: # (1. **Error de permisos de audio**:)

[//]: # ()
[//]: # (   ```kotlin)

[//]: # ()
[//]: # (   // Solicitar permisos antes de usar)

[//]: # ()
[//]: # (   if &#40;ContextCompat.checkSelfPermission&#40;this, Manifest.permission.RECORD_AUDIO&#41; )

[//]: # ()
[//]: # (       != PackageManager.PERMISSION_GRANTED&#41; {)

[//]: # ()
[//]: # (       ActivityCompat.requestPermissions&#40;this, )

[//]: # ()
[//]: # (           arrayOf&#40;Manifest.permission.RECORD_AUDIO&#41;, 1&#41;)

[//]: # ()
[//]: # (   })

[//]: # ()
[//]: # (   ```)

[//]: # ()
[//]: # ()
[//]: # (2. **Problemas de conexión**:)

[//]: # ()
[//]: # (   ```kotlin)

[//]: # ()
[//]: # (   // Verificar estado de salud)

[//]: # ()
[//]: # (   val healthReport = sipLibrary.getSystemHealthReport&#40;&#41;)

[//]: # ()
[//]: # (   Log.d&#40;"SIP", healthReport&#41;)

[//]: # ()
[//]: # (   ```)

[//]: # ()
[//]: # ()
[//]: # (3. **Audio no funciona**:)

[//]: # ()
[//]: # (   ```kotlin)

[//]: # ()
[//]: # (   // Verificar dispositivos disponibles)

[//]: # ()
[//]: # (   val &#40;input, output&#41; = sipLibrary.getAudioDevices&#40;&#41;)

[//]: # ()
[//]: # (   Log.d&#40;"Audio", "Input devices: $input"&#41;)

[//]: # ()
[//]: # (   Log.d&#40;"Audio", "Output devices: $output"&#41;)

[//]: # ()
[//]: # (   ```)

[//]: # ()
[//]: # ()
[//]: # (## 📄 Licencia)

[//]: # ()
[//]: # ()
[//]: # (Desarrollado por **Eddys Larez**)

[//]: # ()
[//]: # ()
[//]: # (Este proyecto es de código abierto y está disponible bajo la licencia MIT.)

[//]: # ()
[//]: # ()
[//]: # (## 🤝 Contribución)

[//]: # ()
[//]: # ()
[//]: # (Las contribuciones son bienvenidas. Por favor:)

[//]: # ()
[//]: # ()
[//]: # (1. Fork el proyecto)

[//]: # ()
[//]: # (2. Crea una rama para tu feature &#40;`git checkout -b feature/nueva-caracteristica`&#41;)

[//]: # ()
[//]: # (3. Commit tus cambios &#40;`git commit -am 'Agregar nueva característica'`&#41;)

[//]: # ()
[//]: # (4. Push a la rama &#40;`git push origin feature/nueva-caracteristica`&#41;)

[//]: # ()
[//]: # (5. Abre un Pull Request)

[//]: # ()
[//]: # ()
[//]: # (## 📞 Soporte)

[//]: # ()
[//]: # ()
[//]: # (Para soporte técnico o preguntas:)

[//]: # ()
[//]: # ()
[//]: # (- GitHub Issues: [Reportar un problema]&#40;https://github.com/eddyslarez/sip-library/issues&#41;)

[//]: # ()
[//]: # (- Email: eddyslarez@example.com)

[//]: # ()
[//]: # ()
[//]: # (## 🔄 Changelog)

[//]: # ()
[//]: # ()
[//]: # (### v1.0.0)

[//]: # ()
[//]: # (- ✅ Lanzamiento inicial)

[//]: # ()
[//]: # (- ✅ Soporte completo para SIP/WebRTC)

[//]: # ()
[//]: # (- ✅ Gestión de llamadas)

[//]: # ()
[//]: # (- ✅ Historial de llamadas)

[//]: # ()
[//]: # (- ✅ Soporte para DTMF)

[//]: # ()
[//]: # (- ✅ Gestión de audio)

[//]: # ()
[//]: # (- ✅ Estados reactivos)

[//]: # ()
[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # ()
[//]: # (**Desarrollado con ❤️ por Eddys Larez**)