package com.eddyslarez.siplibrary.interfaces

import com.eddyslarez.siplibrary.data.services.audio.AudioDevice

/**
 * Interface para manejo de audio
 *
 * @author Eddys Larez
 */
interface AudioManager {
    suspend fun getAudioInputDevices(): List<AudioDevice>
    suspend fun getAudioOutputDevices(): List<AudioDevice>
    fun getCurrentInputDevice(): AudioDevice?
    fun getCurrentOutputDevice(): AudioDevice?
    fun changeAudioInputDevice(device: AudioDevice): Boolean
    fun changeAudioOutputDevice(device: AudioDevice): Boolean
    fun setMuted(muted: Boolean)
    fun isMuted(): Boolean
    fun setAudioEnabled(enabled: Boolean)
    fun diagnoseAudioIssues(): String
}