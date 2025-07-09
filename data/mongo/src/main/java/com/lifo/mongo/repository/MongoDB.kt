package com.lifo.mongo.repository

import android.annotation.SuppressLint
import android.util.Log
import com.lifo.util.Constants.APP_ID
import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import com.lifo.util.toInstant
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mongodb.kbson.ObjectId
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object MongoDB : MongoRepository {
    private const val TAG = "MongoDB"
    private val app = App.create(APP_ID)
    private val user = app.currentUser
    private lateinit var realm: Realm

    // Mutex per sincronizzare le write operations
    private val writeMutex = Mutex()

    init {
        configureTheRealm()
    }

    override fun configureTheRealm() {
        if (user != null) {
            try {
                val config = SyncConfiguration.Builder(user, setOf(Diary::class))
                    .initialSubscriptions { sub ->
                        add(
                            query = sub.query<Diary>(query = "ownerId == $0", user.id),
                            name = "User's Diaries"
                        )
                    }
                    .log(LogLevel.WARN) // Riduci log level per performance
                    .build()
                realm = Realm.open(config)
                Log.d(TAG, "Realm configured successfully for user: ${user.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring Realm", e)
                throw e
            }
        } else {
            Log.w(TAG, "No authenticated user found")
        }
    }

    @SuppressLint("NewApi")
    override fun getAllDiaries(): Flow<Diaries> {
        return if (user != null) {
            try {
                realm.query<Diary>(query = "ownerId == $0", user.id)
                    .sort(property = "date", sortOrder = Sort.DESCENDING)
                    .asFlow()
                    .map { result ->
                        RequestState.Success(
                            data = result.list.groupBy {
                                it.date.toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            }
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all diaries", e)
                flow { emit(RequestState.Error(e)) }
            }
        } else {
            flow { emit(RequestState.Error(UserNotAuthenticatedException())) }
        }
    }

    @SuppressLint("NewApi")
    override fun getFilteredDiaries(zonedDateTime: ZonedDateTime): Flow<Diaries> {
        return if (user != null) {
            try {
                val startOfDay = RealmInstant.from(
                    LocalDateTime.of(
                        zonedDateTime.toLocalDate(),
                        LocalTime.MIDNIGHT
                    ).toEpochSecond(zonedDateTime.offset), 0
                )

                val endOfDay = RealmInstant.from(
                    LocalDateTime.of(
                        zonedDateTime.toLocalDate().plusDays(1),
                        LocalTime.MIDNIGHT
                    ).toEpochSecond(zonedDateTime.offset), 0
                )

                realm.query<Diary>(
                    "ownerId == $0 AND date < $1 AND date > $2",
                    user.id,
                    endOfDay,
                    startOfDay
                ).asFlow().map { result ->
                    RequestState.Success(
                        data = result.list.groupBy {
                            it.date.toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting filtered diaries", e)
                flow { emit(RequestState.Error(e)) }
            }
        } else {
            flow { emit(RequestState.Error(UserNotAuthenticatedException())) }
        }
    }

    override fun getSelectedDiary(diaryId: ObjectId): Flow<RequestState<Diary>> {
        return if (user != null) {
            try {
                realm.query<Diary>(query = "_id == $0", diaryId).asFlow().map { result ->
                    val diary = result.list.firstOrNull()
                    if (diary != null) {
                        RequestState.Success(data = diary)
                    } else {
                        RequestState.Error(Exception("Diary not found"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting selected diary", e)
                flow { emit(RequestState.Error(e)) }
            }
        } else {
            flow { emit(RequestState.Error(UserNotAuthenticatedException())) }
        }
    }

    override suspend fun insertDiary(diary: Diary): RequestState<Diary> {
        return if (user != null) {
            writeMutex.withLock {
                try {
                    Log.d(TAG, "Inserting diary: ${diary.title}")

                    // Verifica che non ci siano transazioni pendenti
                    if (realm.isClosed()) {
                        Log.e(TAG, "Realm is closed, reconfiguring...")
                        configureTheRealm()
                    }

                    val result = realm.write {
                        val addedDiary = copyToRealm(diary.apply { ownerId = user.id })
                        RequestState.Success(data = addedDiary)
                    }

                    Log.d(TAG, "Diary inserted successfully")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Error inserting diary", e)
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserNotAuthenticatedException())
        }
    }

    override suspend fun updateDiary(diary: Diary): RequestState<Diary> {
        return if (user != null) {
            writeMutex.withLock {
                try {
                    Log.d(TAG, "Updating diary: ${diary._id}")

                    val result = realm.write {
                        val queriedDiary = query<Diary>(query = "_id == $0", diary._id).first().find()
                        if (queriedDiary != null) {
                            queriedDiary.title = diary.title
                            queriedDiary.description = diary.description
                            queriedDiary.mood = diary.mood
                            queriedDiary.images = diary.images
                            queriedDiary.date = diary.date
                            RequestState.Success(data = queriedDiary)
                        } else {
                            RequestState.Error(error = Exception("Queried Diary does not exist."))
                        }
                    }

                    Log.d(TAG, "Diary updated successfully")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating diary", e)
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserNotAuthenticatedException())
        }
    }

    override suspend fun deleteDiary(id: ObjectId): RequestState<Boolean> {
        return if (user != null) {
            writeMutex.withLock {
                try {
                    Log.d(TAG, "Deleting diary: $id")

                    val result = realm.write {
                        val diary = query<Diary>(query = "_id == $0 AND ownerId == $1", id, user.id)
                            .first()
                            .find()
                        if (diary != null) {
                            delete(diary)
                            RequestState.Success(data = true)
                        } else {
                            RequestState.Error(Exception("Diary does not exist."))
                        }
                    }

                    Log.d(TAG, "Diary deleted successfully")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting diary", e)
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserNotAuthenticatedException())
        }
    }

    override suspend fun deleteAllDiaries(): RequestState<Boolean> {
        return if (user != null) {
            writeMutex.withLock {
                try {
                    Log.d(TAG, "Deleting all diaries for user: ${user.id}")

                    val result = realm.write {
                        val diaries = this.query<Diary>("ownerId == $0", user.id).find()
                        Log.d(TAG, "Found ${diaries.size} diaries to delete")
                        delete(diaries)
                        RequestState.Success(data = true)
                    }

                    Log.d(TAG, "All diaries deleted successfully")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting all diaries", e)
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserNotAuthenticatedException())
        }
    }

    // Aggiungi metodo per chiudere Realm propriamente
    fun close() {
        if (::realm.isInitialized && !realm.isClosed()) {
            Log.d(TAG, "Closing Realm instance")
            realm.close()
        }
    }
}

private class UserNotAuthenticatedException : Exception("User is not Logged in.")