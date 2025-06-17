package com.eddyslarez.siplibrary.data.models

import com.eddyslarez.siplibrary.data.services.websocket.MultiplatformWebSocket
import com.eddyslarez.siplibrary.utils.generateId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.datetime.Clock
import kotlin.random.Random
/**
 * Información de cuenta SIP y contenedor de estado - Versión mejorada
 *
 * @author Eddys Larez
 */
class AccountInfo(
    val username: String,
    val password: String,
    val domain: String
) {

    // Connection state
    var webSocketClient: MultiplatformWebSocket? = null
    var reconnectionJob: Job? = null
    val reconnectionScope = CoroutineScope(Dispatchers.IO)
    var reconnectCount = 0

    // SIP headers and identifiers
    var callId: String? = null
    var fromTag: String? = null
    var toTag: String? = null
    var cseq: Int = 1
    var fromHeader: String? = null
    var toHeader: String? = null
    var viaHeader: String? = null
    var fromUri: String? = null
    var toUri: String? = null
    var remoteContact: String? = null
    var userAgent: String? = null

    // Authentication
    var authorizationHeader: String? = null
    var challengeNonce: String? = null
    var realm: String? = null
    var authRetryCount: Int = 0
    var method: String? = null

    // WebRTC data
    var useWebRTCFormat = false
    var remoteSdp: String? = null
    var iceUfrag: String? = null
    var icePwd: String? = null
    var dtlsFingerprint: String? = null
    var setupRole: String? = null

    // Call state
    var currentCallData: CallData? = null
    var isRegistered: Boolean = false
    var isCallConnected: Boolean = false
    var hasIncomingCall: Boolean = false
    var callStartTime: Long = 0L

    // Push notifications and registration
    var token: String = ""
    var provider: String = ""
    var expirationSeconds: Int = 3600

    // Configuración personalizada
    var customHeaders: Map<String, String> = emptyMap()
    var customContactParams: Map<String, String> = emptyMap()

    /**
     * Genera un identificador único para requests SIP
     */
    fun generateId(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(100000)}"
    }

    /**
     * Parsea URI de contacto desde header Contact
     */
    fun parseContactUri(contactHeader: String): String {
        val uriMatch = Regex("<([^>]+)>").find(contactHeader)
        return if (uriMatch != null) {
            uriMatch.groupValues[1]
        } else {
            val uriPart = contactHeader.substringAfter(":", "").substringBefore(";", "").trim()
            if (uriPart.isNotEmpty()) uriPart else contactHeader
        }
    }

    /**
     * Construye el header Contact con parámetros personalizados
     */
    fun buildContactHeader(isBackground: Boolean = false): String {
        val baseContact = "sip:$username@$domain;transport=ws"
        val params = mutableListOf<String>()

        // Agregar parámetros push si está en background
        if (isBackground && token.isNotEmpty()) {
            params.add("pn-prid=$token")
            params.add("pn-provider=$provider")
        }

        // Agregar parámetros personalizados
        customContactParams.forEach { (key, value) ->
            params.add("$key=$value")
        }

        val paramString = if (params.isNotEmpty()) {
            ";" + params.joinToString(";")
        } else {
            ""
        }

        return "<$baseContact$paramString>"
    }

    /**
     * Obtiene headers SIP personalizados
     */
    fun getCustomSipHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()

        // Agregar User-Agent si está definido
        userAgent?.let { headers["User-Agent"] = it }

        // Agregar headers personalizados
        headers.putAll(customHeaders)

        return headers
    }

    /**
     * Resetea estado de llamada para preparar nueva llamada
     */
    fun resetCallState() {
        isCallConnected = false
        hasIncomingCall = false
        callStartTime = 0L
        currentCallData = null
    }

    /**
     * Resetea estado de autenticación para nuevo intento de registro
     */
    fun resetAuthState() {
        authRetryCount = 0
        challengeNonce = null
        authorizationHeader = null
    }

    /**
     * Obtiene string de identidad de cuenta
     */
    fun getAccountIdentity(): String {
        return "$username@$domain"
    }

    /**
     * Verifica si la cuenta tiene configuración de push válida
     */
    fun hasPushConfiguration(): Boolean {
        return token.isNotEmpty() && provider.isNotEmpty()
    }

    /**
     * Actualiza configuración de push
     */
    fun updatePushConfiguration(newToken: String, newProvider: String = "fcm") {
        token = newToken
        provider = newProvider
    }

    /**
     * Obtiene el tiempo restante de registro en segundos
     */
    fun getRegistrationTimeRemaining(): Long {
        // Implementar lógica para calcular tiempo restante
        return expirationSeconds.toLong()
    }

    /**
     * Verifica si necesita renovar el registro
     */
    fun needsRegistrationRenewal(renewBeforeSeconds: Int = 60): Boolean {
        return getRegistrationTimeRemaining() <= renewBeforeSeconds
    }
}
//
///**
// * Información de cuenta SIP y contenedor de estado
// *
// * @author Eddys Larez
// */
//class AccountInfo(
//    val username: String,
//    val password: String,
//    val domain: String
//) {
//
//    // Connection state
//    var webSocketClient: MultiplatformWebSocket? = null
//    var reconnectionJob: Job? = null
//    val reconnectionScope = CoroutineScope(Dispatchers.IO)
//    var reconnectCount = 0
//
//    // SIP headers and identifiers
//    var callId: String? = null
//    var fromTag: String? = null
//    var toTag: String? = null
//    var cseq: Int = 1
//    var fromHeader: String? = null
//    var toHeader: String? = null
//    var viaHeader: String? = null
//    var fromUri: String? = null
//    var toUri: String? = null
//    var remoteContact: String? = null
//    var userAgent: String? = null
//
//    // Authentication
//    var authorizationHeader: String? = null
//    var challengeNonce: String? = null
//    var realm: String? = null
//    var authRetryCount: Int = 0
//    var method: String? = null
//
//    // WebRTC data
//    var useWebRTCFormat = false
//    var remoteSdp: String? = null
//    var iceUfrag: String? = null
//    var icePwd: String? = null
//    var dtlsFingerprint: String? = null
//    var setupRole: String? = null
//
//    // Call state
//    var currentCallData: CallData? = null
//    var isRegistered: Boolean = false
//    var isCallConnected: Boolean = false
//    var hasIncomingCall: Boolean = false
//    var callStartTime: Long = 0L
//
//    var token: String = ""
//    var provider: String = ""
//
//    /**
//     * Genera un identificador único para requests SIP
//     */
//    fun generateId(): String {
//        return "${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(100000)}"
//    }
//
//    /**
//     * Parsea URI de contacto desde header Contact
//     */
//    fun parseContactUri(contactHeader: String): String {
//        val uriMatch = Regex("<([^>]+)>").find(contactHeader)
//        return if (uriMatch != null) {
//            uriMatch.groupValues[1]
//        } else {
//            val uriPart = contactHeader.substringAfter(":", "").substringBefore(";", "").trim()
//            if (uriPart.isNotEmpty()) uriPart else contactHeader
//        }
//    }
//
//    /**
//     * Resetea estado de llamada para preparar nueva llamada
//     */
//    fun resetCallState() {
//        isCallConnected = false
//        hasIncomingCall = false
//        callStartTime = 0L
//        currentCallData = null
//    }
//
//    /**
//     * Resetea estado de autenticación para nuevo intento de registro
//     */
//    fun resetAuthState() {
//        authRetryCount = 0
//        challengeNonce = null
//        authorizationHeader = null
//    }
//
//    /**
//     * Obtiene string de identidad de cuenta
//     */
//    fun getAccountIdentity(): String {
//        return "$username@$domain"
//    }
//}