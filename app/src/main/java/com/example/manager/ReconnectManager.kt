package com.example.manager

import android.util.Log
import com.example.model.WebRtcConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReconnectManager(
    private val connectionStateManager: ConnectionStateManager,
    private val onReconnectAttempt: suspend () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var reconnectJob: Job? = null
    private var attemptCount = 0

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val INITIAL_DELAY_MS = 2000L
    }

    fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            connectionStateManager.setState(WebRtcConnectionState.RECONNECTING)
            while (attemptCount < MAX_ATTEMPTS && !connectionStateManager.isConnected()) {
                attemptCount++
                val delayTime = INITIAL_DELAY_MS * (1 shl (attemptCount - 1))
                Log.d("ReconnectManager", "Reconnect attempt #$attemptCount in ${delayTime}ms")
                delay(delayTime)

                try {
                    onReconnectAttempt()
                } catch (e: Exception) {
                    Log.e("ReconnectManager", "Reconnect attempt failed: ${e.message}")
                }
            }

            if (!connectionStateManager.isConnected()) {
                Log.e("ReconnectManager", "Max reconnect attempts reached. Setting FAILED state.")
                connectionStateManager.setState(WebRtcConnectionState.FAILED)
            }
        }
    }

    fun reset() {
        reconnectJob?.cancel()
        attemptCount = 0
    }
}
