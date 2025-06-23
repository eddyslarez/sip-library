package com.eddyslarez.siplibrary.extensions

import android.app.Application
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.core.GlobalEventBus
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.error.ErrorCategory
import com.eddyslarez.siplibrary.error.ErrorCodes
import com.eddyslarez.siplibrary.error.SipError
import com.eddyslarez.siplibrary.error.SipLibraryException
import com.eddyslarez.siplibrary.events.SipEvent
import com.eddyslarez.siplibrary.interfaces.SipEventListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map



/**
 * Extensiones para facilitar el uso del EventBus desde cualquier parte
 */
object SipEventBusExtensions {

    /**
     * Agrega un listener de manera fácil
     */
    suspend fun addListener(listener: SipEventListener) {
        GlobalEventBus.addListener(listener)
    }

    /**
     * Remueve un listener
     */
    suspend fun removeListener(listener: SipEventListener) {
        GlobalEventBus.removeListener(listener)
    }

    /**
     * Observa eventos específicos - CORREGIDO
     */
    fun <T : SipEvent> observeEvents(eventClass: Class<T>): Flow<T> {
        return GlobalEventBus.getEventFlowOfType(eventClass)
    }

    /**
     * Observa errores específicos
     */
    fun observeErrors(): Flow<SipError> {
        return GlobalEventBus.getEventFlowOfType(SipEvent.Error::class.java).map { it.error }
    }

    /**
     * Observa cambios de estado de llamada
     */
    fun observeCallStateChanges(): Flow<CallState> {
        return GlobalEventBus.getEventFlowOfType(SipEvent.CallStateChanged::class.java).map { it.newState }
    }

    /**
     * Observa llamadas entrantes
     */
    fun observeIncomingCalls(): Flow<SipEvent.IncomingCall> {
        return GlobalEventBus.getEventFlowOfType(SipEvent.IncomingCall::class.java)
    }

}



/**
 * Extensiones para manejo de errores
 */
object ErrorExtensions {

    /**
     * Maneja errores de manera consistente
     */
    inline fun <T> handleSipResult(
        result: Result<T>,
        onSuccess: (T) -> Unit = {},
        onError: (SipLibraryException) -> Unit = {}
    ) {
        result.fold(
            onSuccess = onSuccess,
            onFailure = { throwable ->
                when (throwable) {
                    is SipLibraryException -> onError(throwable)
                    else -> onError(SipLibraryException(
                        "Unexpected error: ${throwable.message}",
                        ErrorCodes.UNEXPECTED_ERROR,
                        ErrorCategory.CONFIGURATION,
                        throwable
                    ))
                }
            }
        )
    }

    /**
     * Convierte un Result a un valor nullable con manejo de errores
     */
    fun <T> Result<T>.getOrHandleError(onError: (SipLibraryException) -> Unit = {}): T? {
        return fold(
            onSuccess = { it },
            onFailure = { throwable ->
                when (throwable) {
                    is SipLibraryException -> onError(throwable)
                    else -> onError(SipLibraryException(
                        "Unexpected error: ${throwable.message}",
                        ErrorCodes.UNEXPECTED_ERROR,
                        ErrorCategory.CONFIGURATION,
                        throwable
                    ))
                }
                null
            }
        )
    }
}

/**
 * Extensiones para simplificar el uso de la biblioteca
 */
object SipLibraryExtensions {

    /**
     * Inicialización simple con configuración mínima
     */
    suspend fun initializeSipLibrary(
        application: Application,
        domain: String,
        webSocketUrl: String,
        userAgent: String = "EddysSipLibrary/3.0.0"
    ): Result<Unit> {
        val config = EddysSipLibrary.SipConfig(
            defaultDomain = domain,
            webSocketUrl = webSocketUrl,
            userAgent = userAgent
        )

        return EddysSipLibrary.getInstance().initialize(application, config)
    }

    /**
     * Registro simple de cuenta
     */
    suspend fun registerSimpleAccount(
        username: String,
        password: String,
        domain: String? = null
    ): Result<Unit> {
        return EddysSipLibrary.getInstance().registerAccount(
            username = username,
            password = password,
            domain = domain
        )
    }

    /**
     * Llamada simple
     */
    suspend fun makeSimpleCall(phoneNumber: String): Result<String> {
        return EddysSipLibrary.getInstance().makeCall(phoneNumber)
    }
}

/**
 * DSL para configuración fácil
 */
class SipConfigBuilder {
    private var defaultDomain: String = ""
    private var webSocketUrl: String = ""
    private var userAgent: String = ""
    private var enableLogs: Boolean = true
    private var autoReconnect: Boolean = true
    private var customHeaders: Map<String, String> = emptyMap()

    fun domain(domain: String) { this.defaultDomain = domain }
    fun webSocketUrl(url: String) { this.webSocketUrl = url }
    fun userAgent(agent: String) { this.userAgent = agent }
    fun enableLogs(enable: Boolean) { this.enableLogs = enable }
    fun autoReconnect(enable: Boolean) { this.autoReconnect = enable }
    fun customHeaders(headers: Map<String, String>) { this.customHeaders = headers }

    fun build(): EddysSipLibrary.SipConfig {
        return EddysSipLibrary.SipConfig(
            defaultDomain = defaultDomain,
            webSocketUrl = webSocketUrl,
            userAgent = userAgent,
            enableLogs = enableLogs,
            autoReconnect = autoReconnect,
            customHeaders = customHeaders
        )
    }
}

/**
 * Función DSL para crear configuración
 */
fun sipConfig(block: SipConfigBuilder.() -> Unit): EddysSipLibrary.SipConfig {
    return SipConfigBuilder().apply(block).build()
}
