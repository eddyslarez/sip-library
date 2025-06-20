package com.eddyslarez.siplibrary

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.eddyslarez.siplibrary.core.SipEventDispatcher
import com.eddyslarez.siplibrary.interfaces.SipEventListener
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
//
///**
// * Gestor del ciclo de vida de la aplicación para manejo automático de estados
// *
// * @author Eddys Larez
// */
//class AppLifecycleManager(
//    private val application: Application,
//    private var config: EddysSipLibrary.SipConfig,
//    private var eventListener: SipEventListener?
//) {
//
//    private val TAG = "AppLifecycleManager"
//    private var currentAppState = EddysSipLibrary.AppState.FOREGROUND
//    private var autoPushModeEnabled = true
//    private var networkMonitor: NetworkMonitor? = null
//    private var batteryOptimizationChecker: BatteryOptimizationChecker? = null
//
//    init {
//        setupLifecycleObserver()
//        setupNetworkMonitoring()
//        setupBatteryOptimizationMonitoring()
//    }
//
//    fun setEventListener(listener: SipEventListener) {
//        this.eventListener = listener
//    }
//
//    fun updateConfig(newConfig: EddysSipLibrary.SipConfig) {
//        this.config = newConfig
//        autoPushModeEnabled = newConfig.autoEnterPushOnBackground
//    }
//
//    fun setAutoPushMode(enabled: Boolean) {
//        autoPushModeEnabled = enabled
//    }
//
//    private fun setupLifecycleObserver() {
//        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
//            override fun onStart(owner: LifecycleOwner) {
//                handleAppStateChange(EddysSipLibrary.AppState.FOREGROUND)
//            }
//
//            override fun onStop(owner: LifecycleOwner) {
//                handleAppStateChange(EddysSipLibrary.AppState.BACKGROUND)
//            }
//        })
//
//        // Detectar terminación de la app
//        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
//            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
//            override fun onActivityStarted(activity: Activity) {}
//            override fun onActivityResumed(activity: Activity) {}
//            override fun onActivityPaused(activity: Activity) {}
//            override fun onActivityStopped(activity: Activity) {}
//            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
//            override fun onActivityDestroyed(activity: Activity) {
//                // Detectar si es la última actividad
//                CoroutineScope(Dispatchers.IO).launch {
//                    delay(1000) // Esperar para ver si hay más actividades
//                    if (isAppTerminating()) {
//                        handleAppStateChange(EddysSipLibrary.AppState.TERMINATED)
//                    }
//                }
//            }
//        })
//    }
//
//    private fun setupNetworkMonitoring() {
//        networkMonitor = NetworkMonitor(application) { isConnected, networkType ->
//            eventListener?.onNetworkStateChanged(isConnected, networkType)
//
//            if (!isConnected) {
//                eventListener?.onWarning(EddysSipLibrary.SipWarning(
//                    message = "Network connection lost",
//                    category = EddysSipLibrary.WarningCategory.NETWORK_QUALITY
//                ))
//            }
//        }
//    }
//
//    private fun setupBatteryOptimizationMonitoring() {
//        batteryOptimizationChecker = BatteryOptimizationChecker(application) { isOptimized ->
//            if (isOptimized) {
//                eventListener?.onWarning(EddysSipLibrary.SipWarning(
//                    message = "Battery optimization is enabled, this may affect call quality and push notifications",
//                    category = EddysSipLibrary.WarningCategory.BATTERY_OPTIMIZATION
//                ))
//            }
//        }
//    }
//
//    private fun handleAppStateChange(newState: EddysSipLibrary.AppState) {
//        val previousState = currentAppState
//        currentAppState = newState
//
//        log.d(tag = TAG) { "App state changed: $previousState -> $newState" }
//        eventListener?.onAppStateChanged(newState, previousState)
//
//        when (newState) {
//            EddysSipLibrary.AppState.FOREGROUND -> {
//                handleForegroundTransition()
//            }
//            EddysSipLibrary.AppState.BACKGROUND -> {
//                handleBackgroundTransition()
//            }
//            EddysSipLibrary.AppState.TERMINATED -> {
//                handleTerminationTransition()
//            }
//        }
//    }
//
//    private fun handleForegroundTransition() {
//        if (config.autoExitPushOnForeground && autoPushModeEnabled) {
//            CoroutineScope(Dispatchers.IO).launch {
//                delay(config.pushReconnectDelayMs)
//                EddysSipLibrary.getInstance().exitPushMode("App entered foreground")
//            }
//        }
//    }
//
//    private fun handleBackgroundTransition() {
//        if (config.autoEnterPushOnBackground && autoPushModeEnabled) {
//            CoroutineScope(Dispatchers.IO).launch {
//                delay(config.pushReconnectDelayMs)
//                EddysSipLibrary.getInstance().enterPushMode("App entered background")
//            }
//        }
//
//        if (config.autoDisconnectWebSocketOnBackground) {
//            // Desconectar WebSocket si está configurado
//            CoroutineScope(Dispatchers.IO).launch {
//                delay(5000) // Esperar 5 segundos antes de desconectar
//                // Implementar desconexión de WebSocket
//            }
//        }
//    }
//
//    private fun handleTerminationTransition() {
//        // Limpiar recursos antes de terminar
//        EddysSipLibrary.getInstance().dispose()
//    }
//
//    @SuppressLint("ServiceCast")
//    private fun isAppTerminating(): Boolean {
//        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
//        val tasks = activityManager.getRunningTasks(1)
//        return tasks.isEmpty() || !tasks[0].topActivity?.packageName.equals(application.packageName)
//    }
//
//    fun dispose() {
//        networkMonitor?.dispose()
//        batteryOptimizationChecker?.dispose()
//    }
//}
//
///**
// * Monitor de red para detectar cambios de conectividad
// */
//class NetworkMonitor(
//    private val context: Context,
//    private val callback: (Boolean, String) -> Unit
//) {
//    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//    private var networkCallback: ConnectivityManager.NetworkCallback? = null
//
//    init {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            networkCallback = object : ConnectivityManager.NetworkCallback() {
//                override fun onAvailable(network: Network) {
//                    val networkInfo = connectivityManager.getNetworkCapabilities(network)
//                    val networkType = getNetworkType(networkInfo)
//                    callback(true, networkType)
//                }
//
//                override fun onLost(network: Network) {
//                    callback(false, "none")
//                }
//            }
//            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
//        }
//    }
//
//    private fun getNetworkType(capabilities: NetworkCapabilities?): String {
//        return when {
//            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
//            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
//            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
//            else -> "unknown"
//        }
//    }
//
//    fun dispose() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
//        }
//    }
//}
//
///**
// * Checker para optimización de batería
// */
//class BatteryOptimizationChecker(
//    private val context: Context,
//    private val callback: (Boolean) -> Unit
//) {
//    init {
//        checkBatteryOptimization()
//    }
//
//    @SuppressLint("ServiceCast")
//    private fun checkBatteryOptimization() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
//            val packageName = context.packageName
//            val isOptimized = !powerManager.isIgnoringBatteryOptimizations(packageName)
//            callback(isOptimized)
//        }
//    }
//
//    fun dispose() {
//        // No cleanup needed
//    }
//}

/**
 * Gestor del ciclo de vida de la aplicación para manejo automático de estados
 *
 * @author Eddys Larez
 */
class AppLifecycleManager(
    private val application: Application,
    private var config: EddysSipLibrary.SipConfig,
    private val eventDispatcher: SipEventDispatcher
) {

    private val TAG = "AppLifecycleManager"
    private var currentAppState = EddysSipLibrary.AppState.FOREGROUND
    private var autoPushModeEnabled = true
    private var networkMonitor: NetworkMonitor? = null
    private var batteryOptimizationChecker: BatteryOptimizationChecker? = null

    init {
        setupLifecycleObserver()
        setupNetworkMonitoring()
        setupBatteryOptimizationMonitoring()
    }

    fun updateConfig(newConfig: EddysSipLibrary.SipConfig) {
        this.config = newConfig
        autoPushModeEnabled = newConfig.autoEnterPushOnBackground
    }

    fun setAutoPushMode(enabled: Boolean) {
        autoPushModeEnabled = enabled
    }

    private fun setupLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handleAppStateChange(EddysSipLibrary.AppState.FOREGROUND)
            }

            override fun onStop(owner: LifecycleOwner) {
                handleAppStateChange(EddysSipLibrary.AppState.BACKGROUND)
            }
        })

        // Detectar terminación de la app
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                // Detectar si es la última actividad
                CoroutineScope(Dispatchers.IO).launch {
                    delay(1000) // Esperar para ver si hay más actividades
                    if (isAppTerminating()) {
                        handleAppStateChange(EddysSipLibrary.AppState.TERMINATED)
                    }
                }
            }
        })
    }

    private fun setupNetworkMonitoring() {
        networkMonitor = NetworkMonitor(application) { isConnected, networkType ->
            CoroutineScope(Dispatchers.IO).launch {
                eventDispatcher.onNetworkStateChanged(isConnected, networkType)

                if (!isConnected) {
//                    eventDispatcher.onWarning(EddysSipLibrary.SipWarning(
//                        message = "Network connection lost",
//                        category = EddysSipLibrary.WarningCategory.NETWORK_QUALITY
//                    ))
                }
            }
        }
    }

    private fun setupBatteryOptimizationMonitoring() {
        batteryOptimizationChecker = BatteryOptimizationChecker(application) { isOptimized ->
            if (isOptimized) {
                CoroutineScope(Dispatchers.IO).launch {
//                    eventDispatcher.onWarning(EddysSipLibrary.SipWarning(
//                        message = "Battery optimization is enabled, this may affect call quality and push notifications",
//                        category = EddysSipLibrary.WarningCategory.BATTERY_OPTIMIZATION
//                    ))
                }
            }
        }
    }

    private fun handleAppStateChange(newState: EddysSipLibrary.AppState) {
        val previousState = currentAppState
        currentAppState = newState

        log.d(tag = TAG) { "App state changed: $previousState -> $newState" }

        CoroutineScope(Dispatchers.IO).launch {
            eventDispatcher.onAppStateChanged(newState, previousState)
        }

        when (newState) {
            EddysSipLibrary.AppState.FOREGROUND -> {
                handleForegroundTransition()
            }
            EddysSipLibrary.AppState.BACKGROUND -> {
                handleBackgroundTransition()
            }
            EddysSipLibrary.AppState.TERMINATED -> {
                handleTerminationTransition()
            }
        }
    }

    private fun handleForegroundTransition() {
        if (config.autoExitPushOnForeground && autoPushModeEnabled) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(config.pushReconnectDelayMs)
//                EddysSipLibrary.getInstance().exitPushMode("App entered foreground")
            }
        }
    }

    private fun handleBackgroundTransition() {
        if (config.autoEnterPushOnBackground && autoPushModeEnabled) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(config.pushReconnectDelayMs)
//                EddysSipLibrary.getInstance().enterPushMode("App entered background")
            }
        }

//        if (config.autoDisconnectWebSocketOnBackground) {
//            // Desconectar WebSocket si está configurado
//            CoroutineScope(Dispatchers.IO).launch {
//                delay(5000) // Esperar 5 segundos antes de desconectar
//                // Implementar desconexión de WebSocket
//            }
//        }
    }

    private fun handleTerminationTransition() {
        // Limpiar recursos antes de terminar
        EddysSipLibrary.getInstance().dispose()
    }

    @SuppressLint("ServiceCast")
    private fun isAppTerminating(): Boolean {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = activityManager.getRunningTasks(1)
        return tasks.isEmpty() || !tasks[0].topActivity?.packageName.equals(application.packageName)
    }

    fun dispose() {
        networkMonitor?.dispose()
        batteryOptimizationChecker?.dispose()
    }
}

/**
 * Monitor de red para detectar cambios de conectividad
 */
class NetworkMonitor(
    private val context: Context,
    private val callback: (Boolean, String) -> Unit
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val networkInfo = connectivityManager.getNetworkCapabilities(network)
                    val networkType = getNetworkType(networkInfo)
                    callback(true, networkType)
                }

                override fun onLost(network: Network) {
                    callback(false, "none")
                }
            }
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        }
    }

    private fun getNetworkType(capabilities: NetworkCapabilities?): String {
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            else -> "unknown"
        }
    }

    fun dispose() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        }
    }
}

/**
 * Checker para optimización de batería
 */
class BatteryOptimizationChecker(
    private val context: Context,
    private val callback: (Boolean) -> Unit
) {
    init {
        checkBatteryOptimization()
    }

    @SuppressLint("ServiceCast")
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = context.packageName
            val isOptimized = !powerManager.isIgnoringBatteryOptimizations(packageName)
            callback(isOptimized)
        }
    }

    fun dispose() {
        // No cleanup needed
    }
}