package com.eddyslarez.siplibrary.translation

import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Cliente para OpenAI Realtime API
 *
 * @author Eddys Larez
 */
//class OpenAIRealtimeClient(
//    private val config: TranslationConfig,
//    private val eventCallback: (RealtimeEvent) -> Unit
//) {
//    private val TAG = "OpenAIRealtimeClient"
//
//    private var webSocket: org.java_websocket.client.WebSocketClient? = null
//    private var isConnected = false
//    private var ephemeralKey: String? = null
//    private var sessionId: String? = null
//    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//
//    // Audio buffer para almacenar audio entrante
//    private val inputAudioBuffer = mutableListOf<ByteArray>()
//    private val bufferMutex = Mutex()
//
//    companion object {
//        private const val OPENAI_REALTIME_URL = "wss://api.openai.com/v1/realtime"
//        private const val MODEL = "gpt-4o-realtime-preview-2024-12-17"
//        private const val EPHEMERAL_TOKEN_URL = "https://api.openai.com/v1/realtime/sessions"
//    }
//
//    suspend fun connect(): Boolean {
//        return try {
//            log.d(tag = TAG) { "Connecting to OpenAI Realtime API..." }
//
//            // Obtener ephemeral key si está habilitado
//            if (config.useEphemeralKey) {
//                ephemeralKey = getEphemeralKey()
//                if (ephemeralKey == null) {
//                    log.e(tag = TAG) { "Failed to get ephemeral key" }
//                    return false
//                }
//            }
//
//            val url = "$OPENAI_REALTIME_URL?model=$MODEL"
//            val uri = URI(url)
//
//            // Crear headers para la conexión
//            val headers = mutableMapOf<String, String>()
//            headers["Authorization"] = "Bearer ${ephemeralKey ?: config.openAiApiKey}"
//            headers["OpenAI-Beta"] = "realtime=v1"
//
//            webSocket = object : org.java_websocket.client.WebSocketClient(uri, headers) {
//                override fun onOpen(handshake: org.java_websocket.handshake.ServerHandshake?) {
//                    isConnected = true
//                    log.d(tag = TAG) { "Connected to OpenAI Realtime API" }
//
//                    // Configurar sesión inicial
//                    coroutineScope.launch {
//                        initializeSession()
//                    }
//                }
//
//                override fun onMessage(message: String?) {
//                    message?.let {
//                        coroutineScope.launch {
//                            handleRealtimeMessage(it)
//                        }
//                    }
//                }
//
//                override fun onClose(code: Int, reason: String?, remote: Boolean) {
//                    isConnected = false
//                    log.d(tag = TAG) { "Disconnected from OpenAI Realtime API: $code - $reason" }
//                    eventCallback(RealtimeEvent.Disconnected(code, reason ?: "Unknown"))
//                }
//
//                override fun onError(ex: Exception?) {
//                    log.e(tag = TAG) { "WebSocket error: ${ex?.message}" }
//                    eventCallback(RealtimeEvent.Error("WebSocket error: ${ex?.message}"))
//                }
//            }
//
//            webSocket?.connect()
//            true
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error connecting to OpenAI: ${e.message}" }
//            false
//        }
//    }
//
//    private suspend fun getEphemeralKey(): String? {
//        return try {
//            // Esta función debería llamar a tu servidor backend para obtener una ephemeral key
//            // Por ahora, usamos la API key directa (no recomendado para producción)
//            log.w(tag = TAG) { "Using direct API key. Implement server-side ephemeral key generation for production." }
//            config.openAiApiKey
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error getting ephemeral key: ${e.message}" }
//            null
//        }
//    }
//
//    private suspend fun initializeSession() {
//        // Construir JSON manualmente para evitar problemas de serialización
//        val sessionConfig = """
//        {
//            "type": "session.update",
//            "session": {
//                "modalities": ["text", "audio"],
//                "instructions": "You are a real-time translator. Translate speech from ${config.sourceLanguage.displayName} to ${config.targetLanguage.displayName}. Only provide the translation, no additional commentary. Maintain the tone and emotion of the original speech.",
//                "voice": "${config.voiceStyle.openAiVoice}",
//                "input_audio_format": "pcm16",
//                "output_audio_format": "pcm16",
//                "input_audio_transcription": {
//                    "model": "whisper-1"
//                },
//                "turn_detection": {
//                    "type": "server_vad",
//                    "threshold": 0.5,
//                    "prefix_padding_ms": 300,
//                    "silence_duration_ms": 500
//                },
//                "tools": [],
//                "tool_choice": "auto",
//                "temperature": 0.6,
//                "max_response_output_tokens": 4096
//            }
//        }
//        """.trimIndent()
//
//        sendMessage(sessionConfig)
//    }
//
//    suspend fun sendAudioChunk(audioData: ByteArray) {
//        if (!isConnected) return
//
//        try {
//            bufferMutex.withLock {
//                inputAudioBuffer.add(audioData)
//            }
//
//            val base64Audio = encodeToBase64(audioData)
//            val audioEvent = """
//            {
//                "type": "input_audio_buffer.append",
//                "audio": "$base64Audio"
//            }
//            """.trimIndent()
//
//            sendMessage(audioEvent)
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error sending audio chunk: ${e.message}" }
//        }
//    }
//
//    suspend fun commitAudioBuffer() {
//        if (!isConnected) return
//
//        val commitEvent = """
//        {
//            "type": "input_audio_buffer.commit"
//        }
//        """.trimIndent()
//
//        sendMessage(commitEvent)
//    }
//
//    suspend fun generateTranslation() {
//        if (!isConnected) return
//
//        val responseEvent = """
//        {
//            "type": "response.create",
//            "response": {
//                "modalities": ["audio"],
//                "instructions": "Translate the provided speech to ${config.targetLanguage.displayName}. Provide only the translation without any additional commentary."
//            }
//        }
//        """.trimIndent()
//
//        sendMessage(responseEvent)
//    }
//
//    private fun sendMessage(message: String) {
//        try {
//            webSocket?.send(message)
//            log.d(tag = TAG) { "Sent message: ${message.take(200)}..." }
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error sending message: ${e.message}" }
//        }
//    }
//
//    private suspend fun handleRealtimeMessage(message: String) {
//        try {
//            // Parsear JSON manualmente para evitar problemas de serialización
//            val json = parseJsonMessage(message)
//            val eventType = json["type"] as? String ?: return
//
//            log.d(tag = TAG) { "Received event: $eventType" }
//
//            when (eventType) {
//                "session.created" -> {
//                    val session = json["session"] as? Map<*, *>
//                    sessionId = session?.get("id") as? String
//                    eventCallback(RealtimeEvent.SessionCreated(sessionId ?: ""))
//                }
//
//                "session.updated" -> {
//                    eventCallback(RealtimeEvent.SessionUpdated)
//                }
//
//                "input_audio_buffer.speech_started" -> {
//                    eventCallback(RealtimeEvent.SpeechStarted)
//                }
//
//                "input_audio_buffer.speech_stopped" -> {
//                    eventCallback(RealtimeEvent.SpeechStopped)
//                }
//
//                "response.audio.delta" -> {
//                    val audioDelta = json["delta"] as? String
//                    if (audioDelta != null) {
//                        val audioBytes = decodeFromBase64(audioDelta)
//                        eventCallback(RealtimeEvent.TranslatedAudio(audioBytes))
//                    }
//                }
//
//                "response.audio_transcript.delta" -> {
//                    val transcriptDelta = json["delta"] as? String
//                    if (transcriptDelta != null) {
//                        eventCallback(RealtimeEvent.TranscriptDelta(transcriptDelta))
//                    }
//                }
//
//                "response.done" -> {
//                    val response = json["response"]
//                    eventCallback(RealtimeEvent.TranslationComplete(response?.toString() ?: ""))
//                }
//
//                "error" -> {
//                    val error = json["error"] as? Map<*, *>
//                    val errorMessage = error?.get("message") as? String ?: "Unknown error"
//                    eventCallback(RealtimeEvent.Error(errorMessage))
//                }
//
//                "rate_limits.updated" -> {
//                    // Handle rate limits if needed
//                    log.d(tag = TAG) { "Rate limits updated" }
//                }
//            }
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error handling realtime message: ${e.message}" }
//            eventCallback(RealtimeEvent.Error("Message parsing error: ${e.message}"))
//        }
//    }
//
//    private fun parseJsonMessage(message: String): Map<String, Any> {
//        // Parser JSON simple para evitar dependencias de serialización
//        return try {
//            // Usar una librería JSON simple o implementar parser básico
//            // Por ahora, usar un parser básico
//            val result = mutableMapOf<String, Any>()
//
//            // Extraer campos básicos usando regex
//            val typePattern = """"type"\s*:\s*"([^"]+)"""".toRegex()
//            val typeMatch = typePattern.find(message)
//            if (typeMatch != null) {
//                result["type"] = typeMatch.groupValues[1]
//            }
//
//            // Extraer delta de audio
//            val audioDeltaPattern = """"delta"\s*:\s*"([^"]+)"""".toRegex()
//            val audioDeltaMatch = audioDeltaPattern.find(message)
//            if (audioDeltaMatch != null) {
//                result["delta"] = audioDeltaMatch.groupValues[1]
//            }
//
//            // Extraer session ID
//            val sessionIdPattern = """"id"\s*:\s*"([^"]+)"""".toRegex()
//            val sessionIdMatch = sessionIdPattern.find(message)
//            if (sessionIdMatch != null) {
//                val sessionMap = mapOf("id" to sessionIdMatch.groupValues[1])
//                result["session"] = sessionMap
//            }
//
//            // Extraer error message
//            val errorMessagePattern = """"message"\s*:\s*"([^"]+)"""".toRegex()
//            val errorMessageMatch = errorMessagePattern.find(message)
//            if (errorMessageMatch != null) {
//                val errorMap = mapOf("message" to errorMessageMatch.groupValues[1])
//                result["error"] = errorMap
//            }
//
//            result
//        } catch (e: Exception) {
//            log.e(tag = TAG) { "Error parsing JSON: ${e.message}" }
//            emptyMap()
//        }
//    }
//
//    private fun encodeToBase64(data: ByteArray): String {
//        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
//    }
//
//    private fun decodeFromBase64(data: String): ByteArray {
//        return android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
//    }
//
//    fun disconnect() {
//        isConnected = false
//        coroutineScope.cancel()
//        webSocket?.close()
//        webSocket = null
//        sessionId = null
//        ephemeralKey = null
//    }
//
//    fun isConnected(): Boolean = isConnected
//
//    sealed class RealtimeEvent {
//        data class SessionCreated(val sessionId: String) : RealtimeEvent()
//        object SessionUpdated : RealtimeEvent()
//        object SpeechStarted : RealtimeEvent()
//        object SpeechStopped : RealtimeEvent()
//        data class TranslatedAudio(val audioData: ByteArray) : RealtimeEvent()
//        data class TranscriptDelta(val text: String) : RealtimeEvent()
//        data class TranslationComplete(val response: String) : RealtimeEvent()
//        data class Error(val message: String) : RealtimeEvent()
//        data class Disconnected(val code: Int, val reason: String) : RealtimeEvent()
//    }
//}

/**
 * Cliente mejorado para OpenAI Realtime API
 * Soluciona problemas de buffer, estabilidad y calidad de audio
 */
class OpenAIRealtimeClient(
    private val config: TranslationConfig,
    private val eventCallback: (RealtimeEvent) -> Unit
) {
    private val TAG = "OpenAIRealtimeClient"

    private var webSocket: org.java_websocket.client.WebSocketClient? = null
    private val isConnected = AtomicBoolean(false)
    private var ephemeralKey: String? = null
    private var sessionId: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Audio buffer mejorado para almacenar audio entrante
    private val inputAudioBuffer = mutableListOf<ByteArray>()
    private val bufferMutex = Mutex()
    private val lastCommitTime = AtomicLong(0)

    companion object {
        private const val OPENAI_REALTIME_URL = "wss://api.openai.com/v1/realtime"
        private const val MODEL = "gpt-4o-realtime-preview-2024-12-17"
        private const val EPHEMERAL_TOKEN_URL = "https://api.openai.com/v1/realtime/sessions"
        private const val MIN_AUDIO_BUFFER_MS = 150 // Aumentado para estabilidad
        private const val COMMIT_INTERVAL_MS = 100 // Intervalo mínimo entre commits
        private const val MAX_BUFFER_SIZE = 32768 // Tamaño máximo del buffer
    }

    suspend fun connect(): Boolean {
        return try {
            log.d(tag = TAG) { "Connecting to OpenAI Realtime API..." }

            if (config.useEphemeralKey) {
                ephemeralKey = getEphemeralKey()
                if (ephemeralKey == null) {
                    log.e(tag = TAG) { "Failed to get ephemeral key" }
                    return false
                }
            }

            val url = "$OPENAI_REALTIME_URL?model=$MODEL"
            val uri = URI(url)

            val headers = mutableMapOf<String, String>()
            headers["Authorization"] = "Bearer ${ephemeralKey ?: config.openAiApiKey}"
            headers["OpenAI-Beta"] = "realtime=v1"

            webSocket = object : org.java_websocket.client.WebSocketClient(uri, headers) {
                override fun onOpen(handshake: org.java_websocket.handshake.ServerHandshake?) {
                    isConnected.set(true)
                    log.d(tag = TAG) { "Connected to OpenAI Realtime API successfully" }

                    coroutineScope.launch {
                        initializeEnhancedSession()
                    }
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        coroutineScope.launch {
                            handleRealtimeMessage(it)
                        }
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    isConnected.set(false)
                    log.d(tag = TAG) { "Disconnected from OpenAI Realtime API: $code - $reason" }
                    eventCallback(RealtimeEvent.Disconnected(code, reason ?: "Unknown"))
                }

                override fun onError(ex: Exception?) {
                    log.e(tag = TAG) { "WebSocket error: ${ex?.message}" }
                    eventCallback(RealtimeEvent.Error("WebSocket error: ${ex?.message}"))
                }
            }

            webSocket?.connect()
            true
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error connecting to OpenAI: ${e.message}" }
            false
        }
    }

    private suspend fun getEphemeralKey(): String? {
        return try {
            log.w(tag = TAG) { "Using direct API key. Implement server-side ephemeral key generation for production." }
            config.openAiApiKey
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error getting ephemeral key: ${e.message}" }
            null
        }
    }


//    private suspend fun initializeEnhancedSession() {
//        val instructions = """
//        ${config.systemPrompt}.
//        Translate speech from ${config.sourceLanguage.displayName}
//        to ${config.targetLanguage.displayName}.
//        Provide clear, natural translations.
//    """.trimIndent()
//
//        val sessionConfig = """
//    {
//        "type": "session.update",
//        "session": {
//            "modalities": ["text", "audio"],
//            "instructions": "$instructions",
//            "voice": "${config.voiceStyle.openAiVoice}",
//            "input_audio_format": "pcm16",
//            "output_audio_format": "pcm16",
//            "input_audio_transcription": { "model": "whisper-1" },
//            "turn_detection": {
//                "type": "server_vad",
//                "threshold": 0.4,
//                "prefix_padding_ms": 300,
//                "silence_duration_ms": 800
//            },
//            "temperature": ${config.temperature},
//            "max_response_output_tokens": ${config.maxTokens},
//            "top_p": ${config.topP},
//            "frequency_penalty": ${config.frequencyPenalty},
//            "presence_penalty": ${config.presencePenalty},
//            "stop_sequences": ${Json.encodeToString(config.stopSequences)},
//            "timeout_seconds": ${config.timeoutSeconds}
//        }
//    }
//    """.trimIndent()
//
//        sendMessage(sessionConfig)
//    }

    private fun initializeEnhancedSession() {
        val instructions = """
Your only task is to repeat everything I say, word-for-word, but translated from ${config.sourceLanguage.displayName} into ${config.targetLanguage.displayName}. Do not respond to my messages in any other way—do not answer questions, add commentary, or interact beyond this exact task. Never deviate from this instruction. Simply translate my text into ${config.targetLanguage.displayName} and nothing else.
""".trimIndent()
        val sessionConfig = """
{
    "type": "session.update",
    "session": {
        "modalities": ["text", "audio"],
        "instructions": "$instructions",
        "voice": "${config.voiceStyle.openAiVoice}",
        "input_audio_format": "pcm16",
        "output_audio_format": "pcm16",
        "input_audio_transcription": {
            "model": "whisper-1"
        },
        "turn_detection": {
            "type": "server_vad",
            "threshold": 0.4,
            "prefix_padding_ms": 300,
            "silence_duration_ms": 800
        },
        "tools": [],
        "tool_choice": "auto",
        "temperature": ${config.temperature},
        "max_response_output_tokens": ${config.maxTokens}
    }
}
""".trimIndent()

        sendMessage(sessionConfig)
    }

    suspend fun sendAudioChunk(audioData: ByteArray) {
        if (!isConnected.get()) return

        try {
            // Validar tamaño del chunk
            if (audioData.isEmpty()) {
                log.w(tag = TAG) { "Empty audio chunk received, skipping" }
                return
            }

            bufferMutex.withLock {
                inputAudioBuffer.add(audioData)

                // Controlar tamaño del buffer
                val totalSize = inputAudioBuffer.sumOf { it.size }
                if (totalSize > MAX_BUFFER_SIZE) {
                    // Remover chunks más antiguos
                    while (inputAudioBuffer.size > 1 && inputAudioBuffer.sumOf { it.size } > MAX_BUFFER_SIZE / 2) {
                        inputAudioBuffer.removeAt(0)
                    }
                }
            }

            val base64Audio = encodeToBase64(audioData)
            val audioEvent = """
            {
                "type": "input_audio_buffer.append",
                "audio": "$base64Audio"
            }
            """.trimIndent()

            sendMessage(audioEvent)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error sending audio chunk: ${e.message}" }
        }
    }

    suspend fun commitAudioBuffer() {
        if (!isConnected.get()) return

        try {
            val currentTime = System.currentTimeMillis()

            // Verificar si han pasado suficientes milisegundos desde el último commit
            if (currentTime - lastCommitTime.get() < COMMIT_INTERVAL_MS) {
                return
            }

            val totalBufferSize = bufferMutex.withLock {
                inputAudioBuffer.sumOf { it.size }
            }

            // Verificar que tenemos suficiente audio (mínimo 150ms)
            val requiredBytes =
                (16000 * 2 * MIN_AUDIO_BUFFER_MS) / 1000 // 16kHz, 16-bit, tiempo en ms

            if (totalBufferSize < requiredBytes) {
                log.d(tag = TAG) { "Buffer too small for commit: ${totalBufferSize} bytes, need at least ${requiredBytes}" }
                return
            }

            lastCommitTime.set(currentTime)

            val commitEvent = """
            {
                "type": "input_audio_buffer.commit"
            }
            """.trimIndent()

            sendMessage(commitEvent)
            log.d(tag = TAG) { "Audio buffer committed with ${totalBufferSize} bytes" }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error committing audio buffer: ${e.message}" }
        }
    }

    suspend fun generateTranslation() {
        if (!isConnected.get()) return

        // Pequeña pausa antes de generar respuesta
        delay(200)

        val responseEvent = """
        {
            "type": "response.create",
            "response": {
                "modalities": ["audio"],
                "instructions": "Translate the provided speech to ${config.targetLanguage.displayName}. Provide a clear, natural translation that maintains the original meaning and tone."
            }
        }
        """.trimIndent()

        sendMessage(responseEvent)
    }

    private fun sendMessage(message: String) {
        try {
            if (isConnected.get()) {
                webSocket?.send(message)
                log.d(tag = TAG) { "Sent message: ${message.take(200)}..." }
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error sending message: ${e.message}" }
        }
    }

    private suspend fun handleRealtimeMessage(message: String) {
        try {
            val json = parseJsonMessage(message)
            val eventType = json["type"] as? String ?: return

            log.d(tag = TAG) { "Received event: $eventType" }

            when (eventType) {
                "session.created" -> {
                    val session = json["session"] as? Map<*, *>
                    sessionId = session?.get("id") as? String
                    eventCallback(RealtimeEvent.SessionCreated(sessionId ?: ""))
                }

                "session.updated" -> {
                    eventCallback(RealtimeEvent.SessionUpdated)
                }

                "input_audio_buffer.speech_started" -> {
                    eventCallback(RealtimeEvent.SpeechStarted)
                }

                "input_audio_buffer.speech_stopped" -> {
                    eventCallback(RealtimeEvent.SpeechStopped)

                    // Esperar un poco antes de procesar
                    delay(300)
                    commitAudioBuffer()
                    delay(100)
                    generateTranslation()
                }

                "response.audio.delta" -> {
                    val audioDelta = json["delta"] as? String
                    if (audioDelta != null && audioDelta.isNotEmpty()) {
                        try {
                            val audioBytes = decodeFromBase64(audioDelta)
                            if (audioBytes.isNotEmpty()) {
                                eventCallback(RealtimeEvent.TranslatedAudio(audioBytes))
                            }
                        } catch (e: Exception) {
                            log.e(tag = TAG) { "Error decoding audio delta: ${e.message}" }
                        }
                    }
                }

                "response.audio_transcript.delta" -> {
                    val transcriptDelta = json["delta"] as? String
                    if (transcriptDelta != null && transcriptDelta.isNotEmpty()) {
                        eventCallback(RealtimeEvent.TranscriptDelta(transcriptDelta))
                    }
                }

                "response.done" -> {
                    eventCallback(RealtimeEvent.TranslationComplete(""))
                }

                "error" -> {
                    val error = json["error"] as? Map<*, *>
                    val errorMessage = error?.get("message") as? String ?: "Unknown error"
                    log.e(tag = TAG) { "OpenAI API error: $errorMessage" }
                    eventCallback(RealtimeEvent.Error(errorMessage))
                }

                "rate_limits.updated" -> {
                    log.d(tag = TAG) { "Rate limits updated" }
                }

                else -> {
                    log.d(tag = TAG) { "Unhandled event type: $eventType" }
                }
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error handling realtime message: ${e.message}" }
            eventCallback(RealtimeEvent.Error("Message parsing error: ${e.message}"))
        }
    }

    private fun parseJsonMessage(message: String): Map<String, Any> {
        return try {
            val result = mutableMapOf<String, Any>()

            // Extraer campos básicos usando regex mejorado
            val typePattern = """"type"\s*:\s*"([^"]+)"""".toRegex()
            val typeMatch = typePattern.find(message)
            if (typeMatch != null) {
                result["type"] = typeMatch.groupValues[1]
            }

            // Extraer delta de audio
            val audioDeltaPattern = """"delta"\s*:\s*"([^"]*?)"""".toRegex()
            val audioDeltaMatch = audioDeltaPattern.find(message)
            if (audioDeltaMatch != null) {
                result["delta"] = audioDeltaMatch.groupValues[1]
            }

            // Extraer session ID
            val sessionIdPattern = """"id"\s*:\s*"([^"]+)"""".toRegex()
            val sessionIdMatch = sessionIdPattern.find(message)
            if (sessionIdMatch != null) {
                val sessionMap = mapOf("id" to sessionIdMatch.groupValues[1])
                result["session"] = sessionMap
            }

            // Extraer error message
            val errorMessagePattern = """"message"\s*:\s*"([^"]+)"""".toRegex()
            val errorMessageMatch = errorMessagePattern.find(message)
            if (errorMessageMatch != null) {
                val errorMap = mapOf("message" to errorMessageMatch.groupValues[1])
                result["error"] = errorMap
            }

            result
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error parsing JSON: ${e.message}" }
            emptyMap()
        }
    }

    private fun encodeToBase64(data: ByteArray): String {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
    }

    private fun decodeFromBase64(data: String): ByteArray {
        return try {
            android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error decoding base64: ${e.message}" }
            ByteArray(0)
        }
    }

    fun disconnect() {
        isConnected.set(false)
        coroutineScope.cancel()
        webSocket?.close()
        webSocket = null
        sessionId = null
        ephemeralKey = null

        // Limpiar buffer
        coroutineScope.launch {
            bufferMutex.withLock {
                inputAudioBuffer.clear()
            }
        }
    }

    fun isConnected(): Boolean = isConnected.get()

    sealed class RealtimeEvent {
        data class SessionCreated(val sessionId: String) : RealtimeEvent()
        object SessionUpdated : RealtimeEvent()
        object SpeechStarted : RealtimeEvent()
        object SpeechStopped : RealtimeEvent()
        data class TranslatedAudio(val audioData: ByteArray) : RealtimeEvent()
        data class TranscriptDelta(val text: String) : RealtimeEvent()
        data class TranslationComplete(val response: String) : RealtimeEvent()
        data class Error(val message: String) : RealtimeEvent()
        data class Disconnected(val code: Int, val reason: String) : RealtimeEvent()
    }
}