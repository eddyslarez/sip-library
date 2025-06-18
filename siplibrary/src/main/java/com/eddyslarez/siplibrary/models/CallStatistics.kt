package com.eddyslarez.siplibrary.models

/**
 * Estadísticas de llamada extendidas
 *
 * @author Eddys Larez
 */
data class CallStatistics(
    val id: String,
    val duration: Long,
    val audioCodec: String,
    val networkQuality: Float,
    val packetsLost: Int,
    val jitter: Long,
    val rtt: Long,
    val bitrate: Int = 0,
    val audioLevel: Float = 0f
)