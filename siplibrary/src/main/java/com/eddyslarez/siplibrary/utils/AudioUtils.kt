package com.eddyslarez.siplibrary.utils

/**
 * Utilidades para manejo de audio en traducción
 *
 * @author Eddys Larez
 */
import android.Manifest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object AudioUtils {

    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Convierte audio de PCM16 a bytes
     */
    fun pcm16ToBytes(pcmData: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(pcmData.size * 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcmData) {
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Convierte bytes a PCM16
     */
    fun bytesToPcm16(bytes: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val pcmData = ShortArray(bytes.size / 2)
        for (i in pcmData.indices) {
            pcmData[i] = buffer.getShort(i * 2)
        }
        return pcmData
    }

    /**
     * Convierte Float32Array a PCM16
     */
    fun floatToPcm16(floatArray: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floatArray.size * 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        for (sample in floatArray) {
            val clampedSample = sample.coerceIn(-1.0f, 1.0f)
            val pcmSample = if (clampedSample < 0) {
                (clampedSample * 32768).toInt().toShort()
            } else {
                (clampedSample * 32767).toInt().toShort()
            }
            buffer.putShort(pcmSample)
        }

        return buffer.array()
    }

    /**
     * Convierte PCM16 a Float32Array
     */
    fun pcm16ToFloat(pcmBytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(pcmBytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(pcmBytes.size / 2)

        for (i in floatArray.indices) {
            val pcmSample = buffer.getShort(i * 2)
            floatArray[i] = pcmSample / if (pcmSample < 0) 32768.0f else 32767.0f
        }

        return floatArray
    }

    /**
     * Aplicar ganancia al audio
     */
    fun applyGain(audioData: ByteArray, gainFactor: Float): ByteArray {
        val pcmData = bytesToPcm16(audioData)
        val amplifiedData = ShortArray(pcmData.size)

        for (i in pcmData.indices) {
            val amplified = (pcmData[i] * gainFactor).toInt()
            amplifiedData[i] = amplified.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return pcm16ToBytes(amplifiedData)
    }

    /**
     * Detectar silencio en audio
     */
    fun isSilence(audioData: ByteArray, threshold: Float = 0.01f): Boolean {
        val pcmData = bytesToPcm16(audioData)
        var sum = 0.0

        for (sample in pcmData) {
            sum += abs(sample.toDouble())
        }

        val average = sum / pcmData.size
        return average < (threshold * Short.MAX_VALUE)
    }

    /**
     * Calcular RMS (Root Mean Square) del audio
     */
    fun calculateRMS(audioData: ByteArray): Float {
        val pcmData = bytesToPcm16(audioData)
        var sum = 0.0

        for (sample in pcmData) {
            sum += sample.toDouble().pow(2.0)
        }

        return sqrt(sum / pcmData.size).toFloat()
    }

    /**
     * Normalizar audio
     */
    fun normalizeAudio(audioData: ByteArray): ByteArray {
        val pcmData = bytesToPcm16(audioData)
        val maxValue = pcmData.maxOfOrNull { abs(it.toInt()) } ?: 1

        if (maxValue == 0) return audioData

        val normalizationFactor = Short.MAX_VALUE.toFloat() / maxValue
        val normalizedData = ShortArray(pcmData.size)

        for (i in pcmData.indices) {
            normalizedData[i] = (pcmData[i] * normalizationFactor).toInt().toShort()
        }

        return pcm16ToBytes(normalizedData)
    }

    /**
     * Mezclar dos streams de audio
     */
    fun mixAudio(audio1: ByteArray, audio2: ByteArray, ratio1: Float = 0.5f): ByteArray {
        val pcm1 = bytesToPcm16(audio1)
        val pcm2 = bytesToPcm16(audio2)
        val maxLength = maxOf(pcm1.size, pcm2.size)
        val mixed = ShortArray(maxLength)
        val ratio2 = 1.0f - ratio1

        for (i in 0 until maxLength) {
            val sample1 = if (i < pcm1.size) pcm1[i] else 0
            val sample2 = if (i < pcm2.size) pcm2[i] else 0

            val mixedSample = (sample1 * ratio1 + sample2 * ratio2).toInt()
            mixed[i] = mixedSample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return pcm16ToBytes(mixed)
    }

    /**
     * Aplicar fade in/out al audio
     */
    fun applyFade(audioData: ByteArray, fadeInSamples: Int = 0, fadeOutSamples: Int = 0): ByteArray {
        val pcmData = bytesToPcm16(audioData)
        val fadedData = ShortArray(pcmData.size)

        for (i in pcmData.indices) {
            var factor = 1.0f

            // Fade in
            if (i < fadeInSamples) {
                factor = i.toFloat() / fadeInSamples
            }

            // Fade out
            if (i >= pcmData.size - fadeOutSamples) {
                val fadeOutPosition = pcmData.size - i
                factor = minOf(factor, fadeOutPosition.toFloat() / fadeOutSamples)
            }

            fadedData[i] = (pcmData[i] * factor).toInt().toShort()
        }

        return pcm16ToBytes(fadedData)
    }

    /**
     * Crear AudioRecord configurado para traducción
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun createAudioRecord(): AudioRecord? {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)

        return try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize * 2
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Crear AudioTrack configurado para traducción
     */
    fun createAudioTrack(): AudioTrack? {
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)

        return try {
            AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtener el buffer size recomendado
     */
    fun getRecommendedBufferSize(): Int {
        return AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT) * 2
    }

    /**
     * Verificar si el audio está en formato correcto
     */
    fun isValidAudioFormat(audioData: ByteArray): Boolean {
        return audioData.size > 0 && audioData.size % 2 == 0
    }

    /**
     * Crear chunk de silencio
     */
    fun createSilence(durationMs: Int): ByteArray {
        val samplesCount = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        return ByteArray(samplesCount * 2) // 16-bit = 2 bytes per sample
    }

    /**
     * Resamplear audio (básico)
     */
    fun resample(audioData: ByteArray, fromSampleRate: Int, toSampleRate: Int): ByteArray {
        if (fromSampleRate == toSampleRate) return audioData

        val pcmData = bytesToPcm16(audioData)
        val ratio = toSampleRate.toDouble() / fromSampleRate
        val newSize = (pcmData.size * ratio).toInt()
        val resampled = ShortArray(newSize)

        for (i in resampled.indices) {
            val sourceIndex = (i / ratio).toInt()
            if (sourceIndex < pcmData.size) {
                resampled[i] = pcmData[sourceIndex]
            }
        }

        return pcm16ToBytes(resampled)
    }
}