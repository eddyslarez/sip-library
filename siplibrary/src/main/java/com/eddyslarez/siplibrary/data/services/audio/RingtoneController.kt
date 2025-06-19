package com.eddyslarez.siplibrary.data.services.audio

/**
 * Controlador mejorado de ringtone con gestión automática
 *
 * @author Eddys Larez
 */
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
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RingtoneController(
    private val context: Context,
    private val config: RingtoneConfig
) {

    private val TAG = "RingtoneController"
    private val ringtoneScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Players
    private var incomingRingtone: MediaPlayer? = null
    private var outgoingRingtone: MediaPlayer? = null
    private var notificationPlayer: MediaPlayer? = null

    // Vibrator
    private var vibrator: Vibrator? = null

    // Estados
    private val _isPlayingIncoming = MutableStateFlow(false)
    val isPlayingIncoming: StateFlow<Boolean> = _isPlayingIncoming.asStateFlow()

    private val _isPlayingOutgoing = MutableStateFlow(false)
    val isPlayingOutgoing: StateFlow<Boolean> = _isPlayingOutgoing.asStateFlow()

    private var currentRingtoneJob: Job? = null

    init {
        initializeVibrator()
    }

    private fun initializeVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Reproduce ringtone para llamada entrante
     */
    fun playIncomingRingtone() {
        if (!config.enableIncomingRingtone) {
            log.d(tag = TAG) { "Incoming ringtone disabled in config" }
            return
        }

        ringtoneScope.launch {
            try {
                stopAllRingtones() // Detener cualquier ringtone activo

                val ringtoneUri = getRingtoneUri(RingtoneType.INCOMING)
                incomingRingtone = createMediaPlayer(ringtoneUri, isIncoming = true)

                incomingRingtone?.let { player ->
                    player.isLooping = true
                    player.prepare()
                    player.start()

                    _isPlayingIncoming.value = true

                    // Vibración si está habilitada
                    if (config.enableVibration) {
                        startVibration(VibrationPattern.INCOMING_CALL)
                    }

                    // Auto-stop después del timeout
                    currentRingtoneJob = launch {
                        delay(config.incomingRingtoneTimeoutMs)
                        stopIncomingRingtone()
                    }

                    log.d(tag = TAG) { "Incoming ringtone started" }
                }

            } catch (e: Exception) {
                log.e(tag = TAG) { "Error playing incoming ringtone: ${e.message}" }
                _isPlayingIncoming.value = false
            }
        }
    }

    /**
     * Reproduce ringtone para llamada saliente
     */
    fun playOutgoingRingtone() {
        if (!config.enableOutgoingRingtone) {
            log.d(tag = TAG) { "Outgoing ringtone disabled in config" }
            return
        }

        ringtoneScope.launch {
            try {
                stopAllRingtones()

                val ringtoneUri = getRingtoneUri(RingtoneType.OUTGOING)
                outgoingRingtone = createMediaPlayer(ringtoneUri, isIncoming = false)

                outgoingRingtone?.let { player ->
                    player.isLooping = true
                    player.prepare()
                    player.start()

                    _isPlayingOutgoing.value = true

                    // Auto-stop después del timeout
                    currentRingtoneJob = launch {
                        delay(config.outgoingRingtoneTimeoutMs)
                        stopOutgoingRingtone()
                    }

                    log.d(tag = TAG) { "Outgoing ringtone started" }
                }

            } catch (e: Exception) {
                log.e(tag = TAG) { "Error playing outgoing ringtone: ${e.message}" }
                _isPlayingOutgoing.value = false
            }
        }
    }

    /**
     * Detiene ringtone de llamada entrante
     */
    fun stopIncomingRingtone() {
        currentRingtoneJob?.cancel()

        try {
            incomingRingtone?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            incomingRingtone = null
            _isPlayingIncoming.value = false

            stopVibration()

            log.d(tag = TAG) { "Incoming ringtone stopped" }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error stopping incoming ringtone: ${e.message}" }
        }
    }

    /**
     * Detiene ringtone de llamada saliente
     */
    fun stopOutgoingRingtone() {
        currentRingtoneJob?.cancel()

        try {
            outgoingRingtone?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            outgoingRingtone = null
            _isPlayingOutgoing.value = false

            log.d(tag = TAG) { "Outgoing ringtone stopped" }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error stopping outgoing ringtone: ${e.message}" }
        }
    }

    /**
     * Detiene todos los ringtones activos
     */
    fun stopAllRingtones() {
        stopIncomingRingtone()
        stopOutgoingRingtone()
        stopNotificationSound()
    }

    /**
     * Reproduce sonido de notificación
     */
    fun playNotificationSound(type: NotificationType) {
        if (!config.enableNotificationSounds) return

        ringtoneScope.launch {
            try {
                stopNotificationSound()

                val notificationUri = getNotificationUri(type)
                notificationPlayer = createMediaPlayer(notificationUri, isIncoming = false)

                notificationPlayer?.let { player ->
                    player.prepare()
                    player.start()

                    // Auto-release cuando termine
                    player.setOnCompletionListener {
                        it.release()
                        notificationPlayer = null
                    }
                }

            } catch (e: Exception) {
                log.e(tag = TAG) { "Error playing notification sound: ${e.message}" }
            }
        }
    }

    private fun stopNotificationSound() {
        try {
            notificationPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            notificationPlayer = null
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error stopping notification sound: ${e.message}" }
        }
    }

    private fun createMediaPlayer(uri: Uri, isIncoming: Boolean): MediaPlayer {
        return MediaPlayer().apply {
            setDataSource(context, uri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usage = if (isIncoming) {
                    AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                } else {
                    AudioAttributes.USAGE_VOICE_COMMUNICATION
                }

                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                val streamType = if (isIncoming) {
                    AudioManager.STREAM_RING
                } else {
                    AudioManager.STREAM_VOICE_CALL
                }
                setAudioStreamType(streamType)
            }

            // Configurar volumen
            val volume = if (isIncoming) config.incomingVolume else config.outgoingVolume
            setVolume(volume, volume)
        }
    }

    private fun getRingtoneUri(type: RingtoneType): Uri {
        return when (type) {
            RingtoneType.INCOMING -> {
                config.customIncomingRingtone
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            RingtoneType.OUTGOING -> {
                config.customOutgoingRingtone
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
        }
    }

    private fun getNotificationUri(type: NotificationType): Uri {
        return when (type) {
            NotificationType.CALL_ENDED -> {
                config.customCallEndedSound
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            NotificationType.CALL_CONNECTED -> {
                config.customCallConnectedSound
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            NotificationType.DTMF_TONE -> {
                // Para tonos DTMF se puede usar un sonido específico
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
        }
    }

    private fun startVibration(pattern: VibrationPattern) {
        vibrator?.let { vib ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = when (pattern) {
                        VibrationPattern.INCOMING_CALL -> {
                            VibrationEffect.createWaveform(
                                longArrayOf(0, 1000, 500, 1000, 500),
                                0 // Repetir
                            )
                        }
                        VibrationPattern.NOTIFICATION -> {
                            VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    when (pattern) {
                        VibrationPattern.INCOMING_CALL -> {
                            vib.vibrate(longArrayOf(0, 1000, 500, 1000, 500), 0)
                        }
                        VibrationPattern.NOTIFICATION -> {
                            vib.vibrate(200)
                        }
                    }
                }
            } catch (e: Exception) {
                log.e(tag = TAG) { "Error starting vibration: ${e.message}" }
            }
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error stopping vibration: ${e.message}" }
        }
    }

    /**
     * Actualiza configuración de ringtone
     */
    fun updateConfig(newConfig: RingtoneConfig) {
        // Aplicar nueva configuración
        // Si hay cambios críticos, reiniciar ringtones activos
        if (newConfig.enableIncomingRingtone != config.enableIncomingRingtone ||
            newConfig.enableOutgoingRingtone != config.enableOutgoingRingtone) {
            stopAllRingtones()
        }
    }

    fun dispose() {
        ringtoneScope.cancel()
        stopAllRingtones()
        stopVibration()
        log.d(tag = TAG) { "RingtoneController disposed" }
    }
}

/**
 * Configuración de ringtones
 */
data class RingtoneConfig(
    val enableIncomingRingtone: Boolean = true,
    val enableOutgoingRingtone: Boolean = true,
    val enableNotificationSounds: Boolean = true,
    val enableVibration: Boolean = true,
    val incomingVolume: Float = 1.0f,
    val outgoingVolume: Float = 0.7f,
    val incomingRingtoneTimeoutMs: Long = 30000L, // 30 segundos
    val outgoingRingtoneTimeoutMs: Long = 60000L, // 60 segundos
    val customIncomingRingtone: Uri? = null,
    val customOutgoingRingtone: Uri? = null,
    val customCallEndedSound: Uri? = null,
    val customCallConnectedSound: Uri? = null
)

enum class RingtoneType {
    INCOMING, OUTGOING
}

enum class NotificationType {
    CALL_ENDED, CALL_CONNECTED, DTMF_TONE
}

enum class VibrationPattern {
    INCOMING_CALL, NOTIFICATION
}