package com.example.repository

import com.example.firebase.FirebaseSignalingManager
import com.example.model.CallSession
import com.example.model.WebRtcConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CallRepository(
    private val signalingManager: FirebaseSignalingManager = FirebaseSignalingManager()
) {
    private val _currentSession = MutableStateFlow<CallSession?>(null)
    val currentSession: StateFlow<CallSession?> = _currentSession

    fun startBabySession(pairingCode: String, babyName: String, deviceId: String) {
        _currentSession.value = CallSession(
            pairingCode = pairingCode,
            isBabyDevice = true,
            connectionState = WebRtcConnectionState.CONNECTING
        )
        signalingManager.registerBabyDevice(pairingCode, babyName, deviceId)
    }

    fun startParentSession(pairingCode: String) {
        _currentSession.value = CallSession(
            pairingCode = pairingCode,
            isBabyDevice = false,
            connectionState = WebRtcConnectionState.CONNECTING
        )
        signalingManager.startListeningRoom(pairingCode, isBabyDevice = false)
    }

    fun updateConnectionState(state: WebRtcConnectionState) {
        _currentSession.value = _currentSession.value?.copy(connectionState = state)
    }

    fun endSession() {
        val code = _currentSession.value?.pairingCode
        if (code != null) {
            signalingManager.updateStatus(code, "CLOSED")
        }
        signalingManager.stopListening()
        _currentSession.value = null
    }
}
