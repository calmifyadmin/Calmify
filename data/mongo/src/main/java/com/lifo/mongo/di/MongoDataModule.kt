package com.lifo.mongo.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.mongo.database.AppDatabase
import com.lifo.mongo.database.dao.ChatMessageDao
import com.lifo.mongo.database.dao.ChatSessionDao
import com.lifo.mongo.repository.ChatRepositoryImpl
import com.lifo.mongo.repository.FirestoreDiaryRepository
import com.lifo.mongo.repository.FirestoreInsightRepository
import com.lifo.mongo.repository.FirestoreProfileRepository
import com.lifo.mongo.repository.FirestoreWellbeingRepository
import com.lifo.mongo.repository.UnifiedContentRepositoryImpl
import com.lifo.util.repository.ChatRepository
import com.lifo.util.repository.InsightRepository
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.ProfileRepository
import com.lifo.util.repository.UnifiedContentRepository
import com.lifo.util.repository.WellbeingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * MongoDataModule - Provides repository dependencies
 *
 * Despite the name, now provides Firestore-based repositories
 * Name kept for backward compatibility with existing DI setup
 */
@Module
@InstallIn(SingletonComponent::class)
object MongoDataModule {

    @Provides
    @Singleton
    fun provideChatSessionDao(database: AppDatabase): ChatSessionDao {
        return database.chatSessionDao()
    }

    @Provides
    @Singleton
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    @Singleton
    fun provideMongoRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): MongoRepository {
        return FirestoreDiaryRepository(firestore, auth)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        chatSessionDao: ChatSessionDao,
        chatMessageDao: ChatMessageDao,
        auth: FirebaseAuth,
        diaryRepository: MongoRepository
    ): ChatRepository {
        return ChatRepositoryImpl(chatSessionDao, chatMessageDao, auth, diaryRepository)
    }

    @Provides
    @Singleton
    fun provideUnifiedContentRepository(
        chatSessionDao: ChatSessionDao,
        diaryRepository: MongoRepository
    ): UnifiedContentRepository {
        return UnifiedContentRepositoryImpl(chatSessionDao, diaryRepository)
    }

    @Provides
    @Singleton
    fun provideWellbeingRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): WellbeingRepository {
        return FirestoreWellbeingRepository(firestore, auth)
    }

    @Provides
    @Singleton
    fun provideInsightRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): InsightRepository {
        return FirestoreInsightRepository(firestore, auth)
    }

    @Provides
    @Singleton
    fun provideProfileRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): ProfileRepository {
        return FirestoreProfileRepository(firestore, auth)
    }

}