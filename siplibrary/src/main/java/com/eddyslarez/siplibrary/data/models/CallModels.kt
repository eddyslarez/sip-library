package com.eddyslarez.siplibrary.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.datetime.Clock
/**
 * Modelos de datos para llamadas - Versión mejorada
 *
 * @author Eddys Larez
 */

@Parcelize
enum class CallState : Parcelable {
    NONE,
    INCOMING,
    OUTGOING,
    CALLING,
    RINGING,
    CONNECTED,
    HOLDING,
    ACCEPTING,
    ENDING,
    ENDED,
    DECLINED,
    ERROR
}

@Parcelize
enum class CallDirections : Parcelable {
    INCOMING,
    OUTGOING
}

@Parcelize
enum class CallTypes : Parcelable {
    SUCCESS,
    MISSED,
    DECLINED,
    ABORTED
}

@Parcelize
enum class RegistrationState : Parcelable {
    PROGRESS,
    OK,
    CLEARED,
    NONE,
    IN_PROGRESS,
    FAILED
}

@Parcelize
data class CallData(
    var callId: String = "",
    val from: String = "",
    val to: String = "",
    val direction: CallDirections = CallDirections.OUTGOING,
    val startTime: Long = Clock.System.now().toEpochMilliseconds(),
    var toTag: String? = null,
    var fromTag: String? = null,
    var remoteContactUri: String? = null,
    var remoteContactParams: Map<String, String> = emptyMap(),
    val remoteDisplayName: String = "",
    var inviteFromTag: String = "",
    var inviteToTag: String = "",
    var remoteSdp: String = "",
    var localSdp: String = "",
    var inviteViaBranch: String = "",
    var via: String = "",
    var originalInviteMessage: String = "",
    var originalCallInviteMessage: String = "",
    var isOnHold: Boolean? = null,
    var lastCSeqValue: Int = 0,
    var sipName: String = "",

    // Nuevos campos para funcionalidad mejorada
    var customHeaders: Map<String, String> = emptyMap(),
    var callQuality: CallQuality = CallQuality.UNKNOWN,
    var encryption: EncryptionType = EncryptionType.NONE,
    var codecUsed: String = "",
    var bandwidth: Int = 0, // in kbps
    var packetLoss: Float = 0.0f, // percentage
    var jitter: Float = 0.0f, // in ms
    var rtt: Float = 0.0f, // round trip time in ms
    var callEndReason: CallEndReason = CallEndReason.UNKNOWN
) : Parcelable {

    fun storeInviteMessage(message: String) {
        originalInviteMessage = message
    }

    fun getRemoteParty(): String {
        return when (direction) {
            CallDirections.OUTGOING -> to
            CallDirections.INCOMING -> from
        }
    }

    fun getLocalParty(): String {
        return when (direction) {
            CallDirections.OUTGOING -> from
            CallDirections.INCOMING -> to
        }
    }

    /**
     * Obtiene la duración de la llamada en segundos
     */
    fun getDurationSeconds(): Long {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        return (currentTime - startTime) / 1000
    }

    /**
     * Verifica si la llamada está activa
     */
    fun isActive(): Boolean {
        return callId.isNotEmpty() &&
                (isOnHold == false || isOnHold == null)
    }

    /**
     * Obtiene información de calidad de la llamada
     */
    fun getQualityInfo(): CallQualityInfo {
        return CallQualityInfo(
            quality = callQuality,
            codec = codecUsed,
            bandwidth = bandwidth,
            packetLoss = packetLoss,
            jitter = jitter,
            rtt = rtt,
            encryption = encryption
        )
    }

    override fun toString(): String {
        return "CallData(id=$callId, $from→$to, dir=$direction, started=$startTime, " +
                "fromTag=$fromTag, toTag=$toTag, quality=$callQuality)"
    }
}

@Parcelize
data class CallLog(
    val id: String,
    val direction: CallDirections,
    val to: String,
    val formattedTo: String,
    val from: String,
    val formattedFrom: String,
    val contact: String?,
    val formattedStartDate: String,
    val duration: Int, // in seconds
    val callType: CallTypes,
    val localAddress: String,

    // Nuevos campos para funcionalidad mejorada
    val endReason: CallEndReason = CallEndReason.UNKNOWN,
    val qualityInfo: CallQualityInfo? = null,
    val customData: Map<String, String> = emptyMap()
) : Parcelable

/**
 * Información de calidad de llamada
 */
@Parcelize
data class CallQualityInfo(
    val quality: CallQuality,
    val codec: String,
    val bandwidth: Int,
    val packetLoss: Float,
    val jitter: Float,
    val rtt: Float,
    val encryption: EncryptionType
) : Parcelable

/**
 * Tipos de calidad de llamada
 */
@Parcelize
enum class CallQuality : Parcelable {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN
}

/**
 * Tipos de encriptación
 */
@Parcelize
enum class EncryptionType : Parcelable {
    NONE,
    SRTP,
    DTLS,
    UNKNOWN
}

/**
 * Razones de finalización de llamada
 */
@Parcelize
enum class CallEndReason : Parcelable {
    NORMAL_CLEARING,
    USER_BUSY,
    NO_ANSWER,
    CALL_REJECTED,
    NETWORK_ERROR,
    AUTHENTICATION_FAILED,
    TIMEOUT,
    UNKNOWN
}

data class DtmfRequest(
    val digit: Char,
    val duration: Int = 160,
    val useInfo: Boolean = true
)

data class DtmfQueueStatus(
    val queueSize: Int,
    val isProcessing: Boolean,
    val pendingDigits: String
)

enum class AppLifecycleEvent {
    EnterBackground,
    FinishedLaunching,
    EnterForeground,
    WillTerminate,
    ProtectedDataAvailable,
    ProtectedDataWillBecomeUnavailable
}

interface AppLifecycleListener {
    fun onEvent(event: AppLifecycleEvent)
}
//
///**
// * Modelos de datos para llamadas
// *
// * @author Eddys Larez
// */
//
//@Parcelize
//enum class CallState : Parcelable {
//    NONE,
//    INCOMING,
//    OUTGOING,
//    CALLING,
//    RINGING,
//    CONNECTED,
//    HOLDING,
//    ACCEPTING,
//    ENDING,
//    ENDED,
//    DECLINED,
//    ERROR
//}
//
//@Parcelize
//enum class CallDirections : Parcelable {
//    INCOMING,
//    OUTGOING
//}
//
//@Parcelize
//enum class CallTypes : Parcelable {
//    SUCCESS,
//    MISSED,
//    DECLINED,
//    ABORTED
//}
//
//@Parcelize
//enum class RegistrationState : Parcelable {
//    PROGRESS,
//    OK,
//    CLEARED,
//    NONE,
//    IN_PROGRESS,
//    FAILED
//}
//
//@Parcelize
//data class CallData(
//    var callId: String = "",
//    val from: String = "",
//    val to: String = "",
//    val direction: CallDirections = CallDirections.OUTGOING,
//    val startTime: Long = Clock.System.now().toEpochMilliseconds(),
//    var toTag: String? = null,
//    var fromTag: String? = null,
//    var remoteContactUri: String? = null,
//    var remoteContactParams: Map<String, String> = emptyMap(),
//    val remoteDisplayName: String = "",
//    var inviteFromTag: String = "",
//    var inviteToTag: String = "",
//    var remoteSdp: String = "",
//    var localSdp: String = "",
//    var inviteViaBranch: String = "",
//    var via: String = "",
//    var originalInviteMessage: String = "",
//    var originalCallInviteMessage: String = "",
//    var isOnHold: Boolean? = null,
//    var lastCSeqValue: Int = 0,
//    var sipName: String = ""
//) : Parcelable {
//
//    fun storeInviteMessage(message: String) {
//        originalInviteMessage = message
//    }
//
//    fun getRemoteParty(): String {
//        return when (direction) {
//            CallDirections.OUTGOING -> to
//            CallDirections.INCOMING -> from
//        }
//    }
//
//    fun getLocalParty(): String {
//        return when (direction) {
//            CallDirections.OUTGOING -> from
//            CallDirections.INCOMING -> to
//        }
//    }
//
//    override fun toString(): String {
//        return "CallData(id=$callId, $from→$to, dir=$direction, started=$startTime, " +
//                "fromTag=$fromTag, toTag=$toTag)"
//    }
//}
//
//@Parcelize
//data class CallLog(
//    val id: String,
//    val direction: CallDirections,
//    val to: String,
//    val formattedTo: String,
//    val from: String,
//    val formattedFrom: String,
//    val contact: String?,
//    val formattedStartDate: String,
//    val duration: Int, // in seconds
//    val callType: CallTypes,
//    val localAddress: String
//) : Parcelable
//
//data class DtmfRequest(
//    val digit: Char,
//    val duration: Int = 160,
//    val useInfo: Boolean = true
//)
//
//data class DtmfQueueStatus(
//    val queueSize: Int,
//    val isProcessing: Boolean,
//    val pendingDigits: String
//)
//
//enum class AppLifecycleEvent {
//    EnterBackground,
//    FinishedLaunching,
//    EnterForeground,
//    WillTerminate,
//    ProtectedDataAvailable,
//    ProtectedDataWillBecomeUnavailable
//}
//
//interface AppLifecycleListener {
//    fun onEvent(event: AppLifecycleEvent)
//}