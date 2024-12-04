package com.example

import org.slf4j.LoggerFactory
import com.google.cloud.firestore.Firestore
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class OtpLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val otpId: String = "",
    val phoneNumber: String = "",
    val serviceProviderId: String = "",
    val status: String = "",
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val logType: String = "OTP_GENERATION"
)

object OtpLogger {
    private val logger = LoggerFactory.getLogger(OtpLogger::class.java)
    
    fun logOtpGeneration(
        otpId: String,
        phoneNumber: String,
        serviceProviderId: String,
        status: String,
        error: String? = null
    ) {
        // Create log entry
        val logEntry = OtpLogEntry(
            otpId = otpId,
            phoneNumber = phoneNumber,
            serviceProviderId = serviceProviderId,
            status = status,
            error = error
        )
        
        // Log to console/file using SLF4J
        val logMessage = buildString {
            append("OTP Generation - ")
            append("ID: $otpId, ")
            append("Phone: $phoneNumber, ")
            append("ServiceProvider: $serviceProviderId, ")
            append("Status: $status")
            if (error != null) {
                append(", Error: $error")
            }
        }
        
        // Determine log level based on status
        if (status == "SUCCESS") {
            logger.info(logMessage)
        } else {
            logger.error(logMessage)
        }
        
        // Log to Firestore
        try {
            val firestore = FirestoreClient.getFirestore()
            firestore.collection("otp_logs")
                .document(logEntry.id)
                .set(logEntry)
                .get() // Wait for the operation to complete
        } catch (e: Exception) {
            logger.error("Failed to log to Firestore: ${e.message}", e)
        }
    }
    
    // Additional logging methods for OTP verification
    fun logOtpVerification(
        otpId: String,
        phoneNumber: String,
        serviceProviderId: String,
        status: String,
        error: String? = null
    ) {
        // Create log entry for verification
        val logEntry = OtpLogEntry(
            otpId = otpId,
            phoneNumber = phoneNumber,
            serviceProviderId = serviceProviderId,
            status = status,
            error = error,
            logType = "OTP_VERIFICATION"
        )
        
        // Log to console/file using SLF4J
        val logMessage = buildString {
            append("OTP Verification - ")
            append("ID: $otpId, ")
            append("Phone: $phoneNumber, ")
            append("ServiceProvider: $serviceProviderId, ")
            append("Status: $status")
            if (error != null) {
                append(", Error: $error")
            }
        }
        
        // Determine log level based on status
        if (status == "SUCCESS") {
            logger.info(logMessage)
        } else {
            logger.error(logMessage)
        }
        
        // Log to Firestore
        try {
            val firestore = FirestoreClient.getFirestore()
            firestore.collection("otp_logs")
                .document(logEntry.id)
                .set(logEntry)
                .get() // Wait for the operation to complete
        } catch (e: Exception) {
            logger.error("Failed to log to Firestore: ${e.message}", e)
        }
    }
}