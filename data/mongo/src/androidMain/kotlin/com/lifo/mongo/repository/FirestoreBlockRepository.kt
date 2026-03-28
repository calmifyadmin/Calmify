package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.Block
import com.lifo.util.model.BlockResolution
import com.lifo.util.model.BlockType
import com.lifo.util.repository.BlockRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreBlockRepository(
    firestore: FirebaseFirestore,
) : BlockRepository {

    private val collection = firestore.collection("blocks")

    override suspend fun saveBlock(block: Block): Result<Unit> = runCatching {
        val data = hashMapOf(
            "ownerId" to block.ownerId,
            "timestampMillis" to block.timestampMillis,
            "description" to block.description,
            "type" to block.type.name,
            "resolution" to block.resolution?.name,
            "resolutionNote" to block.resolutionNote,
            "isResolved" to block.isResolved,
            "resolvedAtMillis" to block.resolvedAtMillis,
        )
        collection.document(block.id).set(data).await()
    }

    override fun getActiveBlocks(userId: String): Flow<List<Block>> = callbackFlow {
        val reg = collection
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("isResolved", false)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toBlock() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    override fun getResolvedBlocks(userId: String, limit: Int): Flow<List<Block>> = callbackFlow {
        val reg = collection
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("isResolved", true)
            .orderBy("resolvedAtMillis", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toBlock() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    override suspend fun resolveBlock(blockId: String, resolution: String, note: String): Result<Unit> = runCatching {
        collection.document(blockId).update(
            mapOf(
                "isResolved" to true,
                "resolution" to resolution,
                "resolutionNote" to note,
                "resolvedAtMillis" to System.currentTimeMillis(),
            )
        ).await()
    }

    override suspend fun deleteBlock(blockId: String): Result<Unit> = runCatching {
        collection.document(blockId).delete().await()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toBlock(): Block? = try {
        Block(
            id = id,
            ownerId = getString("ownerId") ?: "",
            timestampMillis = getLong("timestampMillis") ?: 0L,
            description = getString("description") ?: "",
            type = try { BlockType.valueOf(getString("type") ?: "UNKNOWN") } catch (_: Exception) { BlockType.UNKNOWN },
            resolution = getString("resolution")?.let { try { BlockResolution.valueOf(it) } catch (_: Exception) { null } },
            resolutionNote = getString("resolutionNote") ?: "",
            isResolved = getBoolean("isResolved") ?: false,
            resolvedAtMillis = getLong("resolvedAtMillis"),
        )
    } catch (_: Exception) { null }
}
