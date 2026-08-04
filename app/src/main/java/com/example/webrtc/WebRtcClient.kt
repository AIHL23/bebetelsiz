package com.example.webrtc

import android.content.Context
import android.util.Log
import com.example.audio.AudioCaptureManager
import com.example.camera.CameraCaptureManager
import com.example.model.IceCandidateModel
import com.example.model.SdpModel
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcClient(
    private val context: Context,
    private val rootEglBase: EglBase,
    private val onIceCandidateCreated: (IceCandidateModel) -> Unit,
    private val onConnectionStateChanged: (PeerConnection.PeerConnectionState) -> Unit,
    private val onRemoteTrackReceived: (VideoTrack) -> Unit
) {
    companion object {
        private const val TAG = "WebRtcClient"
        private const val VIDEO_TRACK_ID = "baby_video_track"
        private const val AUDIO_TRACK_ID = "baby_audio_track"
        private const val STREAM_ID = "baby_stream"
    }

    val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
        private set

    private var audioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
        private set

    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun initPeerConnection(): PeerConnection? {
        val iceServers = IceServerProvider.getIceServers()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.FAILED) {
                    onConnectionStateChanged(PeerConnection.PeerConnectionState.FAILED)
                } else if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                    onConnectionStateChanged(PeerConnection.PeerConnectionState.CONNECTED)
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val model = IceCandidateModel(
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdp = candidate.sdp
                    )
                    onIceCandidateCreated(model)
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                if (stream != null && stream.videoTracks.isNotEmpty()) {
                    onRemoteTrackReceived(stream.videoTracks[0])
                }
            }

            override fun onRemoveStream(p0: MediaStream?) {}

            override fun onDataChannel(p0: DataChannel?) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (receiver?.track() is VideoTrack) {
                    onRemoteTrackReceived(receiver.track() as VideoTrack)
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState != null) {
                    Log.d(TAG, "PeerConnectionState changed: $newState")
                    onConnectionStateChanged(newState)
                }
            }
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)
        return peerConnection
    }

    private val pendingIceCandidates = ArrayList<IceCandidate>()
    private var isRemoteDescriptionSet = false

    fun startLocalVideoAndAudio(): VideoTrack? {
        try {
            val cameraManager = CameraCaptureManager(context)
            val capturer = cameraManager.createVideoCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
                videoSource = factory.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                capturer.startCapture(CameraCaptureManager.VIDEO_WIDTH, CameraCaptureManager.VIDEO_HEIGHT, CameraCaptureManager.VIDEO_FPS)

                localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
                localVideoTrack?.setEnabled(true)
            }

            val audioManager = AudioCaptureManager(context)
            audioSource = factory.createAudioSource(audioManager.createAudioConstraints())
            localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
            localAudioTrack?.setEnabled(true)

            peerConnection?.let { pc ->
                if (localVideoTrack != null) pc.addTrack(localVideoTrack, listOf(STREAM_ID))
                if (localAudioTrack != null) pc.addTrack(localAudioTrack, listOf(STREAM_ID))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting local video/audio capture: ${e.message}")
        }

        return localVideoTrack
    }

    fun createOffer(onSdpCreated: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            onSdpCreated(desc.description)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create offer failed: $error")
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun createAnswer(onSdpCreated: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            onSdpCreated(desc.description)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create answer failed: $error")
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun setRemoteDescription(sdp: String, type: SessionDescription.Type, onComplete: (() -> Unit)? = null) {
        val sessionDescription = SessionDescription(type, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Set Remote Description success ($type)")
                isRemoteDescriptionSet = true
                drainPendingIceCandidates()
                onComplete?.invoke()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set Remote Description failure: $error")
            }
        }, sessionDescription)
    }

    fun addIceCandidate(candidateModel: IceCandidateModel) {
        if (candidateModel.sdp.isBlank()) {
            Log.w(TAG, "Skipping ICE candidate with blank SDP")
            return
        }
        val candidate = IceCandidate(candidateModel.sdpMid, candidateModel.sdpMLineIndex, candidateModel.sdp)
        if (isRemoteDescriptionSet && peerConnection != null) {
            try {
                peerConnection?.addIceCandidate(candidate)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding ice candidate: ${e.message}")
            }
        } else {
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
            }
        }
    }

    private fun drainPendingIceCandidates() {
        synchronized(pendingIceCandidates) {
            for (candidate in pendingIceCandidates) {
                try {
                    peerConnection?.addIceCandidate(candidate)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding pending ice candidate: ${e.message}")
                }
            }
            pendingIceCandidates.clear()
        }
    }

    fun close() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            peerConnection?.close()
            peerConnection = null
            audioSource?.dispose()
            audioSource = null
            videoSource?.dispose()
            videoSource = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebRtcClient: ${e.message}")
        }
    }
}
