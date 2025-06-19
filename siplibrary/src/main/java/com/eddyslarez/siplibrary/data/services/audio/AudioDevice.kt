package com.eddyslarez.siplibrary.data.services.audio


/**
 * Tipos de dispositivos de audio extendidos
 */
enum class AudioDeviceType {
    BUILT_IN_MIC,
    BUILT_IN_SPEAKER,
    EARPIECE,
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
    USB_HEADSET,
    USB_DEVICE,
    UNKNOWN
}

/**
 * Dispositivo de audio extendido con más información
 */
data class AudioDevice(
    val name: String,
    val descriptor: String,
    val type: AudioDeviceType = AudioDeviceType.UNKNOWN,
    val isOutput: Boolean,
    val isConnected: Boolean = false,
    val priority: Int = 0, // Para ordenar por preferencia
    val nativeDevice: Any? = null,
    val capabilities: Set<String> = emptySet()
)