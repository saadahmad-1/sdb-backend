package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.defaultheaders.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.FileInputStream

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

// Firebase Initialization Extension Function
fun Application.initializeFirebase() {
    val logger = LoggerFactory.getLogger("Application")
    try {
        val serviceAccountStream = this.javaClass.classLoader.getResourceAsStream("firebase-service-account.json")
        
        if (serviceAccountStream == null) {
            logger.error("Firebase service account file not found!")
            return
        }

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
            .build()

        FirebaseApp.initializeApp(options)
        logger.info("Firebase initialized successfully")
    } catch (e: Exception) {
        logger.error("Error initializing Firebase: ${e.message}", e)
    }
}

fun Application.module() {
    // Firebase initialization
    initializeFirebase()

    install(ContentNegotiation) {
        json()
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
        header("X-Firebase-Initialized", "true")
    }

    install(CallLogging) {
        level = Level.INFO
    }
    
    configureRouting()
    configureDeliveryStatusRouting()
}