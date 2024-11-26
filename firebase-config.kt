package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.FileInputStream

class FirebaseInitializer {
    companion object {
        fun initialize() {
            try {
                val serviceAccount = FileInputStream("src/main/resources/firebase-service-account.json")
                
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    // Optionally add database URL if using Realtime Database
                    // .setDatabaseUrl("https://your-project-id.firebaseio.com")
                    .build()

                // Initialize the app only if it hasn't been initialized before
                if (FirebaseApp.getInstancees().isEmpty()) {
                    FirebaseApp.initializeApp(options)
                }
            } catch (e: Exception) {
                println("Error initializing Firebase: ${e.message}")
            }
        }
    }
}