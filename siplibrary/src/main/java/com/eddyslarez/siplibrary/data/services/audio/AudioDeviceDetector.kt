package com.eddyslarez.siplibrary.data.services.audio

/**
 * Detector mejorado de dispositivos de audio con StateFlow
 *
 * @author Eddys Larez
 */
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AudioDeviceDetector(
    private val context: Context,
    private val audioManager: AudioManager
) {

    private val TAG = "AudioDeviceDetector"
    private val detectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // StateFlow para dispositivos de audio
    private val _inputDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val inputDevices: StateFlow<List<AudioDevice>> = _inputDevices.asStateFlow()

    private val _outputDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val outputDevices: StateFlow<List<AudioDevice>> = _outputDevices.asStateFlow()

    private val _currentInputDevice = MutableStateFlow<AudioDevice?>(null)
    val currentInputDevice: StateFlow<AudioDevice?> = _currentInputDevice.asStateFlow()

    private val _currentOutputDevice = MutableStateFlow<AudioDevice?>(null)
    val currentOutputDevice: StateFlow<AudioDevice?> = _currentOutputDevice.asStateFlow()

    // Receivers para cambios de dispositivos
    private var audioDeviceReceiver: BroadcastReceiver? = null
    private var bluetoothReceiver: BroadcastReceiver? = null

    init {
        startDeviceDetection()
    }

    @SuppressLint("MissingPermission")
    private fun startDeviceDetection() {
        detectorScope.launch {
            // Detección inicial
            updateAvailableDevices()
            updateCurrentDevices()

            // Configurar receivers para cambios
            setupAudioDeviceReceiver()
            setupBluetoothReceiver()

            // Polling periódico para dispositivos que no envían broadcast
            while (isActive) {
                delay(5000) // Cada 5 segundos
                updateAvailableDevices()
                updateCurrentDevices()
            }
        }
    }

    private fun setupAudioDeviceReceiver() {
        audioDeviceReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        log.d(tag = TAG) { "Headset plug event detected" }
                        detectorScope.launch {
                            delay(500) // Esperar a que el sistema se actualice
                            updateAvailableDevices()
                            updateCurrentDevices()
                        }
                    }
                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                        log.d(tag = TAG) { "Bluetooth SCO state changed" }
                        detectorScope.launch {
                            updateCurrentDevices()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        context.registerReceiver(audioDeviceReceiver, filter)
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun setupBluetoothReceiver() {
        bluetoothReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        log.d(tag = TAG) { "Bluetooth device connection state changed" }
                        detectorScope.launch {
                            delay(1000) // Dar tiempo para que el perfil se establezca
                            updateAvailableDevices()
                            updateCurrentDevices()
                        }
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        log.d(tag = TAG) { "Bluetooth adapter state changed" }
                        detectorScope.launch {
                            updateAvailableDevices()
                            updateCurrentDevices()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private suspend fun updateAvailableDevices() = withContext(Dispatchers.IO) {
        val newInputDevices = mutableListOf<AudioDevice>()
        val newOutputDevices = mutableListOf<AudioDevice>()

        try {
            // Dispositivos built-in siempre disponibles
            addBuiltInDevices(newInputDevices, newOutputDevices)

            // Detectar dispositivos conectados usando AudioManager API moderno
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addSystemAudioDevices(newInputDevices, newOutputDevices)
            } else {
                addLegacyAudioDevices(newInputDevices, newOutputDevices)
            }

            // Detectar dispositivos Bluetooth específicamente
            addBluetoothDevices(newInputDevices, newOutputDevices)

            _inputDevices.value = newInputDevices
            _outputDevices.value = newOutputDevices

            log.d(tag = TAG) { "Found ${newInputDevices.size} input and ${newOutputDevices.size} output devices" }

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error updating available devices: ${e.message}" }
        }
    }

    private fun addBuiltInDevices(inputDevices: MutableList<AudioDevice>, outputDevices: MutableList<AudioDevice>) {
        // Micrófono built-in siempre presente
        inputDevices.add(AudioDevice(
            name = "Built-in Microphone",
            descriptor = "builtin_mic",
            type = AudioDeviceType.BUILT_IN_MIC,
            isOutput = false,
            isConnected = true,
            priority = 1
        ))

        // Speaker siempre presente
        outputDevices.add(AudioDevice(
            name = "Speaker",
            descriptor = "speaker",
            type = AudioDeviceType.SPEAKER,
            isOutput = true,
            isConnected = true,
            priority = 3
        ))

        // Earpiece en teléfonos
        if (hasEarpiece()) {
            outputDevices.add(AudioDevice(
                name = "Earpiece",
                descriptor = "earpiece",
                type = AudioDeviceType.EARPIECE,
                isOutput = true,
                isConnected = true,
                priority = 2
            ))
        }
    }

    @SuppressLint("WrongConstant")
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun addSystemAudioDevices(inputDevices: MutableList<AudioDevice>, outputDevices: MutableList<AudioDevice>) {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)

        devices.forEach { deviceInfo ->
            val audioDevice = createAudioDeviceFromInfo(deviceInfo)
            if (audioDevice != null) {
                if (deviceInfo.isSink) {
                    outputDevices.add(audioDevice)
                }
                if (deviceInfo.isSource) {
                    val inputDevice = audioDevice.copy(isOutput = false)
                    inputDevices.add(inputDevice)
                }
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun createAudioDeviceFromInfo(deviceInfo: AudioDeviceInfo): AudioDevice? {
        return when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDevice(
                name = "Wired Headset",
                descriptor = "wired_headset",
                type = AudioDeviceType.WIRED_HEADSET,
                isOutput = deviceInfo.isSink,
                isConnected = true,
                priority = 4,
                nativeDevice = deviceInfo
            )
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDevice(
                name = "Wired Headphones",
                descriptor = "wired_headphones",
                type = AudioDeviceType.WIRED_HEADSET,
                isOutput = true,
                isConnected = true,
                priority = 4,
                nativeDevice = deviceInfo
            )
            AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDevice(
                name = "USB Headset",
                descriptor = "usb_headset",
                type = AudioDeviceType.USB_HEADSET,
                isOutput = deviceInfo.isSink,
                isConnected = true,
                priority = 5,
                nativeDevice = deviceInfo
            )
            else -> null
        }
    }

    private fun addLegacyAudioDevices(inputDevices: MutableList<AudioDevice>, outputDevices: MutableList<AudioDevice>) {
        // Para versiones Android < M, usar métodos legacy
        if (audioManager.isWiredHeadsetOn) {
            outputDevices.add(AudioDevice(
                name = "Wired Headset",
                descriptor = "wired_headset",
                type = AudioDeviceType.WIRED_HEADSET,
                isOutput = true,
                isConnected = true,
                priority = 4
            ))

            inputDevices.add(AudioDevice(
                name = "Wired Headset Microphone",
                descriptor = "wired_headset_mic",
                type = AudioDeviceType.WIRED_HEADSET,
                isOutput = false,
                isConnected = true,
                priority = 4
            ))
        }
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun addBluetoothDevices(inputDevices: MutableList<AudioDevice>, outputDevices: MutableList<AudioDevice>) {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter?.isEnabled == true) {
                // Verificar dispositivos emparejados con perfil A2DP o Headset
                val pairedDevices = bluetoothAdapter.bondedDevices

                pairedDevices.forEach { device ->
                    if (isBluetoothAudioDevice(device)) {
                        val deviceName = device.name ?: "Bluetooth Device"
                        val deviceAddress = device.address

                        // Verificar si está conectado
                        val isConnected = isBluetoothDeviceConnected(device)

                        if (isConnected) {
                            outputDevices.add(AudioDevice(
                                name = "$deviceName (Bluetooth)",
                                descriptor = "bluetooth_$deviceAddress",
                                type = AudioDeviceType.BLUETOOTH,
                                isOutput = true,
                                isConnected = true,
                                priority = 6,
                                nativeDevice = device
                            ))

                            inputDevices.add(AudioDevice(
                                name = "$deviceName Microphone (Bluetooth)",
                                descriptor = "bluetooth_mic_$deviceAddress",
                                type = AudioDeviceType.BLUETOOTH,
                                isOutput = false,
                                isConnected = true,
                                priority = 6,
                                nativeDevice = device
                            ))
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            log.w(tag = TAG) { "Bluetooth permission not granted: ${e.message}" }
        } catch (e: Exception) {
            log.e(tag = TAG) { "Error detecting Bluetooth devices: ${e.message}" }
        }
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun isBluetoothAudioDevice(device: BluetoothDevice): Boolean {
        val audioProfiles = setOf(
            BluetoothProfile.HEADSET,
            BluetoothProfile.A2DP,
            BluetoothProfile.HID_DEVICE
        )

        return try {
            device.uuids?.any { uuid ->
                // Verificar UUIDs comunes de audio
                uuid.toString().startsWith("0000110") || // Headset profiles
                        uuid.toString().startsWith("0000111") // A2DP profiles
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun isBluetoothDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothManager.getConnectedDevices(BluetoothProfile.GATT).contains(device) ||
                        bluetoothManager.getConnectedDevices(BluetoothProfile.A2DP).contains(device)
            } else {
                // Para versiones anteriores, verificar conexión SCO
                audioManager.isBluetoothScoAvailableOffCall && audioManager.isBluetoothScoOn
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun updateCurrentDevices() = withContext(Dispatchers.IO) {
        try {
            val currentInput = detectCurrentInputDevice()
            val currentOutput = detectCurrentOutputDevice()

            _currentInputDevice.value = currentInput
            _currentOutputDevice.value = currentOutput

        } catch (e: Exception) {
            log.e(tag = TAG) { "Error updating current devices: ${e.message}" }
        }
    }

    private fun detectCurrentInputDevice(): AudioDevice? {
        return when {
            audioManager.isBluetoothScoOn -> {
                _inputDevices.value.find { it.type == AudioDeviceType.BLUETOOTH }
            }
            audioManager.isWiredHeadsetOn -> {
                _inputDevices.value.find { it.type == AudioDeviceType.WIRED_HEADSET }
            }
            else -> {
                _inputDevices.value.find { it.type == AudioDeviceType.BUILT_IN_MIC }
            }
        }
    }

    private fun detectCurrentOutputDevice(): AudioDevice? {
        return when {
            audioManager.isBluetoothScoOn -> {
                _outputDevices.value.find { it.type == AudioDeviceType.BLUETOOTH }
            }
            audioManager.isSpeakerphoneOn -> {
                _outputDevices.value.find { it.type == AudioDeviceType.SPEAKER }
            }
            audioManager.isWiredHeadsetOn -> {
                _outputDevices.value.find { it.type == AudioDeviceType.WIRED_HEADSET }
            }
            hasEarpiece() -> {
                _outputDevices.value.find { it.type == AudioDeviceType.EARPIECE }
            }
            else -> {
                _outputDevices.value.find { it.type == AudioDeviceType.SPEAKER }
            }
        }
    }

    private fun hasEarpiece(): Boolean {
        return context.packageManager?.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ?: false
    }

    /**
     * Obtiene todos los dispositivos disponibles
     */
    fun getAllDevices(): Pair<List<AudioDevice>, List<AudioDevice>> {
        return Pair(_inputDevices.value, _outputDevices.value)
    }

    /**
     * Busca dispositivo por tipo
     */
    fun findDeviceByType(type: AudioDeviceType, isOutput: Boolean): AudioDevice? {
        val devices = if (isOutput) _outputDevices.value else _inputDevices.value
        return devices.find { it.type == type }
    }

    /**
     * Fuerza actualización de dispositivos
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun forceUpdate() {
        updateAvailableDevices()
        updateCurrentDevices()
    }

    fun dispose() {
        detectorScope.cancel()

        audioDeviceReceiver?.let { context.unregisterReceiver(it) }
        bluetoothReceiver?.let { context.unregisterReceiver(it) }

        log.d(tag = TAG) { "AudioDeviceDetector disposed" }
    }
}

