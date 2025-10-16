package com.lifo.mongo.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.mongo.database.AppDatabase
import com.lifo.mongo.database.dao.ChatMessageDao
import com.lifo.mongo.database.dao.ChatSessionDao
import com.lifo.mongo.repository.*
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
}