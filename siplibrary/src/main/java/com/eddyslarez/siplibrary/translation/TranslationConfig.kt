package com.eddyslarez.siplibrary.translation

/**
 * Configuración para traducción en tiempo real
 *
 * @author Eddys Larez
 */
data class TranslationConfig(
    val isEnabled: Boolean = false,
    val openAiApiKey: String = "",
    val sourceLanguage: TranslationLanguage = TranslationLanguage.AUTO_DETECT,
    val targetLanguage: TranslationLanguage = TranslationLanguage.ENGLISH,
    val voiceGender: VoiceGender = VoiceGender.FEMALE,
    val voiceStyle: VoiceStyle = VoiceStyle.ALLOY,
    val enableBidirectional: Boolean = true,
    val audioFormat: AudioFormat = AudioFormat.PCM16,
    val sampleRate: Int = 24000,
    val enableDebugging: Boolean = false,
    val translationDelay: Int = 100, // ms delay before processing
    val maxAudioBufferSize: Int = 1024 * 1024, // 1MB
    val useEphemeralKey: Boolean = true
)

enum class TranslationLanguage(val code: String, val displayName: String) {
    AUTO_DETECT("auto", "Auto Detect"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
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
    ROMANIAN("ro", "Română"),
    BULGARIAN("bg", "Български"),
    CROATIAN("hr", "Hrvatski"),
    SLOVAK("sk", "Slovenčina"),
    SLOVENIAN("sl", "Slovenščina"),
    ESTONIAN("et", "Eesti"),
    LATVIAN("lv", "Latviešu"),
    LITHUANIAN("lt", "Lietuvių"),
    MALTESE("mt", "Malti"),
    TURKISH("tr", "Türkçe"),
    GREEK("el", "Ελληνικά"),
    HEBREW("he", "עברית"),
    THAI("th", "ไทย"),
    VIETNAMESE("vi", "Tiếng Việt"),
    INDONESIAN("id", "Bahasa Indonesia"),
    MALAY("ms", "Bahasa Melayu"),
    FILIPINO("fil", "Filipino"),
    SWAHILI("sw", "Kiswahili"),
    UKRAINIAN("uk", "Українська"),
    WELSH("cy", "Cymraeg"),
    IRISH("ga", "Gaeilge"),
    SCOTS_GAELIC("gd", "Gàidhlig"),
    BASQUE("eu", "Euskera"),
    CATALAN("ca", "Català"),
    GALICIAN("gl", "Galego")
}

enum class VoiceGender {
    MALE,
    FEMALE,
    NEUTRAL
}

enum class VoiceStyle(val openAiVoice: String, val gender: VoiceGender) {
    ALLOY("alloy", VoiceGender.NEUTRAL),
    ASH("ash", VoiceGender.MALE),
    BALLAD("ballad", VoiceGender.FEMALE),
    CORAL("coral", VoiceGender.FEMALE),
    ECHO("echo", VoiceGender.MALE),
    SAGE("sage", VoiceGender.FEMALE),
    SHIMMER("shimmer", VoiceGender.FEMALE),
    VERSE("verse", VoiceGender.MALE)
}

enum class AudioFormat(val mimeType: String) {
    PCM16("audio/pcm"),
    G711("audio/g711"),
    OPUS("audio/opus")
}

data class TranslationStatistics(
    val translationsProcessed: Int = 0,
    val averageLatency: Long = 0L,
    val errorsCount: Int = 0,
    val lastTranslationTime: Long = 0L,
    val totalAudioProcessed: Long = 0L // bytes
)