package com.lifo.server.firebase

import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.cloud.FirestoreClient as GoogleFirestoreClient
import org.slf4j.LoggerFactory

object FirestoreClient {
    private val logger = LoggerFactory.getLogger(FirestoreClient::class.java)

    /** Named database — the project's default is Datastore Mode, real data lives in "calmify-native" */
    private const val DATABASE_ID = "calmify-native"

    val db: Firestore by lazy {
        GoogleFirestoreClient.getFirestore(FirebaseApp.getInstance(), DATABASE_ID).also {
            logger.info("Firestore client initialized (database=$DATABASE_ID)")
        }
    }
}
