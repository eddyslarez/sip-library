// Procesamiento de audio
val normalizedAudio = AudioUtils.normalizeAudio(audioData)
val mixedAudio = AudioUtils.mixAudio(audio1, audio2, 0.5f)
val silenceDetected = AudioUtils.isSilence(audioData)

// Crear instancias de audio
val audioRecord = AudioUtils.createAudioRecord()
val audioTrack = AudioUtils.createAudioTrack()
```

## Configuración de OpenAI

### Obtener API Key

1. Registrarse en [OpenAI](https://platform.openai.com)
2. Crear una API key en la sección "API Keys"
3. Asegurarse de tener créditos suficientes
4. La API Realtime tiene costos por minuto de uso

### Límites y Costos

- **Modelo**: gpt-4o-realtime-preview
- **Formato de audio**: PCM16 a 16kHz
- **Latencia**: ~200-500ms típicamente
- **Costo**: Variable según uso (consultar precios de OpenAI)

## Ejemplo Completo

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var sipLibrary: TranslationEnabledSipLibrary
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configurar SIP
        val sipConfig = EddysSipLibrary.SipConfig(
            defaultDomain = "tu-dominio.com",
            webSocketUrl = "wss://tu-servidor.com/ws"
        )
        
        // Configurar traducción
        val translationConfig = TranslationConfigurationBuilder()
            .enable()
            .myLanguage(Language.SPANISH)
            .autoDetectRemoteLanguage()
            .voiceGender(VoiceGender.FEMALE)
            .openAiApiKey("tu-api-key")
            .build()
        
        // Inicializar
        sipLibrary = EddysSipLibrary.getInstance().withRealTimeTranslation()
        sipLibrary.initialize(application, sipConfig, eventListener)
        sipLibrary.configureTranslation(translationConfig)
        
        setContent {
            TranslationControlUI()
        }
    }
    
    @Composable
    fun TranslationControlUI() {
        var translationActive by remember { mutableStateOf(false) }
        
        Column {
            Button(
                onClick = {
                    translationActive = sipLibrary.toggleTranslation()
                }
            ) {
                Text(if (translationActive) "Desactivar Traducción" else "Activar Traducción")
            }
            
            if (translationActive) {
                Text("🌐 Traducción activa", color = Color.Green)
            }
        }
    }
    
    private val eventListener = object : TranslationSipEventListener {
        override fun onTranslationEvent(event: TranslationSipEvent) {
            runOnUiThread {
                handleTranslationEvent(event)
            }
        }
        
        override fun onCallConnected(callId: String, duration: Long) {
            // Auto-habilitar traducción en llamadas
            sipLibrary.enableTranslationForCurrentCall()
        }
    }
}
```

## Solución de Problemas

### Problemas Comunes

1. **API Key inválida**: Verificar que la key de OpenAI sea correcta
2. **Sin audio**: Verificar permisos de micrófono
3. **Latencia alta**: Verificar conexión a internet
4. **Idioma no detectado**: Usar configuración manual en lugar de auto-detección

### Logs de Debug

```kotlin
// Habilitar logs detallados
val config = sipConfig.copy(enableLogs = true)

// Ver estadísticas de traducción
val stats = sipLibrary.getTranslationStatistics()
println("Translation stats: $stats")

// Estado del sistema
val healthReport = sipLibrary.getSystemHealthReport()
println(healthReport)
```

## Limitaciones

1. **Dependencia de internet**: Requiere conexión estable para OpenAI
2. **Latencia**: Añade ~200-500ms de latencia a la conversación
3. **Costos**: Uso de la API de OpenAI tiene costos asociados
4. **Idiomas**: Limitado a idiomas soportados por OpenAI Realtime
5. **Calidad**: Depende de la calidad del audio original y la conexión

## Roadmap

- [ ] Soporte para traducción offline
- [ ] Optimización de latencia
- [ ] Más opciones de voz
- [ ] Integración con otros servicios de traducción
- [ ] Mejora en detección de idiomas
- [ ] Soporte para dialectos regionales