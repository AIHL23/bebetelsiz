package com.example.manager

import android.content.Context
import android.util.Log
import com.example.firebase.FirebaseSignalingManager
import com.example.model.IceCandidateModel
import com.example.model.WebRtcConnectionState
import com.example.webrtc.PeerConnectionManager
import com.example.webrtc.VideoRendererManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class WebRtcManager(
    private val context: Context,
    val videoRendererManager: VideoRendererManager = VideoRendererManager(),
    private val signalingManager: FirebaseSignalingManager = FirebaseSignalingManager()
) {
    companion object {
        private const val TAG = "WebRtcManager"
    }

    val connectionStateManager = ConnectionStateManager()
    val connectionState: StateFlow<WebRtcConnectionState> = connectionStateManager.connectionState

    private var peerConnectionManager: PeerConnectionManager? = null
    private var reconnectManager: ReconnectManager? = null
    private var activePairingCode: String? = null
    private var isBabyDeviceMode: Boolean = false

    private var remoteVideoTrack: VideoTrack? = null
    private var localVideoTrack: VideoTrack? = null

    private var roomCollectorJob: Job? = null
    private var handledOfferSdp: String? = null
    private var handledAnswerSdp: String? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        reconnectManager = ReconnectManager(connectionStateManager) {
            Log.d(TAG, "Reconnection triggered by ReconnectManager...")
            activePairingCode?.let { code ->
                if (isBabyDeviceMode) {
                    startBabyDeviceStream(code, "BabyDevice")
                } else {
                    startParentViewerStream(code)
                }
            }
        }
    }

    fun startBabyDeviceStream(pairingCode: String, babyName: String) {
        activePairingCode = pairingCode
        isBabyDeviceMode = true
        handledOfferSdp = null
        handledAnswerSdp = null

        connectionStateManager.setState(WebRtcConnectionState.CONNECTING)

        peerConnectionManager?.close()
        peerConnectionManager = null

        signalingManager.registerBabyDevice(pairingCode, babyName, "DEV-${pairingCode.takeLast(4)}")

        peerConnectionManager = PeerConnectionManager(
            context = context,
            rootEglBase = videoRendererManager.rootEglBase,
            onLocalIceCandidate = { candidate ->
                signalingManager.sendIceCandidate(pairingCode, candidate, isBaby = true)
            },
            onConnectionStateChanged = { state ->
                handlePeerConnectionStateChange(state)
            },
            onRemoteVideoTrackReceived = { track ->
                remoteVideoTrack = track
            }
        ).apply {
            initialize()
            localVideoTrack = startCapturing()
        }

        signalingManager.iceCandidateListener = { candidate ->
            peerConnectionManager?.addRemoteIceCandidate(candidate)
        }

        roomCollectorJob?.cancel()
        roomCollectorJob = scope.launch {
            signalingManager.roomState.collect { room ->
                if (room != null && room.offer != null && room.status == "OFFER_SENT") {
                    val offerSdp = room.offer.sdp
                    if (handledOfferSdp != offerSdp) {
                        handledOfferSdp = offerSdp
                        Log.d(TAG, "Baby device received OFFER from parent")
                        peerConnectionManager?.handleOfferAndCreateAnswer(offerSdp) { answerSdp ->
                            signalingManager.sendAnswer(pairingCode, answerSdp)
                        }
                    }
                }
            }
        }
    }

    fun startParentViewerStream(pairingCode: String) {
        activePairingCode = pairingCode
        isBabyDeviceMode = false
        handledOfferSdp = null
        handledAnswerSdp = null

        connectionStateManager.setState(WebRtcConnectionState.CONNECTING)

        peerConnectionManager?.close()
        peerConnectionManager = null

        signalingManager.startListeningRoom(pairingCode, isBabyDevice = false)

        peerConnectionManager = PeerConnectionManager(
            context = context,
            rootEglBase = videoRendererManager.rootEglBase,
            onLocalIceCandidate = { candidate ->
                signalingManager.sendIceCandidate(pairingCode, candidate, isBaby = false)
            },
            onConnectionStateChanged = { state ->
                handlePeerConnectionStateChange(state)
            },
            onRemoteVideoTrackReceived = { track ->
                remoteVideoTrack = track
                connectionStateManager.setState(WebRtcConnectionState.CONNECTED)
            }
        ).apply {
            initialize()
        }

        signalingManager.iceCandidateListener = { candidate ->
            peerConnectionManager?.addRemoteIceCandidate(candidate)
        }

        // Parent initiates offer
        peerConnectionManager?.initiateOffer { offerSdp ->
            signalingManager.sendOffer(pairingCode, offerSdp)
        }

        roomCollectorJob?.cancel()
        roomCollectorJob = scope.launch {
            signalingManager.roomState.collect { room ->
                if (room != null && room.answer != null && room.status == "CONNECTED") {
                    val answerSdp = room.answer.sdp
                    if (handledAnswerSdp != answerSdp) {
                        handledAnswerSdp = answerSdp
                        Log.d(TAG, "Parent received ANSWER from baby device")
                        peerConnectionManager?.handleAnswer(answerSdp)
                    }
                }
            }
        }
    }

    private fun handlePeerConnectionStateChange(state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                connectionStateManager.setState(WebRtcConnectionState.CONNECTED)
                reconnectManager?.reset()
            }
            PeerConnection.PeerConnectionState.DISCONNECTED,
            PeerConnection.PeerConnectionState.FAILED -> {
                connectionStateManager.setState(WebRtcConnectionState.CONNECTION_LOST)
                reconnectManager?.scheduleReconnect()
            }
            PeerConnection.PeerConnectionState.CLOSED -> {
                connectionStateManager.setState(WebRtcConnectionState.CLOSED)
            }
            else -> {}
        }
    }

    fun bindVideoTrackToRenderer(renderer: SurfaceViewRenderer, isLocal: Boolean = false) {
        try {
            videoRendererManager.initSurfaceViewRenderer(renderer)
            val track = if (isLocal) localVideoTrack else remoteVideoTrack
            track?.let {
                videoRendererManager.attachVideoTrack(it, renderer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding video track to renderer: ${e.message}")
        }
    }

    fun unbindVideoTrackFromRenderer(renderer: SurfaceViewRenderer, isLocal: Boolean = false) {
        try {
            val track = if (isLocal) localVideoTrack else remoteVideoTrack
            track?.removeSink(renderer)
            videoRendererManager.releaseSurfaceViewRenderer(renderer)
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding video track from renderer: ${e.message}")
        }
    }

    fun close() {
        roomCollectorJob?.cancel()
        roomCollectorJob = null
        reconnectManager?.reset()
        peerConnectionManager?.close()
        peerConnectionManager = null
        signalingManager.stopListening()
        remoteVideoTrack = null
        localVideoTrack = null
        connectionStateManager.setState(WebRtcConnectionState.DISCONNECTED)
    }
}
