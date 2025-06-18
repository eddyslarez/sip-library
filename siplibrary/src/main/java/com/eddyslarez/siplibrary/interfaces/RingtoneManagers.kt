package com.eddyslarez.siplibrary.interfaces

/**
 * Interface para manejo de tonos de llamada
 *
 * @author Eddys Larez
 */
interface RingtoneManagers {
    fun playIncomingRingtone()
    fun playOutgoingRingtone()
    fun stopIncomingRingtone()
    fun stopOutgoingRingtone()
    fun stopAllRingtones()
    fun isRingtoneEnabled(): Boolean
    fun setRingtoneEnabled(enabled: Boolean)
    fun isPlaying(): Boolean
}