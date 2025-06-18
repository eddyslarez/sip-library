package com.eddyslarez.siplibrary.translation

import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.core.SipEventDispatcher
import com.eddyslarez.siplibrary.data.services.audio.WebRtcManager
import com.eddyslarez.siplibrary.utils.log
import com.shepeliev.webrtckmp.AudioStreamTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Gestor principal de traducción en tiempo real
 *
 * @author Eddys Larez
 */
class RealtimeTranslationManager(
    private val webRtcManager: WebRtcManager,
    private val eventDispatcher: SipEventDispatcher
) {
    private val TAG = "RealtimeTranslationManager"

    private var translationConfig: TranslationConfig? = null
    private var openAiClient: OpenAIRealtimeClient? = null
    private var audioProcessor: AudioTranslationProcessor? = null

    private var isTranslationActive = false
    private var isConnectedToOpenAI = false

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val statistics = TranslationStatistics()

    // Audio handling
    private val audioBufferMutex = Mutex()
    private val audioQueue = mutableListOf<ByteArray>()
    private var isProcessingAudio = false

    // WebRTC audio capturing
    private var originalAudioTrack: AudioStreamTrack? = null
    private var translatedAudioTrack: AudioStreamTrack? = null

    /**
     * Inicializa la traducción en tiempo real
     */
    suspend fun initialize(config: TranslationConfig): Boolean {
        return try {
            log.d(tag = TAG) { "Initializing real-time translation..." }

            if (!config.isEnabled) {
                log.d(tag = TAG) { "Translation is disabled in config" }
                return false
            }

            if (config.openAiApiKey.isBlank()) {
                log.e(tag = TAG) { "OpenAI API key is required" }
                eventDispatcher.onError(EddysSipLibrary.SipError(
                    code = 5001,
                    message = "OpenAI API key is required for translation",
                    category = EddysSipLibrary.ErrorCategory.CONFIGURATION
                ))
                return false
            }

            translationConfig = config

            // Inicializar procesador de audio
            audioProcessor = AudioTranslationProcessor(config) { translatedAudio ->
                coroutineScope.launch {
                    playTranslatedAudio(translatedAudio)
                }
            }

            // Inicializar cliente OpenAI
            openAiClient = OpenAIRealtimeClient(config) { event ->
                coroutineScope.launch {
                    handleOpenAIEvent(event)
                }
            }

            log.d(tag = TAG) { "Real-time translation initialized successfully" }
            true
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error initializing translation: ${e.message}" }
            eventDispatcher.onError(EddysSipLibrary.SipError(
                code = 5002,
                message = "Failed to initialize translation: ${e.message}",
                category = EddysSipLibrary.ErrorCategory.CONFIGURATION
            ))
            false
        }
    }

    /**
     * Inicia la traducción durante una llamada activa
     */
    suspend fun startTranslation(): Boolean {
        return try {
            log.d(tag = TAG) { "Starting real-time translation..." }

            if (translationConfig?.isEnabled != true) {
                log.w(tag = TAG) { "Translation is not enabled" }
                return false
            }

            if (isTranslationActive) {
                log.w(tag = TAG) { "Translation is already active" }
                return true
            }

            // Conectar a OpenAI
            val connected = openAiClient?.connect() ?: false
            if (!connected) {
                log.e(tag = TAG) { "Failed to connect to OpenAI Realtime API" }
                return false
            }

            isConnectedToOpenAI = true

            // Interceptar audio de WebRTC
            setupAudioInterception()

            isTranslationActive = true

            // Notificar evento
            eventDispatcher.onTranslationStateChanged(true, translationConfig!!.sourceLanguage, translationConfig!!.targetLanguage)

            log.d(tag = TAG) { "Real-time translation started successfully" }
            true
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error starting translation: ${e.message}" }
            eventDispatcher.onError(EddysSipLibrary.SipError(
                code = 5003,
                message = "Failed to start translation: ${e.message}",
                category = EddysSipLibrary.ErrorCategory.SIP_PROTOCOL
            ))
            false
        }
    }

    /**
     * Detiene la traducción
     */
    suspend fun stopTranslation() {
        try {
            log.d(tag = TAG) { "Stopping real-time translation..." }

            isTranslationActive = false

            // Desconectar de OpenAI
            openAiClient?.disconnect()
            isConnectedToOpenAI = false

            // Restaurar audio original
            restoreOriginalAudio()

            // Limpiar buffers
            audioProcessor?.clearBuffers()
            audioBufferMutex.withLock {
                audioQueue.clear()
            }

            // Notificar evento
            eventDispatcher.onTranslationStateChanged(false, null, null)

            log.d(tag = TAG) { "Real-time translation stopped" }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error stopping translation: ${e.message}" }
        }
    }

    /**
     * Configura la interceptación de audio de WebRTC
     */
    private suspend fun setupAudioInterception() {
        try {
            log.d(tag = TAG) { "Setting up audio interception..." }

            // Aquí necesitarías modificar el WebRtcManager para permitir interceptar el audio
            // Por ahora, simularemos con un callback cuando se reciba audio

            setupLocalAudioCapture()
            setupRemoteAudioCapture()

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error setting up audio interception: ${e.message}" }
        }
    }

    private suspend fun setupLocalAudioCapture() {
        // Interceptar audio local (micrófono) para enviar a OpenAI
        coroutineScope.launch {
            while (isTranslationActive) {
                try {
                    // En una implementación real, capturarías el audio del micrófono
                    // antes de enviarlo por WebRTC
                    delay(20) // 20ms chunks
                } catch (e: Exception) {
                    log.e(tag = TAG) { "Error in local audio capture: ${e.message}" }
                }
            }
        }
    }

    private suspend fun setupRemoteAudioCapture() {
        // Interceptar audio remoto para enviar a OpenAI y reemplazar con traducción
        coroutineScope.launch {
            while (isTranslationActive) {
                try {
                    // En una implementación real, interceptarías el audio remoto
                    // antes de reproducirlo
                    delay(20) // 20ms chunks
                } catch (e: Exception) {
                    log.e(tag = TAG) { "Error in remote audio capture: ${e.message}" }
                }
            }
        }
    }

    /**
     * Procesa audio capturado del micrófono local
     */
    suspend fun processLocalAudio(audioData: ByteArray) {
        if (!isTranslationActive || !isConnectedToOpenAI) return

        try {
            // Procesar audio y enviarlo a OpenAI
            val processedAudio = audioProcessor?.processIncomingAudio(audioData)
            if (processedAudio != null) {
                openAiClient?.sendAudioChunk(processedAudio)

                // Enviar el audio original por WebRTC (sin traducir) para el caso local
                // ya que el usuario debe escuchar su propia voz normal
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error processing local audio: ${e.message}" }
        }
    }

    /**
     * Procesa audio recibido del peer remoto
     */
    suspend fun processRemoteAudio(audioData: ByteArray) {
        if (!isTranslationActive || !isConnectedToOpenAI) {
            // Si la traducción no está activa, pasar el audio tal como está
            playOriginalAudio(audioData)
            return
        }

        try {
            // En lugar de reproducir el audio original, enviarlo a OpenAI para traducir
            val processedAudio = audioProcessor?.processIncomingAudio(audioData)
            if (processedAudio != null) {
                // Aquí podrías implementar lógica para detectar si el audio remoto
                // es diferente del audio local para evitar loops
                openAiClient?.sendAudioChunk(processedAudio)

                // No reproducir el audio original, esperar la traducción
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error processing remote audio: ${e.message}" }
            // En caso de error, reproducir audio original
            playOriginalAudio(audioData)
        }
    }

    /**
     * Maneja eventos de OpenAI Realtime API
     */
    private suspend fun handleOpenAIEvent(event: OpenAIRealtimeClient.RealtimeEvent) {
        when (event) {
            is OpenAIRealtimeClient.RealtimeEvent.SessionCreated -> {
                log.d(tag = TAG) { "OpenAI session created: ${event.sessionId}" }
            }

            is OpenAIRealtimeClient.RealtimeEvent.SpeechStarted -> {
                log.d(tag = TAG) { "Speech detection started" }
                eventDispatcher.onTranslationSpeechDetected(true)
            }

            is OpenAIRealtimeClient.RealtimeEvent.SpeechStopped -> {
                log.d(tag = TAG) { "Speech detection stopped" }
                eventDispatcher.onTranslationSpeechDetected(false)

                // Confirmar el buffer de audio y generar traducción
                openAiClient?.commitAudioBuffer()
                openAiClient?.generateTranslation()
            }

            is OpenAIRealtimeClient.RealtimeEvent.TranslatedAudio -> {
                log.d(tag = TAG) { "Received translated audio: ${event.audioData.size} bytes" }

                // Procesar y reproducir audio traducido
                audioProcessor?.processTranslatedAudio(event.audioData)

                // Actualizar estadísticas
                updateStatistics(event.audioData.size)
            }

            is OpenAIRealtimeClient.RealtimeEvent.TranscriptDelta -> {
                log.d(tag = TAG) { "Translation transcript: ${event.text}" }
                eventDispatcher.onTranslationTranscript(event.text)
            }

            is OpenAIRealtimeClient.RealtimeEvent.TranslationComplete -> {
                log.d(tag = TAG) { "Translation completed" }
                eventDispatcher.onTranslationCompleted()
            }

            is OpenAIRealtimeClient.RealtimeEvent.Error -> {
                log.e(tag = TAG) { "OpenAI error: ${event.message}" }
                eventDispatcher.onError(EddysSipLibrary.SipError(
                    code = 5004,
                    message = "OpenAI translation error: ${event.message}",
                    category = EddysSipLibrary.ErrorCategory.SIP_PROTOCOL
                ))
            }

            is OpenAIRealtimeClient.RealtimeEvent.Disconnected -> {
                log.w(tag = TAG) { "OpenAI disconnected: ${event.code} - ${event.reason}" }
                isConnectedToOpenAI = false

                if (isTranslationActive) {
                    // Intentar reconectar
                    coroutineScope.launch {
                        delay(2000)
                        if (isTranslationActive) {
                            openAiClient?.connect()
                        }
                    }
                }
            }

            else -> {
                log.d(tag = TAG) { "Unhandled OpenAI event: $event" }
            }
        }
    }

    /**
     * Reproduce audio traducido
     */
    private suspend fun playTranslatedAudio(audioData: ByteArray) {
        try {
            // Aquí enviarías el audio traducido al sistema de audio de WebRTC
            // En lugar del audio original del peer remoto

            // Simular reproducción (en implementación real, inyectarías al audio stream)
            log.d(tag = TAG) { "Playing translated audio: ${audioData.size} bytes" }

            // Notificar que se está reproduciendo audio traducido
            eventDispatcher.onTranslationAudioPlaying(audioData.size)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error playing translated audio: ${e.message}" }
        }
    }

    /**
     * Reproduce audio original (cuando traducción no está activa)
     */
    private suspend fun playOriginalAudio(audioData: ByteArray) {
        try {
            // Reproducir audio original normalmente
            log.d(tag = TAG) { "Playing original audio: ${audioData.size} bytes" }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error playing original audio: ${e.message}" }
        }
    }

    /**
     * Restaura el audio original
     */
    private suspend fun restoreOriginalAudio() {
        try {
            log.d(tag = TAG) { "Restoring original audio routing..." }

            // Restaurar routing de audio normal
            // Esto dependería de la implementación específica de WebRTC

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error restoring original audio: ${e.message}" }
        }
    }

    /**
     * Actualiza estadísticas de traducción
     */
    private fun updateStatistics(audioSize: Int) {
        // Update translation statistics
        val newStats = statistics.copy(
            translationsProcessed = statistics.translationsProcessed + 1,
            lastTranslationTime = Clock.System.now().toEpochMilliseconds(),
            totalAudioProcessed = statistics.totalAudioProcessed + audioSize
        )

        // Dispatch statistics event
        coroutineScope.launch {
            eventDispatcher.onTranslationStatistics(newStats)
        }
    }

    /**
     * Cambia idiomas de traducción dinámicamente
     */
    suspend fun changeLanguages(sourceLanguage: TranslationLanguage, targetLanguage: TranslationLanguage): Boolean {
        return try {
            if (!isTranslationActive) {
                // Solo actualizar configuración
                translationConfig = translationConfig?.copy(
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )
                true
            } else {
                // Reiniciar sesión con nuevos idiomas
                stopTranslation()
                delay(1000)

                translationConfig = translationConfig?.copy(
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )

                startTranslation()
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error changing languages: ${e.message}" }
            false
        }
    }

    /**
     * Cambia la voz de traducción
     */
    suspend fun changeVoice(voiceStyle: VoiceStyle): Boolean {
        return try {
            translationConfig = translationConfig?.copy(voiceStyle = voiceStyle)

            if (isTranslationActive) {
                // Reinicializar sesión OpenAI con nueva voz
                openAiClient?.disconnect()
                delay(500)
                openAiClient?.connect()
            }

            true
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error changing voice: ${e.message}" }
            false
        }
    }

    /**
     * Obtiene el estado actual de la traducción
     */
    fun getTranslationState(): TranslationState {
        return TranslationState(
            isActive = isTranslationActive,
            isConnectedToOpenAI = isConnectedToOpenAI,
            sourceLanguage = translationConfig?.sourceLanguage,
            targetLanguage = translationConfig?.targetLanguage,
            voiceStyle = translationConfig?.voiceStyle,
            statistics = statistics
        )
    }

    /**
     * Obtiene la configuración actual
     */
    fun getCurrentConfig(): TranslationConfig? = translationConfig

    /**
     * Verifica si la traducción está disponible
     */
    fun isTranslationAvailable(): Boolean {
        return translationConfig?.isEnabled == true &&
                translationConfig?.openAiApiKey?.isNotBlank() == true
    }

    /**
     * Dispose resources
     */
    fun dispose() {
        coroutineScope.launch {
            stopTranslation()
        }

        coroutineScope.cancel()
        audioProcessor?.dispose()
        openAiClient = null
        audioProcessor = null
        translationConfig = null
    }

    data class TranslationState(
        val isActive: Boolean,
        val isConnectedToOpenAI: Boolean,
        val sourceLanguage: TranslationLanguage?,
        val targetLanguage: TranslationLanguage?,
        val voiceStyle: VoiceStyle?,
        val statistics: TranslationStatistics
    )
}

// Extensiones para SipEventDispatcher
suspend fun SipEventDispatcher.onTranslationStateChanged(
    isActive: Boolean,
    sourceLanguage: TranslationLanguage?,
    targetLanguage: TranslationLanguage?
) {
    // Implementar evento de cambio de estado de traducción
}

suspend fun SipEventDispatcher.onTranslationSpeechDetected(isDetected: Boolean) {
    // Implementar evento de detección de voz
}

suspend fun SipEventDispatcher.onTranslationTranscript(text: String) {
    // Implementar evento de transcripción
}

suspend fun SipEventDispatcher.onTranslationCompleted() {
    // Implementar evento de traducción completada
}

suspend fun SipEventDispatcher.onTranslationAudioPlaying(audioSize: Int) {
    // Implementar evento de reproducción de audio traducido
}

suspend fun SipEventDispatcher.onTranslationStatistics(statistics: TranslationStatistics) {
    // Implementar evento de estadísticas de traducción
}