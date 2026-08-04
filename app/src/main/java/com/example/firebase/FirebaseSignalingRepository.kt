package com.example.firebase

import android.util.Log
import com.example.model.IceCandidateModel
import com.example.model.RoomModel
import com.example.model.SdpModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseSignalingRepository {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val ROOMS_COLLECTION = "rooms"
    private val CANDIDATES_COLLECTION = "iceCandidates"

    suspend fun createOrUpdateRoom(room: RoomModel): Boolean {
        return try {
            firestore.collection(ROOMS_COLLECTION)
                .document(room.pairingCode)
                .set(room)
                .await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseSignaling", "Error creating room: ${e.message}")
            false
        }
    }

    suspend fun sendOffer(pairingCode: String, offerSdp: SdpModel): Boolean {
        return try {
            firestore.collection(ROOMS_COLLECTION)
                .document(pairingCode)
                .update(
                    mapOf(
                        "offer" to offerSdp,
                        "status" to "OFFER_SENT"
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseSignaling", "Error sending offer: ${e.message}")
            false
        }
    }

    suspend fun sendAnswer(pairingCode: String, answerSdp: SdpModel): Boolean {
        return try {
            firestore.collection(ROOMS_COLLECTION)
                .document(pairingCode)
                .update(
                    mapOf(
                        "answer" to answerSdp,
                        "status" to "CONNECTED"
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseSignaling", "Error sending answer: ${e.message}")
            false
        }
    }

    suspend fun sendIceCandidate(pairingCode: String, candidate: IceCandidateModel, isBaby: Boolean): Boolean {
        return try {
            val senderType = if (isBaby) "baby" else "parent"
            firestore.collection(ROOMS_COLLECTION)
                .document(pairingCode)
                .collection(CANDIDATES_COLLECTION)
                .add(
                    mapOf(
                        "sender" to senderType,
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "sdp" to candidate.sdp,
                        "timestamp" to System.currentTimeMillis()
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseSignaling", "Error sending ICE candidate: ${e.message}")
            false
        }
    }

    fun observeRoom(pairingCode: String): Flow<RoomModel?> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(ROOMS_COLLECTION)
            .document(pairingCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseSignaling", "Listen failed: ${error.message}")
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val room = snapshot.toObject(RoomModel::class.java)
                    trySend(room)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeRemoteIceCandidates(pairingCode: String, isBabyDevice: Boolean): Flow<IceCandidateModel> = callbackFlow {
        val targetSender = if (isBabyDevice) "parent" else "baby"
        val listener: ListenerRegistration = firestore.collection(ROOMS_COLLECTION)
            .document(pairingCode)
            .collection(CANDIDATES_COLLECTION)
            .whereEqualTo("sender", targetSender)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                for (docChange in snapshots.documentChanges) {
                    if (docChange.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = docChange.document
                        val candidate = IceCandidateModel(
                            sdpMid = doc.getString("sdpMid") ?: "",
                            sdpMLineIndex = doc.getLong("sdpMLineIndex")?.toInt() ?: 0,
                            sdp = doc.getString("sdp") ?: ""
                        )
                        trySend(candidate)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateConnectionState(pairingCode: String, state: String): Boolean {
        return try {
            firestore.collection(ROOMS_COLLECTION)
                .document(pairingCode)
                .update("status", state)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
