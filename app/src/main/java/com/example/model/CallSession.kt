package com.example.model

enum class WebRtcConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    CONNECTION_LOST,
    FAILED,
    CLOSED
}

data class SdpModel(
    val type: String = "",
    val sdp: String = ""
)

data class IceCandidateModel(
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = ""
)

data class RoomModel(
    val roomId: String = "",
    val pairingCode: String = "",
    val babyDeviceId: String = "",
    val babyName: String = "",
    val status: String = "WAITING",
    val offer: SdpModel? = null,
    val answer: SdpModel? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class CallSession(
    val pairingCode: String,
    val isBabyDevice: Boolean,
    val connectionState: WebRtcConnectionState = WebRtcConnectionState.DISCONNECTED,
    val isMicMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val currentFps: Int = 30,
    val errorMessage: String? = null
)
