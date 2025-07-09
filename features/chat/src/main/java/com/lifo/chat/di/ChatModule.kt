package com.lifo.chat.di

import android.content.Context
import androidx.room.Room
import com.lifo.chat.data.local.ChatDao
import com.lifo.chat.data.local.ChatDatabase
import com.lifo.chat.data.repository.ChatRepository
import com.lifo.chat.data.repository.ChatRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    @Provides
    @Singleton
    fun provideChatDatabase(
        @ApplicationContext context: Context
    ): ChatDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = ChatDatabase::class.java,
            name = ChatDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: ChatDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        chatDao: ChatDao
    ): ChatRepository {
        return ChatRepositoryImpl(chatDao)
    }
}