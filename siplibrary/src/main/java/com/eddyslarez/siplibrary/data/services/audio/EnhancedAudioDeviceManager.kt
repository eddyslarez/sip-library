package com.eddyslarez.siplibrary.data.services.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.eddyslarez.siplibrary.core.SipEventDispatcher
import com.eddyslarez.siplibrary.interfaces.AudioManager
import com.eddyslarez.siplibrary.utils.StateManager
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced Audio Device Manager con mejor detección
 *
 * @author Eddys Larez
 */
class EnhancedAudioDeviceManager(
    private val context: Context,
//    private val eventDispatcher: SipEventDispatcher
) : AudioManager {

    private val TAG = "EnhancedAudioDeviceManager"
    private var audioManager: android.media.AudioManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null
    private val deviceDetectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        initialize()
    }

    private fun initialize() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
        }

        setupAudioDeviceDetection()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setupAudioDeviceDetection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) {
                    log.d(tag = TAG) { "Audio devices added: ${addedDevices.size}" }
                    deviceDetectionScope.launch {
                        updateAvailableDevices()
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) {
                    log.d(tag = TAG) { "Audio devices removed: ${removedDevices.size}" }
                    deviceDetectionScope.launch {
                        updateAvailableDevices()
                    }
                }
            }

            audioManager?.registerAudioDeviceCallback(audioDeviceCallback!!, null)
        }
    }

    override suspend fun getAudioInputDevices(): List<AudioDevice> {
        return withContext(Dispatchers.IO) {
            val devices = mutableListOf<AudioDevice>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)?.forEach { deviceInfo ->
                    val audioDevice = convertToAudioDevice(deviceInfo, false)
                    if (audioDevice != null) {
                        devices.add(audioDevice)
                    }
                }
            } else {
                // Fallback para versiones anteriores
                devices.addAll(getLegacyInputDevices())
            }

            // Siempre incluir micrófono integrado
            if (devices.none { it.descriptor == "builtin_mic" }) {
                devices.add(AudioDevice(
                    name = "Built-in Microphone",
                    descriptor = "builtin_mic",
                    nativeDevice = null,
                    isOutput = false
                ))
            }

            log.d(tag = TAG) { "Found ${devices.size} input devices: ${devices.map { it.name }}" }
            devices
        }
    }

    override suspend fun getAudioOutputDevices(): List<AudioDevice> {
        return withContext(Dispatchers.IO) {
            val devices = mutableListOf<AudioDevice>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.forEach { deviceInfo ->
                    val audioDevice = convertToAudioDevice(deviceInfo, true)
                    if (audioDevice != null) {
                        devices.add(audioDevice)
                    }
                }
            } else {
                // Fallback para versiones anteriores
                devices.addAll(getLegacyOutputDevices())
            }

            // Siempre incluir altavoz
            if (devices.none { it.descriptor == "speaker" }) {
                devices.add(AudioDevice(
                    name = "Speaker",
                    descriptor = "speaker",
                    nativeDevice = null,
                    isOutput = true
                ))
            }

            log.d(tag = TAG) { "Found ${devices.size} output devices: ${devices.map { it.name }}" }
            devices
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun convertToAudioDevice(deviceInfo: android.media.AudioDeviceInfo, isOutput: Boolean): AudioDevice? {
        val deviceType = deviceInfo.type
        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            deviceInfo.productName?.toString() ?: getDeviceTypeName(deviceType, isOutput)
        } else {
            getDeviceTypeName(deviceType, isOutput)
        }

        val descriptor = getDeviceDescriptor(deviceType, deviceInfo.id, isOutput)

        return when (deviceType) {
            android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
                if (isOutput) AudioDevice(deviceName, "earpiece", deviceInfo, true) else null
            }
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                if (isOutput) AudioDevice(deviceName, "speaker", deviceInfo, true) else null
            }
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> {
                if (!isOutput) AudioDevice(deviceName, "builtin_mic", deviceInfo, false) else null
            }
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                AudioDevice(deviceName, if (isOutput) "wired_headset" else "wired_headset_mic", deviceInfo, isOutput)
            }
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                if (isOutput) AudioDevice(deviceName, "wired_headphones", deviceInfo, true) else null
            }
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                AudioDevice(deviceName, "bluetooth_sco_${deviceInfo.id}", deviceInfo, isOutput)
            }
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                if (isOutput) AudioDevice(deviceName, "bluetooth_a2dp_${deviceInfo.id}", deviceInfo, true) else null
            }
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> {
                AudioDevice(deviceName, "usb_${deviceInfo.id}", deviceInfo, isOutput)
            }
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> {
                AudioDevice(deviceName, if (isOutput) "usb_headset_${deviceInfo.id}" else "usb_headset_mic_${deviceInfo.id}", deviceInfo, isOutput)
            }
            else -> {
                log.d(tag = TAG) { "Unknown device type: $deviceType, name: $deviceName" }
                AudioDevice(deviceName, "unknown_${deviceType}_${deviceInfo.id}", deviceInfo, isOutput)
            }
        }
    }

    private fun getDeviceTypeName(deviceType: Int, isOutput: Boolean): String {
        return when (deviceType) {
            android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> if (isOutput) "Wired Headset" else "Wired Headset Microphone"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> if (isOutput) "Bluetooth Headset" else "Bluetooth Microphone"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> if (isOutput) "USB Audio" else "USB Microphone"
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> if (isOutput) "USB Headset" else "USB Headset Microphone"
            else -> "Unknown Audio Device"
        }
    }

    private fun getDeviceDescriptor(deviceType: Int, deviceId: Int, isOutput: Boolean): String {
        return when (deviceType) {
            android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "builtin_mic"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> if (isOutput) "wired_headset" else "wired_headset_mic"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth_sco_$deviceId"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth_a2dp_$deviceId"
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "usb_$deviceId"
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> if (isOutput) "usb_headset_$deviceId" else "usb_headset_mic_$deviceId"
            else -> "unknown_${deviceType}_$deviceId"
        }
    }

    private fun getLegacyInputDevices(): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        // Built-in microphone siempre existe
        devices.add(AudioDevice(
            name = "Built-in Microphone",
            descriptor = "builtin_mic",
            nativeDevice = null,
            isOutput = false
        ))

        // Wired headset mic
        if (audioManager?.isWiredHeadsetOn == true) {
            devices.add(AudioDevice(
                name = "Wired Headset Microphone",
                descriptor = "wired_headset_mic",
                nativeDevice = null,
                isOutput = false
            ))
        }

        // Bluetooth microphone
        if (audioManager?.isBluetoothScoAvailableOffCall == true) {
            devices.add(AudioDevice(
                name = "Bluetooth Microphone",
                descriptor = "bluetooth_mic",
                nativeDevice = null,
                isOutput = false
            ))
        }

        return devices
    }

    private fun getLegacyOutputDevices(): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        // Earpiece (solo en teléfonos)
        if (context.packageManager?.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) == true) {
            devices.add(AudioDevice(
                name = "Earpiece",
                descriptor = "earpiece",
                nativeDevice = null,
                isOutput = true
            ))
        }

        // Speaker siempre existe
        devices.add(AudioDevice(
            name = "Speaker",
            descriptor = "speaker",
            nativeDevice = null,
            isOutput = true
        ))

        // Wired headset
        if (audioManager?.isWiredHeadsetOn == true) {
            devices.add(AudioDevice(
                name = "Wired Headset",
                descriptor = "wired_headset",
                nativeDevice = null,
                isOutput = true
            ))
        }

        // Bluetooth headset
        if (audioManager?.isBluetoothScoAvailableOffCall == true) {
            devices.add(AudioDevice(
                name = "Bluetooth Headset",
                descriptor = "bluetooth_headset",
                nativeDevice = null,
                isOutput = true
            ))
        }

        return devices
    }

    override fun getCurrentInputDevice(): AudioDevice? {
        // Determinar el dispositivo de entrada actual basado en el estado del AudioManager
        return when {
            audioManager?.isBluetoothScoOn == true -> {
                AudioDevice("Bluetooth Microphone", "bluetooth_mic", null, false)
            }
            audioManager?.isWiredHeadsetOn == true -> {
                AudioDevice("Wired Headset Microphone", "wired_headset_mic", null, false)
            }
            else -> {
                AudioDevice("Built-in Microphone", "builtin_mic", null, false)
            }
        }
    }

    override fun getCurrentOutputDevice(): AudioDevice? {
        // Determinar el dispositivo de salida actual basado en el estado del AudioManager
        return when {
            audioManager?.isBluetoothScoOn == true -> {
                AudioDevice("Bluetooth Headset", "bluetooth_headset", null, true)
            }
            audioManager?.isSpeakerphoneOn == true -> {
                AudioDevice("Speaker", "speaker", null, true)
            }
            audioManager?.isWiredHeadsetOn == true -> {
                AudioDevice("Wired Headset", "wired_headset", null, true)
            }
            else -> {
                // Default to earpiece if available, otherwise speaker
                if (context.packageManager?.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) == true) {
                    AudioDevice("Earpiece", "earpiece", null, true)
                } else {
                    AudioDevice("Speaker", "speaker", null, true)
                }
            }
        }
    }

    override fun changeAudioInputDevice(device: AudioDevice): Boolean {
        log.d(tag = TAG) { "Changing input device to: ${device.name}" }

        try {
            when {
                device.descriptor.startsWith("bluetooth") -> {
                    if (audioManager?.isBluetoothScoAvailableOffCall == true) {
                        audioManager?.startBluetoothSco()
                        audioManager?.isBluetoothScoOn = true
                        return true
                    }
                }
                device.descriptor == "wired_headset_mic" -> {
                    if (audioManager?.isWiredHeadsetOn == true) {
                        // Wired headset input se activa automáticamente
                        return true
                    }
                }
                device.descriptor == "builtin_mic" -> {
                    // Desactivar bluetooth si está activo
                    if (audioManager?.isBluetoothScoOn == true) {
                        audioManager?.stopBluetoothSco()
                        audioManager?.isBluetoothScoOn = false
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error changing input device: ${e.message}" }
        }

        return false
    }

    override fun changeAudioOutputDevice(device: AudioDevice): Boolean {
        log.d(tag = TAG) { "Changing output device to: ${device.name}" }

        try {
            // Reset all modes first
            audioManager?.isSpeakerphoneOn = false
            if (audioManager?.isBluetoothScoOn == true) {
                audioManager?.stopBluetoothSco()
                audioManager?.isBluetoothScoOn = false
            }

            when {
                device.descriptor.startsWith("bluetooth") -> {
                    if (audioManager?.isBluetoothScoAvailableOffCall == true) {
                        audioManager?.startBluetoothSco()
                        audioManager?.isBluetoothScoOn = true
                        return true
                    }
                }
                device.descriptor == "speaker" -> {
                    audioManager?.isSpeakerphoneOn = true
                    return true
                }
                device.descriptor == "wired_headset" -> {
                    // Wired headset output se activa automáticamente cuando está conectado
                    return audioManager?.isWiredHeadsetOn == true
                }
                device.descriptor == "earpiece" -> {
                    // Earpiece is the default, just ensure others are disabled
                    return true
                }
            }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error changing output device: ${e.message}" }
        }

        return false
    }

    override fun setMuted(muted: Boolean) {
        audioManager?.isMicrophoneMute = muted
        deviceDetectionScope.launch {
            StateManager.updateMuteState(muted)
//            eventDispatcher.onMuteStateChanged(muted)
        }
    }

    override fun isMuted(): Boolean {
        return audioManager?.isMicrophoneMute ?: false
    }

    override fun setAudioEnabled(enabled: Boolean) {
        // Esta implementación dependerá del WebRTC manager
        log.d(tag = TAG) { "Audio enabled: $enabled" }
    }

    override fun diagnoseAudioIssues(): String {
        return buildString {
            appendLine("=== Enhanced Audio Diagnosis ===")
            appendLine("Audio Manager Available: ${audioManager != null}")
            appendLine("Audio Mode: ${audioManager?.mode}")
            appendLine("Speaker On: ${audioManager?.isSpeakerphoneOn}")
            appendLine("Mic Muted: ${audioManager?.isMicrophoneMute}")
            appendLine("Bluetooth SCO On: ${audioManager?.isBluetoothScoOn}")
            appendLine("Bluetooth SCO Available: ${audioManager?.isBluetoothScoAvailableOffCall}")
            appendLine("Wired Headset On: ${audioManager?.isWiredHeadsetOn}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val inputDevices = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                val outputDevices = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                appendLine("Input Devices: ${inputDevices?.size ?: 0}")
                appendLine("Output Devices: ${outputDevices?.size ?: 0}")
            }
        }
    }

    private suspend fun updateAvailableDevices() {
        try {
            val inputDevices = getAudioInputDevices()
            val outputDevices = getAudioOutputDevices()

            StateManager.updateAvailableAudioDevices(inputDevices, outputDevices)
//            eventDispatcher.onAudioDevicesAvailable(inputDevices, outputDevices)

            // Update current devices
            val currentInput = getCurrentInputDevice()
            val currentOutput = getCurrentOutputDevice()
            StateManager.updateCurrentAudioDevice(currentInput, currentOutput)

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error updating available devices: ${e.message}" }
        }
    }

    fun dispose() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback?.let { callback ->
                audioManager?.unregisterAudioDeviceCallback(callback)
            }
        }
        deviceDetectionScope.cancel()
    }
}