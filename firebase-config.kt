package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

class FirebaseInitializer {
    companion object {
        private val logger = LoggerFactory.getLogger(FirebaseInitializer::class.java)

        fun initialize() {
            try {
                // Priority order for finding Firebase credentials
                val credentialPaths = listOf(
                    // 1. Environment variable
                    System.getenv("FIREBASE_CREDENTIALS_PATH"),
                    // 2. Development path
                    "src/main/resources/firebase-service-account.json",
                    // 3. Production path in container
                    "/app/firebase-service-account.json"
                )

                val serviceAccountFile = credentialPaths.firstOrNull { path ->
                    path != null && File(path).exists()
                }

                if (serviceAccountFile == null) {
                    logger.error("No Firebase credentials file found. Tried paths: $credentialPaths")
                    throw IllegalStateException("Firebase credentials file not found")
                }

                logger.info("Using Firebase credentials from: $serviceAccountFile")

                val serviceAccount = FileInputStream(serviceAccountFile)
                
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    // Optionally add database URL if using Realtime Database
                    // .setDatabaseUrl(System.getenv("FIREBASE_DATABASE_URL"))
                    .build()

                // Initialize the app only if it hasn't been initialized before
                if (FirebaseApp.getInstances().isEmpty()) {
                    FirebaseApp.initializeApp(options)
                    logger.info("Firebase initialized successfully")
                }
            } catch (e: Exception) {
                logger.error("Critical error initializing Firebase", e)
                // Rethrow to prevent application startup if Firebase is critical
                throw RuntimeException("Firebase initialization failed", e)
            }
        }
    }
}