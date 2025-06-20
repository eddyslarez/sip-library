package com.eddyslarez.siplibrary.error

import kotlinx.datetime.Clock

/**
 * Sistema mejorado de manejo de errores
 *
 * @author Eddys Larez
 */

/**
 * Códigos de error estándar
 */
object ErrorCodes {
    // Errores de inicialización
    const val INITIALIZATION_FAILED = 1001
    const val NOT_INITIALIZED = 1002
    const val ALREADY_INITIALIZED = 1003

    // Errores de registro
    const val REGISTRATION_FAILED = 2001
    const val NO_REGISTERED_ACCOUNT = 2002
    const val AUTHENTICATION_FAILED = 2003

    // Errores de llamadas
    const val CALL_INITIATION_FAILED = 3001
    const val CALL_ACCEPT_FAILED = 3002
    const val CALL_DECLINE_FAILED = 3003
    const val CALL_END_FAILED = 3004
    const val CALL_HOLD_FAILED = 3005
    const val CALL_RESUME_FAILED = 3006

    // Errores de audio
    const val AUDIO_MANAGER_UNAVAILABLE = 4001
    const val AUDIO_DEVICE_CHANGE_FAILED = 4002
    const val AUDIO_PERMISSION_DENIED = 4003

    // Errores de red
    const val NETWORK_CONNECTION_FAILED = 5001
    const val WEBSOCKET_CONNECTION_FAILED = 5002
    const val NETWORK_TIMEOUT = 5003

    // Errores de protocolo SIP
    const val SIP_MESSAGE_PARSE_FAILED = 6001
    const val SIP_AUTHENTICATION_FAILED = 6002
    const val DTMF_SEND_FAILED = 6003

    // Errores de traducción
    const val TRANSLATION_INIT_FAILED = 7001
    const val TRANSLATION_API_FAILED = 7002
    const val TRANSLATION_CONFIG_INVALID = 7003

    // Errores generales
    const val UNEXPECTED_ERROR = 9999
}

/**
 * Categorías de error
 */
enum class ErrorCategory {
    NETWORK,
    AUTHENTICATION,
    AUDIO,
    SIP_PROTOCOL,
    WEBRTC,
    CONFIGURATION,
    TRANSLATION,
    PERMISSION
}

/**
 * Clase de error SIP mejorada
 */
data class SipError(
    val code: Int,
    val message: String,
    val category: ErrorCategory,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val cause: Throwable? = null,
    val recoverable: Boolean = true,
    val userMessage: String? = null
) {
    /**
     * Obtiene un mensaje amigable para el usuario
     */
    fun getUserFriendlyMessage(): String {
        return userMessage ?: when (category) {
            ErrorCategory.NETWORK -> "Problem with network connection. Please check your internet connection."
            ErrorCategory.AUTHENTICATION -> "Authentication failed. Please check your credentials."
            ErrorCategory.AUDIO -> "Audio device problem. Please check your audio settings."
            ErrorCategory.SIP_PROTOCOL -> "Communication error with server. Please try again."
            ErrorCategory.WEBRTC -> "Media connection problem. Please check your network."
            ErrorCategory.CONFIGURATION -> "Configuration error. Please check your settings."
            ErrorCategory.TRANSLATION -> "Translation service error. Please try again."
            ErrorCategory.PERMISSION -> "Permission required. Please grant necessary permissions."
        }
    }

    /**
     * Verifica si el error es recuperable
     */
    fun isRecoverable(): Boolean = recoverable

    /**
     * Obtiene sugerencias de recuperación
     */
    fun getRecoverySuggestions(): List<String> {
        return when (category) {
            ErrorCategory.NETWORK -> listOf(
                "Check your internet connection",
                "Try switching between WiFi and mobile data",
                "Contact your network administrator"
            )
            ErrorCategory.AUTHENTICATION -> listOf(
                "Verify your username and password",
                "Check domain configuration",
                "Contact your service provider"
            )
            ErrorCategory.AUDIO -> listOf(
                "Check audio device permissions",
                "Try a different audio device",
                "Restart the application"
            )
            ErrorCategory.SIP_PROTOCOL -> listOf(
                "Try again in a moment",
                "Check server configuration",
                "Contact technical support"
            )
            ErrorCategory.WEBRTC -> listOf(
                "Check network connectivity",
                "Try closing other applications",
                "Restart the application"
            )
            ErrorCategory.CONFIGURATION -> listOf(
                "Review configuration settings",
                "Reset to default settings",
                "Contact support for assistance"
            )
            ErrorCategory.TRANSLATION -> listOf(
                "Check internet connection",
                "Verify API key configuration",
                "Try again later"
            )
            ErrorCategory.PERMISSION -> listOf(
                "Grant required permissions in settings",
                "Restart the application",
                "Check device permissions"
            )
        }
    }
}

/**
 * Advertencias del sistema
 */
data class SipWarning(
    val message: String,
    val category: WarningCategory,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val actionable: Boolean = true
)

enum class WarningCategory {
    AUDIO_QUALITY,
    NETWORK_QUALITY,
    BATTERY_OPTIMIZATION,
    PERMISSION,
    CONFIGURATION
}

/**
 * Excepción personalizada de la biblioteca
 */
class SipLibraryException(
    message: String,
    val sipError: SipError,
    cause: Throwable? = null
) : Exception(message, cause) {

    constructor(message: String, code: Int, category: ErrorCategory, cause: Throwable? = null) : this(
        message,
        SipError(code, message, category, cause = cause),
        cause
    )

    /**
     * Obtiene el mensaje amigable para el usuario
     */
    fun getUserFriendlyMessage(): String = sipError.getUserFriendlyMessage()

    /**
     * Verifica si el error es recuperable
     */
    fun isRecoverable(): Boolean = sipError.isRecoverable()

    /**
     * Obtiene sugerencias de recuperación
     */
    fun getRecoverySuggestions(): List<String> = sipError.getRecoverySuggestions()
}

/**
 * Errores específicos de traducción - CORREGIDO
 */
data class TranslationError(
    val code: Int,
    val message: String,
    val category: TranslationErrorCategory,
    val isRecoverable: Boolean = true,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
) {
    fun getUserFriendlyMessage(): String {
        return when (category) {
            TranslationErrorCategory.NETWORK_CONNECTION -> "Translation service is not available. Please check your connection."
            TranslationErrorCategory.API_QUOTA_EXCEEDED -> "Translation quota exceeded. Please try again later."
            TranslationErrorCategory.UNSUPPORTED_LANGUAGE -> "Language not supported for translation."
            TranslationErrorCategory.AUDIO_PROCESSING -> "Audio processing error. Please check your microphone."
            TranslationErrorCategory.AUTHENTICATION -> "Translation service authentication failed."
            TranslationErrorCategory.CONFIGURATION -> "Translation configuration error."
        }
    }
}

enum class TranslationErrorCategory {
    NETWORK_CONNECTION,
    API_QUOTA_EXCEEDED,
    UNSUPPORTED_LANGUAGE,
    AUDIO_PROCESSING,
    AUTHENTICATION,
    CONFIGURATION
}