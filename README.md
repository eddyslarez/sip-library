# EddysSipLibrary - Biblioteca SIP/VoIP para Android

Una biblioteca completa y fácil de usar para implementar funcionalidad SIP/VoIP en aplicaciones Android, desarrollada por Eddys Larez.

## Características Principales

### 🚀 Funcionalidades Core
- **Registro SIP automático** con soporte para múltiples cuentas
- **Llamadas entrantes y salientes** con WebRTC
- **Gestión automática de ciclo de vida** de la aplicación
- **Modo Push y Foreground** automático
- **Gestión de dispositivos de audio** (altavoz, auricular, Bluetooth, etc.)
- **DTMF** (tonos de marcado)
- **Hold/Resume** de llamadas
- **Historial de llamadas** completo

### 🔧 Configuración Avanzada
- **Auto-reconexión** configurable
- **Headers SIP personalizados**
- **Timeouts configurables**
- **Gestión automática de WebSocket**
- **Configuración de audio automática**
- **Soporte para notificaciones push**

### 📱 Gestión de Ciclo de Vida
- **Cambio automático a modo push** cuando la app pasa a segundo plano
- **Reconexión automática** cuando la app vuelve a primer plano
- **Desconexión automática** de WebSocket (opcional)
- **Gestión inteligente de recursos**

## Instalación

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
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## Uso Básico

### 1. Inicialización

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Configuración básica
        val config = EddysSipLibrary.SipConfig(
            defaultDomain = "mi-servidor-sip.com",
            webSocketUrl = "wss://mi-servidor-sip.com:8089/ws",
            userAgent = "MiApp/1.0",
            enableLogs = true,
            
            // Configuración automática de ciclo de vida
            autoSwitchToPushOnBackground = true,
            autoSwitchToForegroundOnResume = true,
            autoDisconnectWebSocketOnBackground = false,
            
            // Configuración de audio
            enableAutoAudioRouting = true,
            preferredAudioDevice = "earpiece", // "speaker", "bluetooth", "wired_headset"
            
            // Configuración de reconexión
            enableAutoReconnect = true,
            maxReconnectAttempts = 3,
            reconnectDelayMs = 2000L,
            exponentialBackoff = true
        )
        
        // Inicializar la biblioteca
        EddysSipLibrary.getInstance().initialize(this, config)
    }
}
```

### 2. Configurar Event Listener

```kotlin
class MainActivity : ComponentActivity() {
    
    private val sipLibrary = EddysSipLibrary.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configurar listener para todos los eventos
        sipLibrary.setEventListener(object : EddysSipLibrary.SipEventListener {
            
            // Estados de registro
            override fun onRegistrationStateChanged(state: RegistrationState, message: String) {
                Log.d("SIP", "Registration state: $state - $message")
            }
            
            override fun onRegistrationSuccess(account: String, expiresIn: Int) {
                Log.d("SIP", "Registered successfully: $account (expires in ${expiresIn}s)")
            }
            
            override fun onRegistrationFailed(account: String, error: String) {
                Log.e("SIP", "Registration failed for $account: $error")
            }
            
            // Estados de llamada
            override fun onCallStateChanged(state: CallState, callId: String) {
                Log.d("SIP", "Call state changed: $state for call $callId")
                when (state) {
                    CallState.INCOMING -> showIncomingCallUI()
                    CallState.CONNECTED -> showActiveCallUI()
                    CallState.ENDED -> hideCallUI()
                    else -> {}
                }
            }
            
            override fun onIncomingCall(callerNumber: String, callerName: String?, callId: String) {
                Log.d("SIP", "Incoming call from: $callerNumber ($callerName)")
                // Mostrar UI de llamada entrante
                showIncomingCallDialog(callerNumber, callerName)
            }
            
            override fun onCallConnected(callId: String, duration: Long) {
                Log.d("SIP", "Call connected: $callId")
                // Actualizar UI de llamada activa
            }
            
            override fun onCallDisconnected(callId: String, reason: String, duration: Long) {
                Log.d("SIP", "Call disconnected: $callId - $reason (duration: ${duration}ms)")
                // Ocultar UI de llamada
            }
            
            override fun onCallFailed(callId: String, error: String) {
                Log.e("SIP", "Call failed: $callId - $error")
                showErrorDialog("Call failed: $error")
            }
            
            override fun onCallHeld(callId: String) {
                Log.d("SIP", "Call held: $callId")
                updateHoldButton(true)
            }
            
            override fun onCallResumed(callId: String) {
                Log.d("SIP", "Call resumed: $callId")
                updateHoldButton(false)
            }
            
            // Dispositivos de audio
            override fun onAudioDevicesChanged(inputDevices: List<AudioDevice>, outputDevices: List<AudioDevice>) {
                Log.d("SIP", "Audio devices changed: ${outputDevices.size} output devices available")
                updateAudioDevicesList(outputDevices)
            }
            
            override fun onAudioDeviceSelected(device: AudioDevice) {
                Log.d("SIP", "Audio device selected: ${device.name}")
                updateAudioDeviceUI(device)
            }
            
            override fun onMuteStateChanged(isMuted: Boolean) {
                Log.d("SIP", "Mute state changed: $isMuted")
                updateMuteButton(isMuted)
            }
            
            // Estados de conexión
            override fun onWebSocketConnected() {
                Log.d("SIP", "WebSocket connected")
                updateConnectionStatus(true)
            }
            
            override fun onWebSocketDisconnected(code: Int, reason: String) {
                Log.d("SIP", "WebSocket disconnected: $code - $reason")
                updateConnectionStatus(false)
            }
            
            override fun onWebSocketError(error: String) {
                Log.e("SIP", "WebSocket error: $error")
                showErrorDialog("Connection error: $error")
            }
            
            // Ciclo de vida de la aplicación
            override fun onAppMovedToBackground() {
                Log.d("SIP", "App moved to background")
                // La biblioteca automáticamente cambia a modo push si está configurado
            }
            
            override fun onAppMovedToForeground() {
                Log.d("SIP", "App moved to foreground")
                // La biblioteca automáticamente vuelve a modo foreground si está configurado
            }
            
            override fun onModeChanged(mode: String) {
                Log.d("SIP", "Operation mode changed to: $mode")
                updateModeIndicator(mode)
            }
            
            // DTMF
            override fun onDtmfSent(digit: Char, success: Boolean) {
                Log.d("SIP", "DTMF sent: $digit - success: $success")
            }
            
            override fun onDtmfReceived(digit: Char) {
                Log.d("SIP", "DTMF received: $digit")
                showDtmfReceived(digit)
            }
            
            // Sistema
            override fun onLibraryError(error: String, exception: Throwable?) {
                Log.e("SIP", "Library error: $error", exception)
                showErrorDialog("System error: $error")
            }
            
            override fun onConfigurationChanged(newConfig: EddysSipLibrary.SipConfig) {
                Log.d("SIP", "Configuration updated")
            }
        })
    }
}
```

### 3. Registrar Cuenta SIP

```kotlin
// Registro básico
sipLibrary.registerAccount(
    username = "usuario123",
    password = "mi_password",
    domain = "mi-servidor-sip.com" // opcional, usa el configurado por defecto
)

// Registro avanzado con configuración personalizada
sipLibrary.registerAccount(
    username = "usuario123",
    password = "mi_password",
    domain = "mi-servidor-sip.com",
    pushToken = "fcm_token_aqui", // para notificaciones push
    pushProvider = "fcm", // o "apns"
    customHeaders = mapOf(
        "X-Custom-Header" to "valor_personalizado",
        "X-App-Version" to "1.0.0"
    ),
    expirationSeconds = 3600 // 1 hora
)
```

### 4. Realizar Llamadas

```kotlin
// Llamada básica
sipLibrary.makeCall("1234567890")

// Llamada con parámetros avanzados
sipLibrary.makeCall(
    phoneNumber = "1234567890",
    username = "mi_cuenta_especifica", // opcional
    domain = "otro-servidor.com", // opcional
    customHeaders = mapOf(
        "X-Call-Type" to "business",
        "X-Priority" to "high"
    )
)
```

### 5. Gestionar Llamadas Entrantes

```kotlin
// En el listener onIncomingCall
private fun showIncomingCallDialog(callerNumber: String, callerName: String?) {
    AlertDialog.Builder(this)
        .setTitle("Llamada entrante")
        .setMessage("$callerNumber${callerName?.let { " ($it)" } ?: ""}")
        .setPositiveButton("Aceptar") { _, _ ->
            sipLibrary.acceptCall()
        }
        .setNegativeButton("Rechazar") { _, _ ->
            sipLibrary.declineCall()
        }
        .setCancelable(false)
        .show()
}
```

### 6. Controles de Llamada

```kotlin
// Terminar llamada
sipLibrary.endCall()

// Silenciar/desmutear
val isMuted = sipLibrary.toggleMute()
// o
sipLibrary.setMuted(true)

// Hold/Resume
sipLibrary.holdCall()
sipLibrary.resumeCall()

// Enviar DTMF
sipLibrary.sendDtmf('1')
sipLibrary.sendDtmfSequence("123#")
```

### 7. Gestión de Dispositivos de Audio

```kotlin
// Obtener dispositivos disponibles
val audioDevices = sipLibrary.getAudioDevices()
Log.d("SIP", "Input devices: ${audioDevices.inputDevices.size}")
Log.d("SIP", "Output devices: ${audioDevices.outputDevices.size}")
Log.d("SIP", "Current output: ${audioDevices.currentOutput?.name}")

// Cambiar dispositivo de audio
audioDevices.outputDevices.forEach { device ->
    when (device.descriptor) {
        "speaker" -> {
            // Cambiar a altavoz
            sipLibrary.changeAudioDevice(device)
        }
        "bluetooth_headset" -> {
            // Cambiar a Bluetooth
            sipLibrary.changeAudioDevice(device)
        }
        "earpiece" -> {
            // Cambiar a auricular
            sipLibrary.changeAudioDevice(device)
        }
    }
}
```

### 8. Gestión de Modos de Operación

```kotlin
// Cambiar manualmente el modo de operación
sipLibrary.setOperationMode(
    EddysSipLibrary.OperationMode.PUSH, 
    pushToken = "nuevo_token_fcm"
)

sipLibrary.setOperationMode(EddysSipLibrary.OperationMode.FOREGROUND)

sipLibrary.setOperationMode(EddysSipLibrary.OperationMode.DISCONNECTED)

// Actualizar token de push
sipLibrary.updatePushToken("nuevo_token", "fcm")
```

### 9. Obtener Estado e Información

```kotlin
// Estado actual de la biblioteca
val status = sipLibrary.getCurrentStatus()
Log.d("SIP", "Initialized: ${status.isInitialized}")
Log.d("SIP", "Registration: ${status.registrationState}")
Log.d("SIP", "Call state: ${status.callState}")
Log.d("SIP", "Has active call: ${status.hasActiveCall}")
Log.d("SIP", "Is muted: ${status.isMuted}")
Log.d("SIP", "Current mode: ${status.currentMode}")
Log.d("SIP", "System healthy: ${status.systemHealth}")

// Historial de llamadas
val callLogs = sipLibrary.getCallLogs()
callLogs.forEach { log ->
    Log.d("SIP", "Call: ${log.from} -> ${log.to}, Duration: ${log.duration}s, Type: ${log.callType}")
}

// Limpiar historial
sipLibrary.clearCallLogs()

// Reporte de diagnóstico
val diagnostic = sipLibrary.getDiagnosticReport()
Log.d("SIP", "System health: ${diagnostic.systemHealth}")
Log.d("SIP", "Audio diagnosis: ${diagnostic.audioDiagnosis}")
Log.d("SIP", "WebSocket status: ${diagnostic.webSocketStatus}")
```

### 10. Configuración Dinámica

```kotlin
// Actualizar configuración en tiempo de ejecución
val newConfig = EddysSipLibrary.SipConfig(
    defaultDomain = "nuevo-servidor.com",
    webSocketUrl = "wss://nuevo-servidor.com:8089/ws",
    userAgent = "MiApp/2.0",
    enableAutoReconnect = true,
    autoSwitchToPushOnBackground = false, // Deshabilitar cambio automático
    preferredAudioDevice = "speaker" // Cambiar dispositivo preferido
)

sipLibrary.updateConfiguration(newConfig)
```

## Configuración Avanzada

### Configuración Completa

```kotlin
val config = EddysSipLibrary.SipConfig(
    // Configuración básica
    defaultDomain = "mi-servidor-sip.com",
    webSocketUrl = "wss://mi-servidor-sip.com:8089/ws",
    userAgent = "MiApp/1.0",
    enableLogs = true,
    
    // Configuración de conexión
    enableAutoReconnect = true,
    pingIntervalMs = 30000L,
    registrationExpirationSeconds = 3600,
    renewBeforeExpirationMs = 60000L,
    
    // Configuración automática de lifecycle
    autoSwitchToPushOnBackground = true,
    autoSwitchToForegroundOnResume = true,
    autoDisconnectWebSocketOnBackground = false,
    autoReconnectWebSocketOnForeground = true,
    
    // Configuración de contacto y headers
    customContactParams = mapOf(
        "app-version" to "1.0.0",
        "device-type" to "android"
    ),
    customSipHeaders = mapOf(
        "X-App-Name" to "MiApp",
        "X-Platform" to "Android"
    ),
    
    // Configuración de audio
    enableAutoAudioRouting = true,
    preferredAudioDevice = "earpiece",
    
    // Configuración de notificaciones push
    defaultPushProvider = "fcm",
    pushNotificationEnabled = true,
    
    // Configuración de timeouts
    callTimeoutMs = 30000L,
    registrationTimeoutMs = 10000L,
    webSocketConnectTimeoutMs = 5000L,
    
    // Configuración de reintentos
    maxReconnectAttempts = 3,
    reconnectDelayMs = 2000L,
    exponentialBackoff = true
)
```

## Manejo de Errores

```kotlin
override fun onLibraryError(error: String, exception: Throwable?) {
    when {
        error.contains("registration", ignoreCase = true) -> {
            // Error de registro
            handleRegistrationError(error)
        }
        error.contains("call", ignoreCase = true) -> {
            // Error de llamada
            handleCallError(error)
        }
        error.contains("websocket", ignoreCase = true) -> {
            // Error de conexión
            handleConnectionError(error)
        }
        else -> {
            // Error general
            Log.e("SIP", "General error: $error", exception)
        }
    }
}

private fun handleRegistrationError(error: String) {
    // Mostrar mensaje al usuario
    showErrorDialog("Error de registro: $error")
    
    // Intentar reconectar después de un delay
    Handler(Looper.getMainLooper()).postDelayed({
        sipLibrary.forceReconnect()
    }, 5000)
}
```

## Limpieza de Recursos

```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // Desregistrar cuentas
    sipLibrary.unregisterAccount("mi_usuario", "mi-servidor-sip.com")
    
    // Liberar recursos
    sipLibrary.dispose()
}
```

## Características Destacadas

### 🔄 Gestión Automática de Ciclo de Vida
- La biblioteca detecta automáticamente cuando la app pasa a segundo plano
- Cambia automáticamente a modo push para recibir notificaciones
- Reconecta automáticamente cuando la app vuelve a primer plano
- Gestiona eficientemente los recursos de WebSocket

### 🎵 Gestión Inteligente de Audio
- Detección automática de dispositivos de audio disponibles
- Cambio dinámico entre altavoz, auricular, Bluetooth, etc.
- Configuración automática del dispositivo preferido
- Gestión de permisos de audio

### 🔧 Configuración Flexible
- Headers SIP personalizados por cuenta y por llamada
- Timeouts configurables para diferentes operaciones
- Estrategias de reconexión personalizables
- Configuración de parámetros de contacto dinámicos

### 📊 Monitoreo y Diagnóstico
- Reportes detallados de salud del sistema
- Diagnóstico de problemas de audio
- Estado de conexiones WebSocket
- Métricas de llamadas y registro

## Licencia

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

## Soporte

Para soporte técnico, reportar bugs o solicitar nuevas características:
- Email: eddys.larez@example.com
- GitHub Issues: [https://github.com/eddyslarez/sip-library/issues](https://github.com/eddyslarez/sip-library/issues)

---

**Desarrollado con ❤️ por Eddys Larez**


[//]: # (# Eddys Larez SIP Library)

[//]: # ()
[//]: # (Una biblioteca SIP/VoIP para Android desarrollada por Eddys Larez, que proporciona funcionalidades completas para realizar y recibir llamadas SIP usando WebRTC y WebSocket.)

[//]: # ()
[//]: # (## 🚀 Características)

[//]: # ()
[//]: # (- ✅ Llamadas SIP entrantes y salientes)

[//]: # (- ✅ Soporte para WebRTC)

[//]: # (- ✅ Conexión WebSocket robusta con reconexión automática)

[//]: # (- ✅ Soporte para DTMF)

[//]: # (- ✅ Gestión de dispositivos de audio &#40;altavoz, auriculares, Bluetooth&#41;)

[//]: # (- ✅ Historial de llamadas)

[//]: # (- ✅ Notificaciones push)

[//]: # (- ✅ Estados de llamada reactivos con Flow)

[//]: # (- ✅ Arquitectura moderna con Kotlin)

[//]: # ()
[//]: # (## 📱 Instalación)

[//]: # ()
[//]: # (### Usando JitPack)

[//]: # ()
[//]: # (1. Agrega JitPack en tu `settings.gradle.kts` &#40;nivel proyecto&#41;:)

[//]: # ()
[//]: # (```kotlin)

[//]: # (dependencyResolutionManagement {)

[//]: # (    repositories {)

[//]: # (        google&#40;&#41;)

[//]: # (        mavenCentral&#40;&#41;)

[//]: # (        maven { url = uri&#40;"https://jitpack.io"&#41; })

[//]: # (    })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (2. Agrega la dependencia en tu `build.gradle.kts` &#40;nivel app&#41;:)

[//]: # ()
[//]: # (```kotlin)

[//]: # (dependencies {)

[//]: # (    implementation&#40;"com.github.eddyslarez:sip-library:1.0.0"&#41;)

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (### Desde GitHub directamente)

[//]: # ()
[//]: # (También puedes clonar el repositorio e incluir el módulo en tu proyecto:)

[//]: # ()
[//]: # (```bash)

[//]: # (git clone https://github.com/eddyslarez/sip-library.git)

[//]: # (```)

[//]: # ()
[//]: # (## 🛠️ Configuración)

[//]: # ()
[//]: # (### 1. Permisos)

[//]: # ()
[//]: # (Agrega estos permisos en tu `AndroidManifest.xml`:)

[//]: # ()
[//]: # (```xml)

[//]: # (<uses-permission android:name="android.permission.INTERNET" />)

[//]: # (<uses-permission android:name="android.permission.RECORD_AUDIO" />)

[//]: # (<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />)

[//]: # (<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />)

[//]: # (<uses-permission android:name="android.permission.BLUETOOTH" />)

[//]: # (<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />)

[//]: # (```)

[//]: # ()
[//]: # (### 2. Inicialización)

[//]: # ()
[//]: # (En tu `Application` clase:)

[//]: # ()
[//]: # (```kotlin)

[//]: # (class MyApplication : Application&#40;&#41; {)

[//]: # (    override fun onCreate&#40;&#41; {)

[//]: # (        super.onCreate&#40;&#41;)

[//]: # (        )
[//]: # (        // Configuración personalizada &#40;opcional&#41;)

[//]: # (        val config = EddysSipLibrary.SipConfig&#40;)

[//]: # (            defaultDomain = "tu-dominio.com",)

[//]: # (            webSocketUrl = "wss://tu-servidor:puerto/",)

[//]: # (            userAgent = "MiApp/1.0.0",)

[//]: # (            enableLogs = true)

[//]: # (        &#41;)

[//]: # (        )
[//]: # (        // Inicializar la biblioteca)

[//]: # (        EddysSipLibrary.getInstance&#40;&#41;.initialize&#40;this, config&#41;)

[//]: # (    })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (## 📋 Uso Básico)

[//]: # ()
[//]: # (### Registrar una cuenta SIP)

[//]: # ()
[//]: # (```kotlin)

[//]: # (val sipLibrary = EddysSipLibrary.getInstance&#40;&#41;)

[//]: # ()
[//]: # (sipLibrary.registerAccount&#40;)

[//]: # (    username = "usuario",)

[//]: # (    password = "contraseña",)

[//]: # (    domain = "mi-dominio.com", // opcional, usa el configurado por defecto)

[//]: # (    pushToken = "token_fcm", // opcional)

[//]: # (    pushProvider = "fcm" // fcm o apns)

[//]: # (&#41;)

[//]: # (```)

[//]: # ()
[//]: # (### Realizar una llamada)

[//]: # ()
[//]: # (```kotlin)

[//]: # (sipLibrary.makeCall&#40;"1234567890"&#41;)

[//]: # (```)

[//]: # ()
[//]: # (### Responder/Rechazar llamadas)

[//]: # ()
[//]: # (```kotlin)

[//]: # (// Aceptar llamada entrante)

[//]: # (sipLibrary.acceptCall&#40;&#41;)

[//]: # ()
[//]: # (// Rechazar llamada entrante)

[//]: # (sipLibrary.declineCall&#40;&#41;)

[//]: # ()
[//]: # (// Terminar llamada actual)

[//]: # (sipLibrary.endCall&#40;&#41;)

[//]: # (```)

[//]: # ()
[//]: # (### Funciones durante la llamada)

[//]: # ()
[//]: # (```kotlin)

[//]: # (// Silenciar/desmute)

[//]: # (sipLibrary.toggleMute&#40;&#41;)

[//]: # ()
[//]: # (// Verificar si está silenciado)

[//]: # (val isMuted = sipLibrary.isMuted&#40;&#41;)

[//]: # ()
[//]: # (// Enviar DTMF)

[//]: # (sipLibrary.sendDtmf&#40;'1'&#41;)

[//]: # (sipLibrary.sendDtmfSequence&#40;"123*"&#41;)

[//]: # ()
[//]: # (// Poner en espera)

[//]: # (sipLibrary.holdCall&#40;&#41;)

[//]: # (sipLibrary.resumeCall&#40;&#41;)

[//]: # (```)

[//]: # ()
[//]: # (### Gestión de audio)

[//]: # ()
[//]: # (```kotlin)

[//]: # (// Obtener dispositivos disponibles)

[//]: # (val &#40;inputDevices, outputDevices&#41; = sipLibrary.getAudioDevices&#40;&#41;)

[//]: # ()
[//]: # (// Cambiar dispositivo de salida)

[//]: # (outputDevices.forEach { device ->)

[//]: # (    if &#40;device.name.contains&#40;"Bluetooth"&#41;&#41; {)

[//]: # (        sipLibrary.changeAudioOutput&#40;device&#41;)

[//]: # (    })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (## 🔄 Observar Estados)

[//]: # ()
[//]: # (### Estados de llamada)

[//]: # ()
[//]: # (```kotlin)

[//]: # (class MainActivity : ComponentActivity&#40;&#41; {)

[//]: # (    private lateinit var sipLibrary: EddysSipLibrary)

[//]: # (    )
[//]: # (    override fun onCreate&#40;savedInstanceState: Bundle?&#41; {)

[//]: # (        super.onCreate&#40;savedInstanceState&#41;)

[//]: # (        )
[//]: # (        sipLibrary = EddysSipLibrary.getInstance&#40;&#41;)

[//]: # (        )
[//]: # (        // Observar cambios de estado de llamada)

[//]: # (        lifecycleScope.launch {)

[//]: # (            sipLibrary.getCallStateFlow&#40;&#41;.collect { callState ->)

[//]: # (                when &#40;callState&#41; {)

[//]: # (                    CallState.INCOMING -> {)

[//]: # (                        // Llamada entrante)

[//]: # (                        showIncomingCallUI&#40;&#41;)

[//]: # (                    })

[//]: # (                    CallState.CONNECTED -> {)

[//]: # (                        // Llamada conectada)

[//]: # (                        showInCallUI&#40;&#41;)

[//]: # (                    })

[//]: # (                    CallState.ENDED -> {)

[//]: # (                        // Llamada terminada)

[//]: # (                        showMainUI&#40;&#41;)

[//]: # (                    })

[//]: # (                    else -> {)

[//]: # (                        // Otros estados)

[//]: # (                    })

[//]: # (                })

[//]: # (            })

[//]: # (        })

[//]: # (    })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (### Estados de registro)

[//]: # ()
[//]: # (```kotlin)

[//]: # (lifecycleScope.launch {)

[//]: # (    sipLibrary.getRegistrationStateFlow&#40;&#41;.collect { registrationState ->)

[//]: # (        when &#40;registrationState&#41; {)

[//]: # (            RegistrationState.OK -> {)

[//]: # (                // Registrado exitosamente)

[//]: # (                updateUI&#40;"Conectado"&#41;)

[//]: # (            })

[//]: # (            RegistrationState.FAILED -> {)

[//]: # (                // Error en registro)

[//]: # (                updateUI&#40;"Error de conexión"&#41;)

[//]: # (            })

[//]: # (            else -> {)

[//]: # (                // Otros estados)

[//]: # (            })

[//]: # (        })

[//]: # (    })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (## 📞 Historial de Llamadas)

[//]: # ()
[//]: # (```kotlin)

[//]: # (// Obtener todas las llamadas)

[//]: # (val callLogs = sipLibrary.getCallLogs&#40;&#41;)

[//]: # ()
[//]: # (// Obtener solo llamadas perdidas)

[//]: # (val missedCalls = sipLibrary.getMissedCalls&#40;&#41;)

[//]: # ()
[//]: # (// Limpiar historial)

[//]: # (sipLibrary.clearCallLogs&#40;&#41;)

[//]: # ()
[//]: # (// Buscar llamadas de un número específico)

[//]: # (val callsFromNumber = sipLibrary.getCallLogsForNumber&#40;"1234567890"&#41;)

[//]: # (```)

[//]: # ()
[//]: # (## 🔧 Configuración Avanzada)

[//]: # ()
[//]: # (### Callbacks personalizados)

[//]: # ()
[//]: # (```kotlin)

[//]: # (sipLibrary.setCallbacks&#40;object : EddysSipLibrary.SipCallbacks {)

[//]: # (    override fun onCallTerminated&#40;&#41; {)

[//]: # (        // Llamada terminada)

[//]: # (    })

[//]: # (    )
[//]: # (    override fun onCallStateChanged&#40;state: CallState&#41; {)

[//]: # (        // Estado de llamada cambió)

[//]: # (    })

[//]: # (    )
[//]: # (    override fun onRegistrationStateChanged&#40;state: RegistrationState&#41; {)

[//]: # (        // Estado de registro cambió)

[//]: # (    })

[//]: # (    )
[//]: # (    override fun onIncomingCall&#40;callerNumber: String, callerName: String?&#41; {)

[//]: # (        // Llamada entrante)

[//]: # (        showNotification&#40;"Llamada de $callerNumber"&#41;)

[//]: # (    })

[//]: # (}&#41;)

[//]: # (```)

[//]: # ()
[//]: # (### Diagnóstico y salud del sistema)

[//]: # ()
[//]: # (```kotlin)

[//]: # (// Verificar si el sistema está saludable)

[//]: # (val isHealthy = sipLibrary.isSystemHealthy&#40;&#41;)

[//]: # ()
[//]: # (// Obtener reporte detallado)

[//]: # (val healthReport = sipLibrary.getSystemHealthReport&#40;&#41;)

[//]: # (println&#40;healthReport&#41;)

[//]: # (```)

[//]: # ()
[//]: # (## 🌟 Características Avanzadas)

[//]: # ()
[//]: # (### Soporte para múltiples dominios)

[//]: # ()
[//]: # (```kotlin)

[//]: # (// Configurar diferentes servidores según el dominio)

[//]: # (val config = EddysSipLibrary.SipConfig&#40;)

[//]: # (    defaultDomain = "dominio",)

[//]: # (    webSocketUrl = "wss://dominio:XXXXXX/")

[//]: # (&#41;)

[//]: # ()
[//]: # (// registro)

[//]: # (sipLibrary.registerAccount&#40;)

[//]: # (    username = "usuario",)

[//]: # (    password = "contraseña",)

[//]: # (    domain = "dominio")

[//]: # (&#41;)

[//]: # (```)

[//]: # ()
[//]: # (### Reconexión automática)

[//]: # ()
[//]: # (La biblioteca maneja automáticamente:)

[//]: # (- ✅ Reconexión de WebSocket)

[//]: # (- ✅ Re-registro SIP)

[//]: # (- ✅ Manejo de cambios de red)

[//]: # (- ✅ Keepalive con ping/pong)

[//]: # ()
[//]: # (### Soporte para notificaciones push)

[//]: # ()
[//]: # (```kotlin)

[//]: # (// Actualizar token de push)

[//]: # (sipLibrary.updatePushToken&#40;"nuevo_token_fcm", "fcm"&#41;)

[//]: # (```)

[//]: # ()
[//]: # (## 🐛 Solución de Problemas)

[//]: # ()
[//]: # (### Problemas comunes)

[//]: # ()
[//]: # (1. **Error de permisos de audio**:)

[//]: # (   ```kotlin)

[//]: # (   // Solicitar permisos antes de usar)

[//]: # (   if &#40;ContextCompat.checkSelfPermission&#40;this, Manifest.permission.RECORD_AUDIO&#41; )

[//]: # (       != PackageManager.PERMISSION_GRANTED&#41; {)

[//]: # (       ActivityCompat.requestPermissions&#40;this, )

[//]: # (           arrayOf&#40;Manifest.permission.RECORD_AUDIO&#41;, 1&#41;)

[//]: # (   })

[//]: # (   ```)

[//]: # ()
[//]: # (2. **Problemas de conexión**:)

[//]: # (   ```kotlin)

[//]: # (   // Verificar estado de salud)

[//]: # (   val healthReport = sipLibrary.getSystemHealthReport&#40;&#41;)

[//]: # (   Log.d&#40;"SIP", healthReport&#41;)

[//]: # (   ```)

[//]: # ()
[//]: # (3. **Audio no funciona**:)

[//]: # (   ```kotlin)

[//]: # (   // Verificar dispositivos disponibles)

[//]: # (   val &#40;input, output&#41; = sipLibrary.getAudioDevices&#40;&#41;)

[//]: # (   Log.d&#40;"Audio", "Input devices: $input"&#41;)

[//]: # (   Log.d&#40;"Audio", "Output devices: $output"&#41;)

[//]: # (   ```)

[//]: # ()
[//]: # (## 📄 Licencia)

[//]: # ()
[//]: # (Desarrollado por **Eddys Larez**)

[//]: # ()
[//]: # (Este proyecto es de código abierto y está disponible bajo la licencia MIT.)

[//]: # ()
[//]: # (## 🤝 Contribución)

[//]: # ()
[//]: # (Las contribuciones son bienvenidas. Por favor:)

[//]: # ()
[//]: # (1. Fork el proyecto)

[//]: # (2. Crea una rama para tu feature &#40;`git checkout -b feature/nueva-caracteristica`&#41;)

[//]: # (3. Commit tus cambios &#40;`git commit -am 'Agregar nueva característica'`&#41;)

[//]: # (4. Push a la rama &#40;`git push origin feature/nueva-caracteristica`&#41;)

[//]: # (5. Abre un Pull Request)

[//]: # ()
[//]: # (## 📞 Soporte)

[//]: # ()
[//]: # (Para soporte técnico o preguntas:)

[//]: # ()
[//]: # (- GitHub Issues: [Reportar un problema]&#40;https://github.com/eddyslarez/sip-library/issues&#41;)

[//]: # (- Email: eddyslarez@example.com)

[//]: # ()
[//]: # (## 🔄 Changelog)

[//]: # ()
[//]: # (### v1.0.0)

[//]: # (- ✅ Lanzamiento inicial)

[//]: # (- ✅ Soporte completo para SIP/WebRTC)

[//]: # (- ✅ Gestión de llamadas)

[//]: # (- ✅ Historial de llamadas)

[//]: # (- ✅ Soporte para DTMF)

[//]: # (- ✅ Gestión de audio)

[//]: # (- ✅ Estados reactivos)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (**Desarrollado con ❤️ por Eddys Larez**)