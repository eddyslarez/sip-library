package com.eddyslarez.siplibrary.translation

/**
 * Listener de eventos específicos para traducción en tiempo real
 *
 * @author Eddys Larez
 */
interface TranslationEventListener {
    /**
     * Se llama cuando cambia el estado de la traducción
     */
    fun onTranslationStateChanged(
        isActive: Boolean,
        sourceLanguage: TranslationLanguage?,
        targetLanguage: TranslationLanguage?
    ) {}

    /**
     * Se llama cuando se detecta o deja de detectar voz
     */
    fun onSpeechDetectionChanged(isDetected: Boolean) {}

    /**
     * Se llama cuando se recibe una transcripción parcial
     */
    fun onTranscriptionReceived(text: String, isComplete: Boolean) {}

    /**
     * Se llama cuando se completa una traducción
     */
    fun onTranslationCompleted(originalText: String?, translatedText: String?) {}

    /**
     * Se llama cuando se está reproduciendo audio traducido
     */
    fun onTranslatedAudioPlaying(durationMs: Long, audioLevel: Float) {}

    /**
     * Se llama cuando hay estadísticas actualizadas de traducción
     */
    fun onTranslationStatisticsUpdated(statistics: TranslationStatistics) {}

    /**
     * Se llama cuando hay un error de traducción
     */
    fun onTranslationError(error: TranslationError) {}

    /**
     * Se llama cuando cambia la calidad de la traducción
     */
    fun onTranslationQualityChanged(quality: TranslationQuality) {}
}

/**
 * Errores específicos de traducción
 */
data class TranslationError(
    val code: Int,
    val message: String,
    val category: TranslationErrorCategory,
    val isRecoverable: Boolean = true
)

enum class TranslationErrorCategory {
    NETWORK_CONNECTION,
    API_QUOTA_EXCEEDED,
    UNSUPPORTED_LANGUAGE,
    AUDIO_PROCESSING,
    AUTHENTICATION,
    CONFIGURATION
}

/**
 * Calidad de traducción
 */
data class TranslationQuality(
    val overallScore: Float, // 0.0 - 1.0
    val latency: Long, // ms
    val accuracy: Float, // 0.0 - 1.0
    val audioQuality: Float, // 0.0 - 1.0
    val networkStability: Float // 0.0 - 1.0
)