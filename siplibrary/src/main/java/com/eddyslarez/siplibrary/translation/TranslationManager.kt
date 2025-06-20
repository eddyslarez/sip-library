package com.eddyslarez.siplibrary.translation

import com.eddyslarez.siplibrary.core.GlobalEventBus
import com.eddyslarez.siplibrary.error.ErrorCategory
import com.eddyslarez.siplibrary.error.ErrorCodes
import com.eddyslarez.siplibrary.error.SipLibraryException
import com.eddyslarez.siplibrary.events.SipEvent
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gestor de traducción independiente
 * Puede inicializarse en cualquier parte de la aplicación
 *
 * @author Eddys Larez
 */
class TranslationManager private constructor() {

    private val TAG = "TranslationManager"

    private var translationConfig: TranslationConfig? = null
    private var openAiClient: OpenAIRealtimeClient? = null
    private var audioProcessor: AudioTranslationProcessor? = null

    private var isInitialized = false
    private var isTranslationActive = false
    private var isConnectedToOpenAI = false

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val statistics = TranslationStatistics()

    companion object {
        @Volatile
        private var INSTANCE: TranslationManager? = null

        /**
         * Obtiene la instancia singleton del gestor de traducción
         */
        fun getInstance(): TranslationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TranslationManager().also { INSTANCE = it }
            }
        }
    }

    /**
     * Inicializa el sistema de traducción de manera independiente
     */
    suspend fun initialize(config: TranslationConfig): Result<Unit> {
        return try {
            if (isInitialized) {
                return Result.success(Unit)
            }

            log.d(tag = TAG) { "Initializing TranslationManager..." }

            if (!config.isEnabled) {
                log.d(tag = TAG) { "Translation is disabled in config" }
                return Result.success(Unit)
            }

            if (config.openAiApiKey.isBlank()) {
                val error = TranslationError(
                    code = ErrorCodes.TRANSLATION_CONFIG_INVALID,
                    message = "OpenAI API key is required for translation",
                    category = TranslationErrorCategory.CONFIGURATION
                )
                GlobalEventBus.emit(SipEvent.TranslationError(error))
                return Result.failure(SipLibraryException("OpenAI API key required", error.code, ErrorCategory.TRANSLATION))
            }

            translationConfig = config

            // Inicializar procesador de audio
            audioProcessor = AudioTranslationProcessor(config) { translatedAudio ->
                coroutineScope.launch {
                    processTranslatedAudio(translatedAudio)
                }
            }

            // Inicializar cliente OpenAI
            openAiClient = OpenAIRealtimeClient(config) { event ->
                coroutineScope.launch {
                    handleOpenAIEvent(event)
                }
            }

            isInitialized = true

            GlobalEventBus.emit(SipEvent.TranslationStateChanged(false, config.sourceLanguage, config.targetLanguage))

            log.d(tag = TAG) { "TranslationManager initialized successfully" }
            Result.success(Unit)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error initializing translation: ${e.message}" }

            val error = TranslationError(
                code = ErrorCodes.TRANSLATION_INIT_FAILED,
                message = "Failed to initialize translation: ${e.message}",
                category = TranslationErrorCategory.CONFIGURATION
            )

            GlobalEventBus.emit(SipEvent.TranslationError(error))
            Result.failure(SipLibraryException("Failed to initialize translation", error.code, ErrorCategory.TRANSLATION, e))
        }
    }

    /**
     * Inicia la traducción en tiempo real
     */
    suspend fun startTranslation(): Result<Unit> {
        return try {
            log.d(tag = TAG) { "Starting real-time translation..." }

            if (!isInitialized) {
                val error = TranslationError(
                    code = ErrorCodes.NOT_INITIALIZED,
                    message = "Translation manager not initialized",
                    category = TranslationErrorCategory.CONFIGURATION
                )
                GlobalEventBus.emit(SipEvent.TranslationError(error))
                return Result.failure(SipLibraryException("Translation not initialized", error.code, ErrorCategory.TRANSLATION))
            }

            if (translationConfig?.isEnabled != true) {
                log.w(tag = TAG) { "Translation is not enabled" }
                return Result.success(Unit)
            }

            if (isTranslationActive) {
                log.w(tag = TAG) { "Translation is already active" }
                return Result.success(Unit)
            }

            // Conectar a OpenAI
            val connected = openAiClient?.connect() ?: false
            if (!connected) {
                val error = TranslationError(
                    code = ErrorCodes.TRANSLATION_API_FAILED,
                    message = "Failed to connect to OpenAI Realtime API",
                    category = TranslationErrorCategory.NETWORK_CONNECTION
                )
                GlobalEventBus.emit(SipEvent.TranslationError(error))
                return Result.failure(SipLibraryException("Failed to connect to OpenAI", error.code, ErrorCategory.TRANSLATION))
            }

            isConnectedToOpenAI = true
            isTranslationActive = true

            GlobalEventBus.emit(SipEvent.TranslationStateChanged(
                true,
                translationConfig!!.sourceLanguage,
                translationConfig!!.targetLanguage
            ))

            log.d(tag = TAG) { "Real-time translation started successfully" }
            Result.success(Unit)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error starting translation: ${e.message}" }

            val error = TranslationError(
                code = ErrorCodes.TRANSLATION_API_FAILED,
                message = "Failed to start translation: ${e.message}",
                category = TranslationErrorCategory.NETWORK_CONNECTION
            )

            GlobalEventBus.emit(SipEvent.TranslationError(error))
            Result.failure(SipLibraryException("Failed to start translation", error.code, ErrorCategory.TRANSLATION, e))
        }
    }

    /**
     * Detiene la traducción
     */
    suspend fun stopTranslation(): Result<Unit> {
        return try {
            log.d(tag = TAG) { "Stopping real-time translation..." }

            isTranslationActive = false

            // Desconectar de OpenAI
            openAiClient?.disconnect()
            isConnectedToOpenAI = false

            // Limpiar buffers
            audioProcessor?.clearBuffers()

            GlobalEventBus.emit(SipEvent.TranslationStateChanged(false, null, null))

            log.d(tag = TAG) { "Real-time translation stopped" }
            Result.success(Unit)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error stopping translation: ${e.message}" }
            Result.failure(SipLibraryException("Failed to stop translation", ErrorCodes.UNEXPECTED_ERROR, ErrorCategory.TRANSLATION, e))
        }
    }

    /**
     * Procesa audio para traducción
     */
    suspend fun processAudioForTranslation(audioData: ByteArray): Result<Unit> {
        return try {
            if (!isTranslationActive || !isConnectedToOpenAI) {
                return Result.success(Unit)
            }

            val processedAudio = audioProcessor?.processIncomingAudio(audioData)
            if (processedAudio != null) {
                openAiClient?.sendAudioChunk(processedAudio)
            }

            Result.success(Unit)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error processing audio: ${e.message}" }

            val error = TranslationError(
                code = ErrorCodes.TRANSLATION_API_FAILED,
                message = "Error processing audio: ${e.message}",
                category = TranslationErrorCategory.AUDIO_PROCESSING
            )

            GlobalEventBus.emit(SipEvent.TranslationError(error))
            Result.failure(SipLibraryException("Audio processing failed", error.code, ErrorCategory.TRANSLATION, e))
        }
    }

    /**
     * Cambia idiomas de traducción dinámicamente
     */
    suspend fun changeLanguages(sourceLanguage: TranslationLanguage, targetLanguage: TranslationLanguage): Result<Unit> {
        return try {
            if (!isInitialized) {
                return Result.failure(SipLibraryException("Translation not initialized", ErrorCodes.NOT_INITIALIZED, ErrorCategory.TRANSLATION))
            }

            if (!isTranslationActive) {
                // Solo actualizar configuración
                translationConfig = translationConfig?.copy(
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )

                GlobalEventBus.emit(SipEvent.TranslationStateChanged(false, sourceLanguage, targetLanguage))
                return Result.success(Unit)
            } else {
                // Reiniciar sesión con nuevos idiomas
                stopTranslation()
                delay(1000)

                translationConfig = translationConfig?.copy(
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )

                return startTranslation()
            }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error changing languages: ${e.message}" }
            Result.failure(SipLibraryException("Failed to change languages", ErrorCodes.TRANSLATION_CONFIG_INVALID, ErrorCategory.TRANSLATION, e))
        }
    }

    /**
     * Cambia la voz de traducción
     */
    suspend fun changeVoice(voiceStyle: VoiceStyle): Result<Unit> {
        return try {
            if (!isInitialized) {
                return Result.failure(SipLibraryException("Translation not initialized", ErrorCodes.NOT_INITIALIZED, ErrorCategory.TRANSLATION))
            }

            translationConfig = translationConfig?.copy(voiceStyle = voiceStyle)

            if (isTranslationActive) {
                // Reinicializar sesión OpenAI con nueva voz
                openAiClient?.disconnect()
                delay(500)
                openAiClient?.connect()
            }

            Result.success(Unit)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error changing voice: ${e.message}" }
            Result.failure(SipLibraryException("Failed to change voice", ErrorCodes.TRANSLATION_CONFIG_INVALID, ErrorCategory.TRANSLATION, e))
        }
    }

    /**
     * Obtiene el estado actual de la traducción
     */
    fun getTranslationState(): TranslationState {
        return TranslationState(
            isInitialized = isInitialized,
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
        return isInitialized &&
                translationConfig?.isEnabled == true &&
                translationConfig?.openAiApiKey?.isNotBlank() == true
    }

    /**
     * Maneja eventos de OpenAI
     */
    private suspend fun handleOpenAIEvent(event: OpenAIRealtimeClient.RealtimeEvent) {
        when (event) {
            is OpenAIRealtimeClient.RealtimeEvent.SessionCreated -> {
                log.d(tag = TAG) { "OpenAI session created: ${event.sessionId}" }
            }

            is OpenAIRealtimeClient.RealtimeEvent.TranslatedAudio -> {
                log.d(tag = TAG) { "Received translated audio: ${event.audioData.size} bytes" }
                audioProcessor?.processTranslatedAudio(event.audioData)
                updateStatistics(event.audioData.size)
            }

            is OpenAIRealtimeClient.RealtimeEvent.TranscriptDelta -> {
                log.d(tag = TAG) { "Translation transcript: ${event.text}" }
            }

            is OpenAIRealtimeClient.RealtimeEvent.TranslationComplete -> {
                log.d(tag = TAG) { "Translation completed" }
                GlobalEventBus.emit(SipEvent.TranslationCompleted(null, event.response))
            }

            is OpenAIRealtimeClient.RealtimeEvent.Error -> {
                log.e(tag = TAG) { "OpenAI error: ${event.message}" }
                val error = TranslationError(
                    code = ErrorCodes.TRANSLATION_API_FAILED,
                    message = "OpenAI translation error: ${event.message}",
                    category = TranslationErrorCategory.NETWORK_CONNECTION
                )
                GlobalEventBus.emit(SipEvent.TranslationError(error))
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
     * Procesa audio traducido
     */
    private suspend fun processTranslatedAudio(audioData: ByteArray) {
        try {
            // Aquí se reproduciría el audio traducido
            log.d(tag = TAG) { "Processing translated audio: ${audioData.size} bytes" }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error processing translated audio: ${e.message}" }
        }
    }

    /**
     * Actualiza estadísticas
     */
    private fun updateStatistics(audioSize: Int) {
        // Actualizar estadísticas de traducción
    }

    /**
     * Libera recursos
     */
    fun dispose() {
        if (isInitialized) {
            coroutineScope.launch {
                stopTranslation()
            }

            coroutineScope.cancel()
            audioProcessor?.dispose()
            openAiClient = null
            audioProcessor = null
            translationConfig = null
            isInitialized = false

            log.d(tag = TAG) { "TranslationManager disposed" }
        }
    }

    data class TranslationState(
        val isInitialized: Boolean,
        val isActive: Boolean,
        val isConnectedToOpenAI: Boolean,
        val sourceLanguage: TranslationLanguage?,
        val targetLanguage: TranslationLanguage?,
        val voiceStyle: VoiceStyle?,
        val statistics: TranslationStatistics
    )
}