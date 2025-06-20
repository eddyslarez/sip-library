package com.eddyslarez.siplibrary.translation

import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import java.net.URI
/**
 * Cliente para OpenAI Realtime API
 *
 * @author Eddys Larez
 */
class OpenAIRealtimeClient(
    private val config: TranslationConfig,
    private val eventCallback: (RealtimeEvent) -> Unit
) {
    private val TAG = "OpenAIRealtimeClient"

    private var webSocket: org.java_websocket.client.WebSocketClient? = null
    private var isConnected = false
    private var ephemeralKey: String? = null
    private var sessionId: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Audio buffer para almacenar audio entrante
    private val inputAudioBuffer = mutableListOf<ByteArray>()
    private val bufferMutex = Mutex()

    companion object {
        private const val OPENAI_REALTIME_URL = "wss://api.openai.com/v1/realtime"
        private const val MODEL = "gpt-4o-realtime-preview-2024-12-17"
        private const val EPHEMERAL_TOKEN_URL = "https://api.openai.com/v1/realtime/sessions"
    }

    suspend fun connect(): Boolean {
        return try {
            log.d(tag = TAG) { "Connecting to OpenAI Realtime API..." }

            // Obtener ephemeral key si está habilitado
            if (config.useEphemeralKey) {
                ephemeralKey = getEphemeralKey()
                if (ephemeralKey == null) {
                    log.e(tag = TAG) { "Failed to get ephemeral key" }
                    return false
                }
            }

            val url = "$OPENAI_REALTIME_URL?model=$MODEL"
            val uri = URI(url)

            // Crear headers para la conexión
            val headers = mutableMapOf<String, String>()
            headers["Authorization"] = "Bearer ${ephemeralKey ?: config.openAiApiKey}"
            headers["OpenAI-Beta"] = "realtime=v1"

            webSocket = object : org.java_websocket.client.WebSocketClient(uri, headers) {
                override fun onOpen(handshake: org.java_websocket.handshake.ServerHandshake?) {
                    isConnected = true
                    log.d(tag = TAG) { "Connected to OpenAI Realtime API" }

                    // Configurar sesión inicial
                    coroutineScope.launch {
                        initializeSession()
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
                    isConnected = false
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
            // Esta función debería llamar a tu servidor backend para obtener una ephemeral key
            // Por ahora, usamos la API key directa (no recomendado para producción)
            log.w(tag = TAG) { "Using direct API key. Implement server-side ephemeral key generation for production." }
            config.openAiApiKey
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error getting ephemeral key: ${e.message}" }
            null
        }
    }

    private suspend fun initializeSession() {
        // Construir JSON manualmente para evitar problemas de serialización
        val sessionConfig = """
        {
            "type": "session.update",
            "session": {
                "modalities": ["text", "audio"],
                "instructions": "You are a real-time translator. Translate speech from ${config.sourceLanguage.displayName} to ${config.targetLanguage.displayName}. Only provide the translation, no additional commentary. Maintain the tone and emotion of the original speech.",
                "voice": "${config.voiceStyle.openAiVoice}",
                "input_audio_format": "pcm16",
                "output_audio_format": "pcm16",
                "input_audio_transcription": {
                    "model": "whisper-1"
                },
                "turn_detection": {
                    "type": "server_vad",
                    "threshold": 0.5,
                    "prefix_padding_ms": 300,
                    "silence_duration_ms": 500
                },
                "tools": [],
                "tool_choice": "auto",
                "temperature": 0.6,
                "max_response_output_tokens": 4096
            }
        }
        """.trimIndent()

        sendMessage(sessionConfig)
    }

    suspend fun sendAudioChunk(audioData: ByteArray) {
        if (!isConnected) return

        try {
            bufferMutex.withLock {
                inputAudioBuffer.add(audioData)
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
        if (!isConnected) return

        val commitEvent = """
        {
            "type": "input_audio_buffer.commit"
        }
        """.trimIndent()

        sendMessage(commitEvent)
    }

    suspend fun generateTranslation() {
        if (!isConnected) return

        val responseEvent = """
        {
            "type": "response.create",
            "response": {
                "modalities": ["audio"],
                "instructions": "Translate the provided speech to ${config.targetLanguage.displayName}. Provide only the translation without any additional commentary."
            }
        }
        """.trimIndent()

        sendMessage(responseEvent)
    }

    private fun sendMessage(message: String) {
        try {
            webSocket?.send(message)
            log.d(tag = TAG) { "Sent message: ${message.take(200)}..." }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error sending message: ${e.message}" }
        }
    }

    private suspend fun handleRealtimeMessage(message: String) {
        try {
            // Parsear JSON manualmente para evitar problemas de serialización
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
                }

                "response.audio.delta" -> {
                    val audioDelta = json["delta"] as? String
                    if (audioDelta != null) {
                        val audioBytes = decodeFromBase64(audioDelta)
                        eventCallback(RealtimeEvent.TranslatedAudio(audioBytes))
                    }
                }

                "response.audio_transcript.delta" -> {
                    val transcriptDelta = json["delta"] as? String
                    if (transcriptDelta != null) {
                        eventCallback(RealtimeEvent.TranscriptDelta(transcriptDelta))
                    }
                }

                "response.done" -> {
                    val response = json["response"]
                    eventCallback(RealtimeEvent.TranslationComplete(response?.toString() ?: ""))
                }

                "error" -> {
                    val error = json["error"] as? Map<*, *>
                    val errorMessage = error?.get("message") as? String ?: "Unknown error"
                    eventCallback(RealtimeEvent.Error(errorMessage))
                }

                "rate_limits.updated" -> {
                    // Handle rate limits if needed
                    log.d(tag = TAG) { "Rate limits updated" }
                }
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error handling realtime message: ${e.message}" }
            eventCallback(RealtimeEvent.Error("Message parsing error: ${e.message}"))
        }
    }

    private fun parseJsonMessage(message: String): Map<String, Any> {
        // Parser JSON simple para evitar dependencias de serialización
        return try {
            // Usar una librería JSON simple o implementar parser básico
            // Por ahora, usar un parser básico
            val result = mutableMapOf<String, Any>()

            // Extraer campos básicos usando regex
            val typePattern = """"type"\s*:\s*"([^"]+)"""".toRegex()
            val typeMatch = typePattern.find(message)
            if (typeMatch != null) {
                result["type"] = typeMatch.groupValues[1]
            }

            // Extraer delta de audio
            val audioDeltaPattern = """"delta"\s*:\s*"([^"]+)"""".toRegex()
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
        return android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
    }

    fun disconnect() {
        isConnected = false
        coroutineScope.cancel()
        webSocket?.close()
        webSocket = null
        sessionId = null
        ephemeralKey = null
    }

    fun isConnected(): Boolean = isConnected

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