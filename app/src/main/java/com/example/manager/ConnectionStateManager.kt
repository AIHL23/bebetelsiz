package com.example.manager

import com.example.model.WebRtcConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectionStateManager {
    private val _connectionState = MutableStateFlow(WebRtcConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WebRtcConnectionState> = _connectionState

    fun setState(newState: WebRtcConnectionState) {
        _connectionState.value = newState
    }

    fun isConnected(): Boolean = _connectionState.value == WebRtcConnectionState.CONNECTED
}
