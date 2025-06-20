package com.eddyslarez.siplibrary.interfaces

import com.eddyslarez.siplibrary.data.models.CallState

/**
 * Interface para manejo de llamadas
 *
 * @author Eddys Larez
 */
interface CallManager {
    suspend fun makeCall(phoneNumber: String, username: String, domain: String, customHeaders: Map<String, String> = emptyMap()): String
    suspend fun acceptCall()
    suspend fun declineCall()
    suspend fun endCall()
    suspend fun holdCall()
    suspend fun resumeCall()
    fun sendDtmf(digit: Char, duration: Int = 160): Boolean
    fun sendDtmfSequence(digits: String, duration: Int = 160): Boolean
    fun getCurrentCallState(): CallState
    fun hasActiveCall(): Boolean
    fun isCallConnected(): Boolean
}