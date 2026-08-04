package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.manager.WebRtcManager
import com.example.model.WebRtcConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BabyMonitorViewModel(application: Application) : AndroidViewModel(application) {
    val webRtcManager = WebRtcManager(application.applicationContext)

    val connectionState: StateFlow<WebRtcConnectionState> = webRtcManager.connectionState

    private val _pairingCode = MutableStateFlow("582914")
    val pairingCode: StateFlow<String> = _pairingCode

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted

    fun generateNewPairingCode() {
        val newCode = (100000..999999).random().toString()
        _pairingCode.value = newCode
    }

    fun startBabyDevice(code: String, babyName: String) {
        _pairingCode.value = code
        viewModelScope.launch {
            webRtcManager.startBabyDeviceStream(code, babyName)
        }
    }

    fun startParentViewer(code: String) {
        _pairingCode.value = code
        viewModelScope.launch {
            webRtcManager.startParentViewerStream(code)
        }
    }

    fun toggleMic() {
        _isMicMuted.value = !_isMicMuted.value
    }

    override fun onCleared() {
        super.onCleared()
        webRtcManager.close()
    }
}
