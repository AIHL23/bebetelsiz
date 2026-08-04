package com.example.audio

import android.content.Context
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import org.webrtc.MediaConstraints

class AudioCaptureManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioCaptureManager"
    }

    fun isAcousticEchoCancelerSupported(): Boolean = AcousticEchoCanceler.isAvailable()
    fun isNoiseSuppressorSupported(): Boolean = NoiseSuppressor.isAvailable()
    fun isAutomaticGainControlSupported(): Boolean = AutomaticGainControl.isAvailable()

    fun createAudioConstraints(): MediaConstraints {
        val mediaConstraints = MediaConstraints()

        val echoCancelerAvailable = isAcousticEchoCancelerSupported()
        val noiseSuppressorAvailable = isNoiseSuppressorSupported()
        val agcAvailable = isAutomaticGainControlSupported()

        Log.d(TAG, "Audio Features -> EchoCanceler: $echoCancelerAvailable, NoiseSuppressor: $noiseSuppressorAvailable, AGC: $agcAvailable")

        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googEchoCancellation", echoCancelerAvailable.toString())
        )
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googAutoGainControl", agcAvailable.toString())
        )
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googNoiseSuppression", noiseSuppressorAvailable.toString())
        )
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googHighpassFilter", "true")
        )
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googAudioMirroring", "false")
        )

        return mediaConstraints
    }
}
