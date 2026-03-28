package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.ConnectionEntry
import com.lifo.util.model.ConnectionType
import com.lifo.util.model.RelationshipReflection
import com.lifo.util.repository.ConnectionRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreConnectionRepository(
    private val db: FirebaseFirestore,
) : ConnectionRepository {

    private val entriesCollection = db.collection("connection_entries")
    private val reflectionsCollection = db.collection("relationship_reflections")

    override fun getEntries(ownerId: String): Flow<List<ConnectionEntry>> = callbackFlow {
        val listener = entriesCollection
            .whereEqualTo("ownerId", ownerId)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val entries = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ConnectionEntry(
                            id = doc.id,
                            ownerId = doc.getString("ownerId") ?: "",
                            dayKey = doc.getString("dayKey") ?: "",
                            timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                            type = try { ConnectionType.valueOf(doc.getString("type") ?: "GRATITUDE") } catch (_: Exception) { ConnectionType.GRATITUDE },
                            personName = doc.getString("personName") ?: "",
                            description = doc.getString("description") ?: "",
                            expressed = doc.getBoolean("expressed") ?: false,
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(entries)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun saveEntry(entry: ConnectionEntry) {
        val data = mapOf(
            "ownerId" to entry.ownerId,
            "dayKey" to entry.dayKey,
            "timestampMillis" to entry.timestampMillis,
            "type" to entry.type.name,
            "personName" to entry.personName,
            "description" to entry.description,
            "expressed" to entry.expressed,
        )
        entriesCollection.document(entry.id).set(data).await()
    }

    override suspend fun deleteEntry(id: String, ownerId: String) {
        entriesCollection.document(id).delete().await()
    }

    override fun getReflection(ownerId: String, monthKey: String): Flow<RelationshipReflection?> = callbackFlow {
        val docId = "${ownerId}_$monthKey"
        val listener = reflectionsCollection.document(docId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) { trySend(null); return@addSnapshotListener }
            try {
                @Suppress("UNCHECKED_CAST")
                val reflection = RelationshipReflection(
                    id = docId,
                    ownerId = snapshot.getString("ownerId") ?: "",
                    monthKey = snapshot.getString("monthKey") ?: "",
                    timestampMillis = snapshot.getLong("timestampMillis") ?: 0L,
                    nurturingRelationships = (snapshot.get("nurturingRelationships") as? List<String>) ?: emptyList(),
                    drainingRelationships = (snapshot.get("drainingRelationships") as? List<String>) ?: emptyList(),
                    intention = snapshot.getString("intention") ?: "",
                )
                trySend(reflection)
            } catch (_: Exception) { trySend(null) }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun saveReflection(reflection: RelationshipReflection) {
        val docId = "${reflection.ownerId}_${reflection.monthKey}"
        val data = mapOf(
            "ownerId" to reflection.ownerId,
            "monthKey" to reflection.monthKey,
            "timestampMillis" to reflection.timestampMillis,
            "nurturingRelationships" to reflection.nurturingRelationships,
            "drainingRelationships" to reflection.drainingRelationships,
            "intention" to reflection.intention,
        )
        reflectionsCollection.document(docId).set(data).await()
    }
}
