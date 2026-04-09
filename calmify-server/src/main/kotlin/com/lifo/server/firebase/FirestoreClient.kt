package com.lifo.server.firebase

import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient as GoogleFirestoreClient
import org.slf4j.LoggerFactory

object FirestoreClient {
    private val logger = LoggerFactory.getLogger(FirestoreClient::class.java)

    val db: Firestore? by lazy {
        try {
            GoogleFirestoreClient.getFirestore().also {
                logger.info("Firestore client initialized")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize Firestore client", e)
            null
        }
    }
}
