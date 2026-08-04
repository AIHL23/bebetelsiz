package com.example.webrtc

import org.webrtc.PeerConnection

object IceServerProvider {
    /**
     * Returns Google's STUN servers and allows additional TURN servers to be injected.
     */
    fun getIceServers(turnServerUrl: String? = null, turnUsername: String? = null, turnCredential: String? = null): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()

        // Google STUN Servers
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer())

        // Optional TURN server support
        if (!turnServerUrl.isNullOrEmpty()) {
            val builder = PeerConnection.IceServer.builder(turnServerUrl)
            if (!turnUsername.isNullOrEmpty() && !turnCredential.isNullOrEmpty()) {
                builder.setUsername(turnUsername).setPassword(turnCredential)
            }
            iceServers.add(builder.createIceServer())
        }

        return iceServers
    }
}
