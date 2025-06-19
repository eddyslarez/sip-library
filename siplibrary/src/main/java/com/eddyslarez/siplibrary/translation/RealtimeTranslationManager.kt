package com.eddyslarez.siplibrary.translation

/**
 * Gestor principal de traducción en tiempo real
 *
 * @author Eddys Larez
 */
import android.os.Build
import androidx.annotation.RequiresApi
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.HashMap

class RealtimeTranslationManager(
    private var configuration: TranslationConfiguration,
    private val eventListener: TranslationEventListener
) {
    private val TAG = "RealtimeTranslationManager"

    // OpenAI clients para cada dirección de traducción
    private var myToRemoteClient: OpenAIRealtimeClient? = null
    private var remoteToMyClient: OpenAIRealtimeClient? = null

    // Estado de sesión
    private var isActive = false
    private var currentSession: TranslationSession? = null
    private var detectedRemoteLanguage: Language? = null

    // Audio buffers
    private val myAudioQueue = ConcurrentLinkedQueue<ByteArray>()
    private val remoteAudioQueue = ConcurrentLinkedQueue<ByteArray>()

    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioProcessingJob: Job? = null

    // Language detection
    private var languageDetectionJob: Job? = null
    private val audioSamplesForDetection = mutableListOf<ByteArray>()
    private var detectionInProgress = false

    // Callbacks para integración con WebRTC
    var onTranslatedAudioReady: ((ByteArray, Boolean) -> Unit)? = null

    init {
        setupEventHandling()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateConfiguration(newConfig: TranslationConfiguration) {
        configuration = newConfig
        if (isActive) {
            // Reiniciar sesión con nueva configuración
            stopTranslation()
            startTranslation()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startTranslation(): Boolean {
        if (!configuration.isEnabled || configuration.openAiApiKey.isEmpty()) {
            log.d(tag = TAG) { "Translation not enabled or API key missing" }
            return false
        }

        if (isActive) {
            log.d(tag = TAG) { "Translation already active" }
            return true
        }

        try {
            log.d(tag = TAG) { "Starting real-time translation" }

            // Crear clientes para ambas direcciones
            myToRemoteClient = OpenAIRealtimeClient(configuration, createClientEventListener(true))
            remoteToMyClient = OpenAIRealtimeClient(configuration, createClientEventListener(false))

            // Conectar clientes
            myToRemoteClient?.connect()
            remoteToMyClient?.connect()

            // Inicializar sesión
            currentSession = TranslationSession(
                sessionId = generateSessionId(),
                myLanguage = configuration.myLanguage,
                remoteLanguage = configuration.remoteLanguage ?: Language.ENGLISH
            )

            // Comenzar procesamiento de audio
            startAudioProcessing()

            isActive = true
            eventListener.onTranslationEvent(
                TranslationEvent.SessionCreated(currentSession!!.sessionId)
            )

            return true

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error starting translation: ${e.message}" }
            eventListener.onTranslationEvent(
                TranslationEvent.Error("Failed to start translation: ${e.message}", 500)
            )
            return false
        }
    }

    fun stopTranslation() {
        if (!isActive) return

        log.d(tag = TAG) { "Stopping real-time translation" }

        isActive = false
        audioProcessingJob?.cancel()
        languageDetectionJob?.cancel()

        myToRemoteClient?.disconnect()
        remoteToMyClient?.disconnect()

        myToRemoteClient = null
        remoteToMyClient = null

        currentSession?.let {
            eventListener.onTranslationEvent(TranslationEvent.SessionEnded(it.sessionId))
        }
        currentSession = null

        // Limpiar buffers
        myAudioQueue.clear()
        remoteAudioQueue.clear()
        audioSamplesForDetection.clear()
        detectedRemoteLanguage = null
    }

    fun processMyAudio(audioData: ByteArray) {
        if (!isActive || !configuration.isEnabled) return

        myAudioQueue.offer(audioData)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun processRemoteAudio(audioData: ByteArray) {
        if (!isActive || !configuration.isEnabled) return

        // Si auto-detect está habilitado y no hemos detectado el idioma, agregarlo para detección
        if (configuration.autoDetectRemoteLanguage && detectedRemoteLanguage == null) {
            addAudioForLanguageDetection(audioData)
        }

        remoteAudioQueue.offer(audioData)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startAudioProcessing() {
        audioProcessingJob = scope.launch {
            while (isActive) {
                // Procesar audio mío (para enviar al remoto)
                processMyAudioQueue()

                // Procesar audio remoto (para traducir a mi idioma)
                processRemoteAudioQueue()

                delay(50) // Procesar cada 50ms
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processMyAudioQueue() {
        val audioChunks = mutableListOf<ByteArray>()

        // Recoger chunks disponibles
        while (myAudioQueue.isNotEmpty() && audioChunks.size < 10) {
            myAudioQueue.poll()?.let { audioChunks.add(it) }
        }

        if (audioChunks.isNotEmpty()) {
            val targetLanguage = detectedRemoteLanguage ?: configuration.remoteLanguage ?: Language.ENGLISH

            // Enviar audio para traducción (mi idioma -> idioma remoto)
            myToRemoteClient?.let { client ->
                if (!client.isConnected()) return@let

                // Inicializar sesión de traducción si es necesario
                if (client.getCurrentSession() == null) {
                    client.startTranslationSession(configuration.myLanguage, targetLanguage)
                    delay(500) // Esperar a que se configure la sesión
                }

                // Enviar chunks de audio
                audioChunks.forEach { chunk ->
                    client.sendAudioChunk(chunk)
                }
                client.commitAudioInput()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processRemoteAudioQueue() {
        val audioChunks = mutableListOf<ByteArray>()

        // Recoger chunks disponibles
        while (remoteAudioQueue.isNotEmpty() && audioChunks.size < 10) {
            remoteAudioQueue.poll()?.let { audioChunks.add(it) }
        }

        if (audioChunks.isNotEmpty()) {
            val sourceLanguage = detectedRemoteLanguage ?: configuration.remoteLanguage ?: Language.ENGLISH

            // Enviar audio para traducción (idioma remoto -> mi idioma)
            remoteToMyClient?.let { client ->
                if (!client.isConnected()) return@let

                // Inicializar sesión de traducción si es necesario
                if (client.getCurrentSession() == null) {
                    client.startTranslationSession(sourceLanguage, configuration.myLanguage)
                    delay(500) // Esperar a que se configure la sesión
                }

                // Enviar chunks de audio
                audioChunks.forEach { chunk ->
                    client.sendAudioChunk(chunk)
                }
                client.commitAudioInput()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun addAudioForLanguageDetection(audioData: ByteArray) {
        if (detectionInProgress) return

        audioSamplesForDetection.add(audioData)

        // Si tenemos suficientes muestras, intentar detectar idioma
        if (audioSamplesForDetection.size >= 10) { // ~500ms de audio
            startLanguageDetection()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startLanguageDetection() {
        if (detectionInProgress) return

        detectionInProgress = true
        languageDetectionJob = scope.launch {
            try {
                // Crear un cliente temporal para detección de idioma
                val detectionClient = OpenAIRealtimeClient(
                    configuration.copy(voice = "alloy"), // Voz neutral para detección
                    object : TranslationEventListener {
                        override fun onTranslationEvent(event: TranslationEvent) {
                            // Solo manejar eventos de detección
                        }
                    }
                )

                detectionClient.connect()
                delay(1000) // Esperar conexión

                // Configurar para detección (sin traducción, solo transcripción)
                val detectionInstructions = """
                    Listen to the audio and identify the language being spoken. 
                    Respond only with the language name in English (e.g., "Spanish", "English", "French", etc.).
                    Do not translate or repeat the content, just identify the language.
                """.trimIndent()

                // Enviar muestras de audio
                audioSamplesForDetection.forEach { sample ->
                    detectionClient.sendAudioChunk(sample)
                }
                detectionClient.commitAudioInput()

                // Simular detección (en implementación real, manejarías la respuesta)
                delay(2000)

                // Por ahora, usar un idioma por defecto
                val detectedLang = Language.ENGLISH // En implementación real, parsearías la respuesta
                setDetectedLanguage(detectedLang)

                detectionClient.disconnect()

            } catch (e: Exception) {
                log.e(tag = TAG) { "Error in language detection: ${e.message}" }
            } finally {
                detectionInProgress = false
                audioSamplesForDetection.clear()
            }
        }
    }

    private fun setDetectedLanguage(language: Language) {
        if (detectedRemoteLanguage != language) {
            detectedRemoteLanguage = language
            eventListener.onTranslationEvent(TranslationEvent.LanguageDetected(language))

            // Reiniciar sesiones de traducción con el nuevo idioma
            currentSession = currentSession?.copy(remoteLanguage = language)

            log.d(tag = TAG) { "Detected remote language: ${language.displayName}" }
        }
    }

    private fun setupEventHandling() {
        // Los eventos se manejarán a través de los listeners de cada cliente
    }

    private fun createClientEventListener(isMyToRemote: Boolean): TranslationEventListener {
        return object : TranslationEventListener {
            override fun onTranslationEvent(event: TranslationEvent) {
                when (event) {
                    is TranslationEvent.TranslationReady -> {
                        // Audio traducido listo para enviar
                        onTranslatedAudioReady?.invoke(event.translatedAudio, isMyToRemote)
                        eventListener.onTranslationEvent(event)
                    }
                    is TranslationEvent.Error -> {
                        log.e(tag = TAG) { "Translation error (${if (isMyToRemote) "my->remote" else "remote->my"}): ${event.message}" }
                        eventListener.onTranslationEvent(event)
                    }
                    else -> {
                        eventListener.onTranslationEvent(event)
                    }
                }
            }
        }
    }

    private fun generateSessionId(): String {
        return "trans_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}"
    }

    fun isTranslationActive(): Boolean = isActive

    fun getCurrentSession(): TranslationSession? = currentSession

    fun getDetectedLanguage(): Language? = detectedRemoteLanguage

    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "isActive" to isActive,
            "myLanguage" to configuration.myLanguage.displayName,
            "remoteLanguage" to (detectedRemoteLanguage?.displayName ?: configuration.remoteLanguage?.displayName ?: "Unknown"),
            "audioQueueSizes" to mapOf(
                "myAudio" to myAudioQueue.size,
                "remoteAudio" to remoteAudioQueue.size
            ),
            "sessionId" to (currentSession?.sessionId ?: "none")
        )
    }

    fun dispose() {
        stopTranslation()
        scope.cancel()
    }
}