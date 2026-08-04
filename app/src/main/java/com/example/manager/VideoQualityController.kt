package com.example.manager

import android.util.Log
import org.webrtc.RtpSender

class VideoQualityController {
    companion object {
        private const val TAG = "VideoQualityController"
        private const val MAX_BITRATE_BPS = 2_000_000 // 2 Mbps
        private const val MIN_BITRATE_BPS = 300_000   // 300 Kbps
    }

    fun adaptVideoQuality(videoSender: RtpSender?, targetFps: Int) {
        if (videoSender == null) return
        try {
            val parameters = videoSender.parameters
            if (parameters.encodings.isNotEmpty()) {
                val encoding = parameters.encodings[0]
                if (targetFps < 15) {
                    encoding.maxBitrateBps = MIN_BITRATE_BPS
                    Log.d(TAG, "Low network condition detected: Lowered bitrate to ${MIN_BITRATE_BPS / 1000} kbps")
                } else {
                    encoding.maxBitrateBps = MAX_BITRATE_BPS
                    Log.d(TAG, "Network restored: Increased bitrate to ${MAX_BITRATE_BPS / 1000} kbps")
                }
                videoSender.parameters = parameters
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adapting video quality: ${e.message}")
        }
    }
}
