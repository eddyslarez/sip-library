package com.eddyslarez.siplibrary.data.services.audio

import android.Manifest
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import com.eddyslarez.siplibrary.core.SipEventDispatcher
import com.eddyslarez.siplibrary.interfaces.RingtoneManagers
import com.eddyslarez.siplibrary.utils.StateManager
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Enhanced Ringtone Manager con configuración completa
 *
 * @author Eddys Larez
 */
class EnhancedRingtoneManager(
    private val application: Application,
//    private val eventDispatcher: SipEventDispatcher
) : RingtoneManagers {

    private val TAG = "EnhancedRingtoneManager"
    private var incomingRingtone: MediaPlayer? = null
    private var outgoingRingtone: MediaPlayer? = null
    private var isRingtoneEnabled = true
    private val ringtoneScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Configuración del ringtone
    private var customIncomingRingtoneUri: Uri? = null
    private var customOutgoingRingtoneUri: Uri? = null
    private var ringtoneVolume: Float = 1.0f
    private var vibrationEnabled = true

    init {
        initializeRingtoneSettings()
        observeRingtoneState()
    }

    private fun initializeRingtoneSettings() {
        // Cargar configuración guardada
        ringtoneScope.launch {
            // Aquí cargarías las preferencias guardadas
            StateManager.setRingtoneEnabled(isRingtoneEnabled)
        }
    }

    private fun observeRingtoneState() {
        ringtoneScope.launch {
            StateManager.ringtoneStateFlow.collect { state ->
                isRingtoneEnabled = state.isEnabled
            }
        }
    }

    override fun playIncomingRingtone() {
        if (!isRingtoneEnabled) {
            log.d(tag = TAG) { "Ringtone disabled, not playing incoming ringtone" }
            return
        }

        ringtoneScope.launch {
            try {
                stopIncomingRingtone() // Asegurar que no hay otro reproduciéndose

                val ringtoneUri = customIncomingRingtoneUri
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                incomingRingtone = MediaPlayer().apply {
                    setDataSource(application, ringtoneUri)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(AudioManager.STREAM_RING)
                    }

                    setVolume(ringtoneVolume, ringtoneVolume)
                    isLooping = true

                    setOnCompletionListener {
                        StateManager.setIncomingRingtoneState(false)
                    }

                    setOnErrorListener { _, what, extra ->
                        log.e(tag = TAG) { "Error playing incoming ringtone: what=$what, extra=$extra" }
                        StateManager.setIncomingRingtoneState(false)
                        true
                    }

                    prepare()
                    start()
                }

                StateManager.setIncomingRingtoneState(true)
                log.d(tag = TAG) { "Incoming ringtone started" }

                // Vibration if enabled
                if (vibrationEnabled) {
                    startVibration()
                }

            } catch (e: Exception) {
                log.e(tag = TAG) { "Error playing incoming ringtone: ${e.message}" }
                StateManager.setIncomingRingtoneState(false)
            }
        }
    }

    override fun playOutgoingRingtone() {
        if (!isRingtoneEnabled) {
            log.d(tag = TAG) { "Ringtone disabled, not playing outgoing ringtone" }
            return
        }

        ringtoneScope.launch {
            try {
                stopOutgoingRingtone() // Asegurar que no hay otro reproduciéndose

                val ringtoneUri = customOutgoingRingtoneUri
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                outgoingRingtone = MediaPlayer().apply {
                    setDataSource(application, ringtoneUri)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
                    }

                    setVolume(ringtoneVolume * 0.7f, ringtoneVolume * 0.7f) // Más bajo para outgoing
                    isLooping = true

                    setOnCompletionListener {
                        StateManager.setOutgoingRingtoneState(false)
                    }

                    setOnErrorListener { _, what, extra ->
                        log.e(tag = TAG) { "Error playing outgoing ringtone: what=$what, extra=$extra" }
                        StateManager.setOutgoingRingtoneState(false)
                        true
                    }

                    prepare()
                    start()
                }

                StateManager.setOutgoingRingtoneState(true)
                log.d(tag = TAG) { "Outgoing ringtone started" }

            } catch (e: Exception) {
                log.e(tag = TAG) { "Error playing outgoing ringtone: ${e.message}" }
                StateManager.setOutgoingRingtoneState(false)
            }
        }
    }

    override fun stopIncomingRingtone() {
        ringtoneScope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.VIBRATE) {
            try {
                incomingRingtone?.let { player ->
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                }
                incomingRingtone = null
                StateManager.setIncomingRingtoneState(false)
                stopVibration()
                log.d(tag = TAG) { "Incoming ringtone stopped" }
            } catch (e: Exception) {
                log.e(tag = TAG) { "Error stopping incoming ringtone: ${e.message}" }
                StateManager.setIncomingRingtoneState(false)
            }
        }
    }

    override fun stopOutgoingRingtone() {
        ringtoneScope.launch {
            try {
                outgoingRingtone?.let { player ->
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                }
                outgoingRingtone = null
                StateManager.setOutgoingRingtoneState(false)
                log.d(tag = TAG) { "Outgoing ringtone stopped" }
            } catch (e: Exception) {
                log.e(tag = TAG) { "Error stopping outgoing ringtone: ${e.message}" }
                StateManager.setOutgoingRingtoneState(false)
            }
        }
    }

    override fun stopAllRingtones() {
        stopIncomingRingtone()
        stopOutgoingRingtone()
        log.d(tag = TAG) { "All ringtones stopped" }
    }

    override fun isRingtoneEnabled(): Boolean = isRingtoneEnabled

    override fun setRingtoneEnabled(enabled: Boolean) {
        isRingtoneEnabled = enabled
        StateManager.setRingtoneEnabled(enabled)

        if (!enabled) {
            stopAllRingtones()
        }

        log.d(tag = TAG) { "Ringtone enabled: $enabled" }
    }

    override fun isPlaying(): Boolean {
        val state = StateManager.getCurrentRingtoneState()
        return state.isIncomingPlaying || state.isOutgoingPlaying
    }

    // Métodos adicionales para configuración
    fun setCustomIncomingRingtone(uri: Uri?) {
        customIncomingRingtoneUri = uri
        log.d(tag = TAG) { "Custom incoming ringtone set: $uri" }
    }

    fun setCustomOutgoingRingtone(uri: Uri?) {
        customOutgoingRingtoneUri = uri
        log.d(tag = TAG) { "Custom outgoing ringtone set: $uri" }
    }

    fun setRingtoneVolume(volume: Float) {
        ringtoneVolume = volume.coerceIn(0f, 1f)
        log.d(tag = TAG) { "Ringtone volume set: $ringtoneVolume" }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
        log.d(tag = TAG) { "Vibration enabled: $enabled" }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun startVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                val effect = VibrationEffect.createWaveform(pattern, 0)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                vibrator.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error starting vibration: ${e.message}" }
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun stopVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            vibrator.cancel()
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error stopping vibration: ${e.message}" }
        }
    }

    fun dispose() {
        stopAllRingtones()
        ringtoneScope.cancel()
    }
}