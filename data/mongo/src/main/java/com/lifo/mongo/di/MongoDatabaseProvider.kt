package com.lifo.mongo.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lifo.mongo.database.AppDatabase
import com.lifo.util.Constants.DATABASE_NAME
import com.lifo.util.Constants.IMAGES_DATABASE
import java.io.File

object MongoDatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            // Controlla se esiste il vecchio database
            val oldDbFile = context.getDatabasePath(IMAGES_DATABASE)
            val newDbFile = context.getDatabasePath(DATABASE_NAME)

            // Se esiste il vecchio database e non il nuovo, rinomina
            if (oldDbFile.exists() && !newDbFile.exists()) {
                renameDatabase(context, IMAGES_DATABASE, DATABASE_NAME)
            }

            val instance = Room.databaseBuilder(
                context = context.applicationContext,
                klass = AppDatabase::class.java,
                name = DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

            INSTANCE = instance
            instance
        }
    }

    private fun renameDatabase(context: Context, oldName: String, newName: String) {
        val oldFile = context.getDatabasePath(oldName)
        val newFile = context.getDatabasePath(newName)

        // Rinomina il file principale
        oldFile.renameTo(newFile)

        // Rinomina anche i file -shm e -wal se esistono
        File("${oldFile.absolutePath}-shm").let { if (it.exists()) it.renameTo(File("${newFile.absolutePath}-shm")) }
        File("${oldFile.absolutePath}-wal").let { if (it.exists()) it.renameTo(File("${newFile.absolutePath}-wal")) }
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Se hai già una migrazione 1->2, inseriscila qui
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create chat_sessions table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    lastMessageAt INTEGER NOT NULL,
                    aiModel TEXT NOT NULL,
                    messageCount INTEGER NOT NULL,
                    ownerId TEXT NOT NULL
                )
            """)

            // Create chat_messages table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT PRIMARY KEY NOT NULL,
                    sessionId TEXT NOT NULL,
                    content TEXT NOT NULL,
                    isUser INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    error TEXT,
                    FOREIGN KEY(sessionId) REFERENCES chat_sessions(id) ON DELETE CASCADE
                )
            """)

            // Create index on sessionId
            database.execSQL("CREATE INDEX index_chat_messages_sessionId ON chat_messages(sessionId)")
        }
    }
}