package com.lifo.server.firebase

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import java.io.FileInputStream

object FirebaseAdmin {
    private val logger = LoggerFactory.getLogger(FirebaseAdmin::class.java)

    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            logger.info("Firebase already initialized")
            return
        }

        val options = when {
            // Cloud Run: uses Application Default Credentials
            System.getenv("GOOGLE_CLOUD_PROJECT") != null -> {
                logger.info("Initializing Firebase with Application Default Credentials")
                FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(System.getenv("GOOGLE_CLOUD_PROJECT"))
                    .build()
            }
            // Local dev: use service account key file
            System.getenv("FIREBASE_SERVICE_ACCOUNT") != null -> {
                val keyPath = System.getenv("FIREBASE_SERVICE_ACCOUNT")
                logger.info("Initializing Firebase with service account: $keyPath")
                FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(FileInputStream(keyPath)))
                    .build()
            }
            else -> {
                logger.warn("No Firebase credentials found — auth middleware will reject all tokens")
                logger.warn("Set FIREBASE_SERVICE_ACCOUNT=/path/to/key.json for local dev")
                return
            }
        }

        FirebaseApp.initializeApp(options)
        logger.info("Firebase Admin SDK initialized successfully")
    }
}
