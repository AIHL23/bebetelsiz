package com.example.firebase

import android.util.Log
import com.example.model.IceCandidateModel
import com.example.model.RoomModel
import com.example.model.SdpModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FirebaseSignalingManager(
    private val repository: FirebaseSignalingRepository = FirebaseSignalingRepository()
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _roomState = MutableStateFlow<RoomModel?>(null)
    val roomState: StateFlow<RoomModel?> = _roomState

    private var roomJob: Job? = null
    private var iceJob: Job? = null
    var iceCandidateListener: ((IceCandidateModel) -> Unit)? = null

    fun startListeningRoom(pairingCode: String, isBabyDevice: Boolean) {
        stopListening()

        roomJob = scope.launch {
            repository.observeRoom(pairingCode).collect { room ->
                _roomState.value = room
            }
        }

        iceJob = scope.launch {
            repository.observeRemoteIceCandidates(pairingCode, isBabyDevice).collect { candidate ->
                iceCandidateListener?.invoke(candidate)
            }
        }
    }

    fun registerBabyDevice(pairingCode: String, babyName: String, babyDeviceId: String) {
        scope.launch {
            val room = RoomModel(
                roomId = pairingCode,
                pairingCode = pairingCode,
                babyDeviceId = babyDeviceId,
                babyName = babyName,
                status = "WAITING"
            )
            repository.createOrUpdateRoom(room)
            startListeningRoom(pairingCode, isBabyDevice = true)
        }
    }

    fun sendOffer(pairingCode: String, offerSdp: String) {
        scope.launch {
            repository.sendOffer(pairingCode, SdpModel(type = "OFFER", sdp = offerSdp))
        }
    }

    fun sendAnswer(pairingCode: String, answerSdp: String) {
        scope.launch {
            repository.sendAnswer(pairingCode, SdpModel(type = "ANSWER", sdp = answerSdp))
        }
    }

    fun sendIceCandidate(pairingCode: String, candidate: IceCandidateModel, isBaby: Boolean) {
        scope.launch {
            repository.sendIceCandidate(pairingCode, candidate, isBaby)
        }
    }

    fun updateStatus(pairingCode: String, status: String) {
        scope.launch {
            repository.updateConnectionState(pairingCode, status)
        }
    }

    fun stopListening() {
        roomJob?.cancel()
        roomJob = null
        iceJob?.cancel()
        iceJob = null
    }
}
