package com.eddyslarez.siplibrary.translation

/**
 * Configuración para traducción en tiempo real
 *
 * @author Eddys Larez
 */
data class TranslationConfiguration(
    val isEnabled: Boolean = false,
    val myLanguage: Language = Language.SPANISH,
    val voiceGender: VoiceGender = VoiceGender.MALE,
    val autoDetectRemoteLanguage: Boolean = true,
    val remoteLanguage: Language? = null, // Si autoDetect es false
    val openAiApiKey: String = "",
    val model: String = "gpt-4o-realtime-preview-2024-12-17",
    val voice: String = "alloy", // alloy, ash, ballad, coral, echo, sage, shimmer, verse
    val outputAudioFormat: String = "pcm16",
    val inputAudioFormat: String = "pcm16"
)

enum class Language(val code: String, val displayName: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    ITALIAN("it", "Italiano"),
    PORTUGUESE("pt", "Português"),
    RUSSIAN("ru", "Русский"),
    CHINESE("zh", "中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    ARABIC("ar", "العربية"),
    HINDI("hi", "हिन्दी"),
    DUTCH("nl", "Nederlands"),
    SWEDISH("sv", "Svenska"),
    NORWEGIAN("no", "Norsk"),
    DANISH("da", "Dansk"),
    FINNISH("fi", "Suomi"),
    POLISH("pl", "Polski"),
    CZECH("cs", "Čeština"),
    HUNGARIAN("hu", "Magyar"),
    GREEK("el", "Ελληνικά"),
    HEBREW("he", "עברית"),
    THAI("th", "ไทย"),
    VIETNAMESE("vi", "Tiếng Việt"),
    TURKISH("tr", "Türkçe");

    companion object {
        fun fromCode(code: String): Language? = entries.find { it.code == code }
        fun fromDisplayName(displayName: String): Language? = entries.find { it.displayName == displayName }
    }
}

enum class VoiceGender(val displayName: String, val voices: List<String>) {
    FEMALE("Femenina", listOf("alloy", "shimmer", "coral")),
    MALE("Masculina", listOf("echo", "ash", "ballad")),
    NEUTRAL("Neutral", listOf("sage", "verse"));

    fun getRandomVoice(): String = voices.random()
    fun getDefaultVoice(): String = voices.first()
}

data class TranslationSession(
    val sessionId: String,
    val myLanguage: Language,
    val remoteLanguage: Language,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

sealed class TranslationEvent {
    data class AudioReceived(val audioData: ByteArray, val fromRemote: Boolean) : TranslationEvent()
    data class TranslationReady(val translatedAudio: ByteArray, val forRemote: Boolean) : TranslationEvent()
    data class LanguageDetected(val language: Language) : TranslationEvent()
    data class Error(val message: String, val code: Int) : TranslationEvent()
    data class SessionCreated(val sessionId: String) : TranslationEvent()
    data class SessionEnded(val sessionId: String) : TranslationEvent()
}

interface TranslationEventListener {
    fun onTranslationEvent(event: TranslationEvent)
}