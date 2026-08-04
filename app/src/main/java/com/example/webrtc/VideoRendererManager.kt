package com.example.webrtc

import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class VideoRendererManager {
    val rootEglBase: EglBase = EglBase.create()

    fun initSurfaceViewRenderer(renderer: SurfaceViewRenderer) {
        try {
            renderer.init(rootEglBase.eglBaseContext, null)
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            renderer.setEnableHardwareScaler(true)
            renderer.setMirror(false)
        } catch (e: Exception) {
            // Might already be initialized
        }
    }

    fun attachVideoTrack(videoTrack: VideoTrack, renderer: SurfaceViewRenderer) {
        videoTrack.addSink(renderer)
    }

    fun releaseSurfaceViewRenderer(renderer: SurfaceViewRenderer) {
        try {
            renderer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
