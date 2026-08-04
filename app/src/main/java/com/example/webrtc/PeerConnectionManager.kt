package com.example.webrtc

import android.content.Context
import android.util.Log
import com.example.model.IceCandidateModel
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class PeerConnectionManager(
    private val context: Context,
    private val rootEglBase: EglBase,
    private val onLocalIceCandidate: (IceCandidateModel) -> Unit,
    private val onConnectionStateChanged: (PeerConnection.PeerConnectionState) -> Unit,
    private val onRemoteVideoTrackReceived: (VideoTrack) -> Unit
) {
    private var webRtcClient: WebRtcClient? = null

    fun initialize(): WebRtcClient {
        val client = WebRtcClient(
            context = context,
            rootEglBase = rootEglBase,
            onIceCandidateCreated = onLocalIceCandidate,
            onConnectionStateChanged = onConnectionStateChanged,
            onRemoteTrackReceived = onRemoteVideoTrackReceived
        )
        client.initPeerConnection()
        webRtcClient = client
        return client
    }

    fun startCapturing(): VideoTrack? {
        return webRtcClient?.startLocalVideoAndAudio()
    }

    fun initiateOffer(onOfferCreated: (String) -> Unit) {
        webRtcClient?.createOffer(onOfferCreated)
    }

    fun handleOfferAndCreateAnswer(offerSdp: String, onAnswerCreated: (String) -> Unit) {
        webRtcClient?.setRemoteDescription(offerSdp, SessionDescription.Type.OFFER) {
            webRtcClient?.createAnswer(onAnswerCreated)
        }
    }

    fun handleAnswer(answerSdp: String, onComplete: (() -> Unit)? = null) {
        webRtcClient?.setRemoteDescription(answerSdp, SessionDescription.Type.ANSWER, onComplete)
    }

    fun addRemoteIceCandidate(candidate: IceCandidateModel) {
        webRtcClient?.addIceCandidate(candidate)
    }

    fun close() {
        webRtcClient?.close()
        webRtcClient = null
    }
}
