package com.lifo.mongo.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firestore Module - 2025 Stack
 *
 * NO -ktx imports needed with Firebase BOM 34.0.0+
 * All Kotlin extensions are now in the base modules
 */
@Module
@InstallIn(SingletonComponent::class)
object FirestoreModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return Firebase.storage
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        // Usa il database secondario "calmify-native" in Native Mode
        // invece del database (default) che è in Datastore Mode
        val firestore = FirebaseFirestore.getInstance(
            com.google.firebase.FirebaseApp.getInstance(),
            "calmify-native"
        )

        // Configurazione ottimizzata per offline-first
        val settings = firestoreSettings {
            // Cache persistente con limite di 100MB
            setLocalCacheSettings(
                persistentCacheSettings {
                    setSizeBytes(100 * 1024 * 1024) // 100MB
                }
            )

            // Memory cache settings
            setLocalCacheSettings(
                memoryCacheSettings {
                    // Garbage collection automatica
                }
            )
        }

        firestore.firestoreSettings = settings

        return firestore
    }
}
