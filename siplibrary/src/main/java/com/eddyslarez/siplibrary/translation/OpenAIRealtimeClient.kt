package com.eddyslarez.siplibrary.translation

/**
 * Cliente para la API de OpenAI Realtime
 *
 * @author Eddys Larez
 */
import android.os.Build
import androidx.annotation.RequiresApi
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.*
import kotlin.random.Random

class OpenAIRealtimeClient(
    private val configuration: TranslationConfiguration,
    private val eventListener: TranslationEventListener
) {
    private val TAG = "OpenAIRealtimeClient"
    private var webSocketClient: WebSocketClient? = null
    private var isConnected = false
    private var sessionId: String? = null

    // Audio buffers
    private val audioInputBuffer = mutableListOf<ByteArray>()
    private val audioOutputBuffer = mutableListOf<ByteArray>()

    // Translation state
    private var currentInputLanguage: Language? = null
    private var currentOutputLanguage: Language? = null

    private val json = Json { ignoreUnknownKeys = true }

    fun connect() {
        if (configuration.openAiApiKey.isEmpty()) {
            eventListener.onTranslationEvent(
                TranslationEvent.Error("OpenAI API key not configured", 401)
            )
            return
        }

        try {
            val uri = URI("wss://api.openai.com/v1/realtime?model=${configuration.model}")
            val headers = mapOf(
                "Authorization" to "Bearer ${configuration.openAiApiKey}",
                "OpenAI-Beta" to "realtime=v1"
            )

            webSocketClient = object : WebSocketClient(uri, headers) {
                override fun onOpen(handshakedata: ServerHandshake) {
                    isConnected = true
                    log.d(tag = TAG) { "Connected to OpenAI Realtime API" }
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onMessage(message: String) {
                    handleServerMessage(message)
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    isConnected = false
                    sessionId?.let {
                        eventListener.onTranslationEvent(TranslationEvent.SessionEnded(it))
                    }
                    log.d(tag = TAG) { "Disconnected from OpenAI Realtime API: $reason" }
                }

                override fun onError(ex: Exception) {
                    eventListener.onTranslationEvent(
                        TranslationEvent.Error("WebSocket error: ${ex.message}", 500)
                    )
                    log.e(tag = TAG) { "WebSocket error: ${ex.message}" }
                }
            }

            webSocketClient?.connect()

        } catch (e: Exception) {
            eventListener.onTranslationEvent(
                TranslationEvent.Error("Failed to connect: ${e.message}", 500)
            )
        }
    }

    fun disconnect() {
        webSocketClient?.close()
        webSocketClient = null
        isConnected = false
    }

    fun startTranslationSession(inputLanguage: Language, outputLanguage: Language) {
        if (!isConnected) {
            eventListener.onTranslationEvent(
                TranslationEvent.Error("Not connected to OpenAI", 503)
            )
            return
        }

        currentInputLanguage = inputLanguage
        currentOutputLanguage = outputLanguage

        val instructions = createTranslationInstructions(inputLanguage, outputLanguage)
        val voice = getVoiceForGender(configuration.voiceGender)

        val sessionUpdate = buildJsonObject {
            put("type", "session.update")
            put("session", buildJsonObject {
                put("instructions", instructions)
                put("voice", voice)
                put("input_audio_format", configuration.inputAudioFormat)
                put("output_audio_format", configuration.outputAudioFormat)
                put("turn_detection", buildJsonObject {
                    put("type", "server_vad")
                    put("threshold", 0.5)
                    put("prefix_padding_ms", 300)
                    put("silence_duration_ms", 200)
                    put("create_response", true)
                })
                put("modalities", buildJsonArray {
                    add("audio")
                })
            })
        }

        sendMessage(sessionUpdate.toString())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendAudioChunk(audioData: ByteArray) {
        if (!isConnected) return

        val base64Audio = Base64.getEncoder().encodeToString(audioData)
        val audioMessage = buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", base64Audio)
        }

        sendMessage(audioMessage.toString())
    }

    fun commitAudioInput() {
        if (!isConnected) return

        val commitMessage = buildJsonObject {
            put("type", "input_audio_buffer.commit")
        }

        sendMessage(commitMessage.toString())

        val responseMessage = buildJsonObject {
            put("type", "response.create")
            put("response", buildJsonObject {
                put("modalities", buildJsonArray { add("audio") })
            })
        }

        sendMessage(responseMessage.toString())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleServerMessage(message: String) {
        try {
            val jsonMessage = json.parseToJsonElement(message).jsonObject
            val eventType = jsonMessage["type"]?.jsonPrimitive?.content

            when (eventType) {
                "session.created" -> {
                    sessionId = jsonMessage["session"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                    sessionId?.let {
                        eventListener.onTranslationEvent(TranslationEvent.SessionCreated(it))
                    }
                    log.d(tag = TAG) { "Session created: $sessionId" }
                }

                "response.audio.delta" -> {
                    val audioDelta = jsonMessage["delta"]?.jsonPrimitive?.content
                    if (!audioDelta.isNullOrEmpty()) {
                        val audioBytes = Base64.getDecoder().decode(audioDelta)
                        eventListener.onTranslationEvent(
                            TranslationEvent.TranslationReady(audioBytes, true)
                        )
                    }
                }

                "input_audio_buffer.speech_started" -> {
                    log.d(tag = TAG) { "Speech started" }
                }

                "input_audio_buffer.speech_stopped" -> {
                    log.d(tag = TAG) { "Speech stopped" }
                }

                "response.done" -> {
                    log.d(tag = TAG) { "Response completed" }
                }

                "error" -> {
                    val errorMessage = jsonMessage["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    val errorCode = jsonMessage["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 500
                    eventListener.onTranslationEvent(
                        TranslationEvent.Error(errorMessage, errorCode)
                    )
                }

                else -> {
                    log.d(tag = TAG) { "Unhandled event type: $eventType" }
                }
            }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error parsing server message: ${e.message}" }
        }
    }

    private fun sendMessage(message: String) {
        try {
            webSocketClient?.send(message)
            log.d(tag = TAG) { "Sent message: ${message.take(100)}..." }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error sending message: ${e.message}" }
        }
    }

    private fun createTranslationInstructions(inputLang: Language, outputLang: Language): String {
        return """
            Your only task is to repeat everything I say, word-for-word, but translated into ${outputLang.displayName}. 
            Do not respond to my messages in any other way—do not answer questions, add commentary, or interact beyond this exact task. 
            Never deviate from this instruction. Simply translate my speech from ${inputLang.displayName} into ${outputLang.displayName} and nothing else.
            Maintain the same tone and emotion of the original speech when possible.
        """.trimIndent()
    }

    private fun getVoiceForGender(gender: VoiceGender): String {
        return when (configuration.voice) {
            in gender.voices -> configuration.voice
            else -> gender.getDefaultVoice()
        }
    }

    fun isConnected(): Boolean = isConnected

    fun getCurrentSession(): String? = sessionId
}

@Serializable
data class SessionConfiguration(
    val instructions: String,
    val voice: String,
    val input_audio_format: String,
    val output_audio_format: String,
    val turn_detection: TurnDetection,
    val modalities: List<String>
)

@Serializable
data class TurnDetection(
    val type: String,
    val threshold: Double,
    val prefix_padding_ms: Int,
    val silence_duration_ms: Int,
    val create_response: Boolean
)