package com.eddyslarez.siplibrary.utils

import com.eddyslarez.siplibrary.EddysSipLibrary.NetworkQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Monitor de calidad de red
 */
class NetworkQualityMonitor(
    private val callback: (NetworkQuality) -> Unit
) {
    private var currentQuality: NetworkQuality? = null
    private val monitorJob = CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            updateNetworkQuality()
            delay(5000) // Actualizar cada 5 segundos
        }
    }


    private fun updateNetworkQuality() {
        // Simular medición de calidad de red
        val latency = (50..200).random().toLong()
        val packetLoss = (0..5).random().toFloat() / 100f
        val jitter = (5..50).random().toLong()
        val score = calculateScore(latency, packetLoss, jitter)


        currentQuality = NetworkQuality(
            score = score,
            latency = latency,
            packetLoss = packetLoss,
            jitter = jitter
        )


        currentQuality?.let { callback(it) }
    }


    private fun calculateScore(latency: Long, packetLoss: Float, jitter: Long): Float {
        val latencyScore = when {
            latency < 100 -> 1.0f
            latency < 200 -> 0.8f
            latency < 300 -> 0.6f
            else -> 0.4f
        }


        val packetLossScore = when {
            packetLoss < 0.01f -> 1.0f
            packetLoss < 0.03f -> 0.8f
            packetLoss < 0.05f -> 0.6f
            else -> 0.4f
        }


        val jitterScore = when {
            jitter < 20 -> 1.0f
            jitter < 40 -> 0.8f
            jitter < 60 -> 0.6f
            else -> 0.4f
        }


        return (latencyScore + packetLossScore + jitterScore) / 3f
    }


    fun getCurrentQuality(): NetworkQuality? = currentQuality


    fun dispose() {
        monitorJob.cancel()
    }
}
