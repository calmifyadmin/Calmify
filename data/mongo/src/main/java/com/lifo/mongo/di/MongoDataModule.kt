package com.lifo.mongo.di

import com.google.firebase.auth.FirebaseAuth
import com.lifo.mongo.database.AppDatabase
import com.lifo.mongo.database.dao.ChatMessageDao
import com.lifo.mongo.database.dao.ChatSessionDao
import com.lifo.mongo.repository.ChatRepository
import com.lifo.mongo.repository.ChatRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun provideChatRepository(
        chatSessionDao: ChatSessionDao,
        chatMessageDao: ChatMessageDao,
        auth: FirebaseAuth
    ): ChatRepository {
        return ChatRepositoryImpl(chatSessionDao, chatMessageDao, auth)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
}