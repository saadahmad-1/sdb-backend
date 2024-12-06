package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.defaultheaders.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.FileInputStream
import java.io.InputStream

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

// Firebase Initialization Extension Function
fun Application.initializeFirebase() {
    val logger = LoggerFactory.getLogger("Application")
    try {
        // Multiple strategies for finding Firebase credentials
        val serviceAccountStream = findFirebaseCredentials()
        
        if (serviceAccountStream == null) {
            logger.error("Firebase service account file not found!")
            throw IllegalStateException("Firebase credentials are missing")
        }

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
            .build()

        // Prevent multiple initializations
        if (FirebaseApp.getInstances().isEmpty()) {
            FirebaseApp.initializeApp(options)
            logger.info("Firebase initialized successfully")
        }
    } catch (e: Exception) {
        logger.error("Critical error initializing Firebase: ${e.message}", e)
        // Optionally rethrow to prevent application startup
        // throw RuntimeException("Firebase initialization failed", e)
    }
}

// Helper function to find Firebase credentials
private fun Application.findFirebaseCredentials(): InputStream? {
    val logger = LoggerFactory.getLogger("FirebaseCredentials")
    
    // Search strategies for credentials
    val credentialSources = listOf(
        // 1. Environment variable path
        System.getenv("FIREBASE_CREDENTIALS_PATH")?.let { path -> 
            logger.info("Trying credentials from environment variable")
            try { FileInputStream(path) } catch (e: Exception) { null }
        },
        
        // 2. Classpath resource (works in development)
        this.javaClass.classLoader.getResourceAsStream("firebase-service-account.json")
            ?.also { logger.info("Using credentials from classpath") },
        
        // 3. Specific file paths (for different environments)
        listOf(
            "firebase-service-account.json",
            "src/main/resources/firebase-service-account.json",
            "/app/firebase-service-account.json"
        ).firstNotNullOfOrNull { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    logger.info("Using credentials from file: $path")
                    FileInputStream(file)
                } else null
            } catch (e: Exception) { null }
        }
    )

    return credentialSources.firstOrNull()
}

fun Application.module() {
    // Firebase initialization (early in the pipeline)
    initializeFirebase()

    // Content Negotiation with more robust JSON configuration
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Default Headers with more informative markers
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
        header("X-Firebase-Initialized", "true")
        header("X-App-Version", "1.0.0")
    }

    // Enhanced Call Logging
    install(CallLogging) {
        level = Level.INFO
        // Optionally filter or format logs
        // filter { call -> call.request.path() != "/health" }
    }
    
    // Route configurations
    configureAuthRouting()
    configureOtpRouting()
    configureDeliveryStatusRouting()
    configureParcelRouting()
    configureDeliveryBoxRouting()
    configureUserRouting()

    // Optional: Add a health check endpoint
    routing {
        get("/health") {
            call.respond(mapOf(
                "status" to "healthy", 
                "firebase" to (FirebaseApp.getInstances().isNotEmpty())
            ))
        }
    }
}