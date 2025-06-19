package com.eddyslarez.siplibrary.managers

/**
 * Despachador de eventos que permite múltiples listeners
 *
 * @author Eddys Larez
 */
import com.eddyslarez.siplibrary.interfaces.SipEventHandler
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class EventDispatcher {

     val TAG = "EventDispatcher"
    private val dispatchScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Almacena listeners por tipo de interface
     val listeners = ConcurrentHashMap<KClass<out SipEventHandler>, MutableSet<SipEventHandler>>()

    // Estadísticas de eventos
    private val eventStats = ConcurrentHashMap<String, Int>()

    /**
     * Registra un listener para eventos específicos
     */
    inline fun <reified T : SipEventHandler> registerListener(listener: T) {
        val listenerClass = T::class
        listeners.computeIfAbsent(listenerClass) { ConcurrentHashMap.newKeySet() }.add(listener)
        log.d(tag = TAG) { "Registered listener for ${listenerClass.simpleName}" }
    }

    /**
     * Desregistra un listener
     */
    inline fun <reified T : SipEventHandler> unregisterListener(listener: T) {
        val listenerClass = T::class
        listeners[listenerClass]?.remove(listener)
        if (listeners[listenerClass]?.isEmpty() == true) {
            listeners.remove(listenerClass)
        }
        log.d(tag = TAG) { "Unregistered listener for ${listenerClass.simpleName}" }
    }

    /**
     * Despacha eventos a todos los listeners registrados del tipo correspondiente
     */
    inline fun <reified T : SipEventHandler> dispatch(noinline action: (T) -> Unit) {
        val listenerClass = T::class
        val targetListeners = listeners[listenerClass]?.toList() ?: return

        dispatchScope.launch {
            targetListeners.forEach { listener ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    action(listener as T)

                    // Actualizar estadísticas
                    val eventName = "${listenerClass.simpleName}.${Thread.currentThread().stackTrace[3].methodName}"
                    eventStats[eventName] = (eventStats[eventName] ?: 0) + 1

                } catch (e: Exception) {
                    log.e(tag = TAG) { "Error dispatching event to ${listenerClass.simpleName}: ${e.message}" }
                }
            }
        }
    }

    /**
     * Despacha eventos de manera síncrona (para casos críticos)
     */
    inline fun <reified T : SipEventHandler> dispatchSync(noinline action: (T) -> Unit) {
        val listenerClass = T::class
        val targetListeners = listeners[listenerClass]?.toList() ?: return

        targetListeners.forEach { listener ->
            try {
                @Suppress("UNCHECKED_CAST")
                action(listener as T)
            } catch (e: Exception) {
                log.e(tag = TAG) { "Error dispatching sync event to ${listenerClass.simpleName}: ${e.message}" }
            }
        }
    }

    /**
     * Obtiene estadísticas de eventos
     */
    fun getEventStatistics(): Map<String, Int> = eventStats.toMap()

    /**
     * Limpia todas las estadísticas
     */
    fun clearStatistics() {
        eventStats.clear()
    }

    /**
     * Obtiene el número de listeners registrados por tipo
     */
    fun getListenerCounts(): Map<String, Int> {
        return listeners.mapKeys { it.key.simpleName ?: "Unknown" }
            .mapValues { it.value.size }
    }

    /**
     * Libera recursos
     */
    fun dispose() {
        dispatchScope.cancel()
        listeners.clear()
        eventStats.clear()
        log.d(tag = TAG) { "EventDispatcher disposed" }
    }
}