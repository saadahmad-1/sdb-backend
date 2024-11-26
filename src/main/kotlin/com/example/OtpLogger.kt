package com.example

import org.slf4j.LoggerFactory

object OtpLogger {
    private val logger = LoggerFactory.getLogger(OtpLogger::class.java)
    
    fun logOtpGeneration(
        otpId: String,
        phoneNumber: String,
        serviceProviderId: String,
        status: String,
        error: String? = null
    ) {
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
        
        if (status == "SUCCESS") {
            logger.info(logMessage)
        } else {
            logger.error(logMessage)
        }
    }
}