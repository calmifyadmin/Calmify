package com.lifo.util.repository

import com.lifo.util.model.ConnectionEntry
import com.lifo.util.model.RelationshipReflection
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    fun getEntries(ownerId: String): Flow<List<ConnectionEntry>>
    suspend fun saveEntry(entry: ConnectionEntry)
    suspend fun deleteEntry(id: String, ownerId: String)
    fun getReflection(ownerId: String, monthKey: String): Flow<RelationshipReflection?>
    suspend fun saveReflection(reflection: RelationshipReflection)
}
