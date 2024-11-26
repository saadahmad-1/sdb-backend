package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import java.io.FileInputStream
import java.util.UUID

// Firestore OTP Data Class for Storage
@Serializable
data class FirestoreOtpData(
    val otpId: String,
    val phoneNumber: String,
    val serviceProviderId: String,
    val otp: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 600000, // 10 minutes expiry
    val status: String = "PENDING"
)

object FirestoreClient {
    private var firestore: Firestore? = null

    fun initialize(path: String = "src/main/resources/firebase-service-account.json") {
        val credentials = FileInputStream(path)
        val options = FirestoreOptions.getDefaultInstance().toBuilder()
            .setCredentials(GoogleCredentials.fromStream(credentials))
            .build()
        firestore = options.service
    }

    fun getFirestore(): Firestore {
        return firestore ?: throw IllegalStateException("Firestore not initialized")
    }
}

fun Application.configureRouting() {
    // Initialize Firestore when the application starts
    FirestoreClient.initialize()

    routing {
        route("api/v1") {
            post("generate-otp") {
                try {
                    val request = call.receiveNullable<OtpRequest>() ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            OtpResponse(
                                otpId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                message = "Invalid request body"
                            )
                        )
                        return@post
                    }

                    if (!isValidPhoneNumber(request.phoneNumber)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            OtpResponse(
                                otpId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                message = "Invalid phone number format"
                            )
                        )
                        return@post
                    }
                    
                    val otp = (100000..999999).random().toString()
                    val otpId = UUID.randomUUID().toString()
                    
                    // Store OTP in Firestore
                    val firestoreOtpData = FirestoreOtpData(
                        otpId = otpId,
                        phoneNumber = request.phoneNumber,
                        serviceProviderId = request.serviceProviderId,
                        otp = otp
                    )
                    
                    val firestore = FirestoreClient.getFirestore()
                    firestore.collection("otps")
                        .document(otpId)
                        .set(firestoreOtpData)
                        .get() // Wait for the operation to complete
                    
                    OtpLogger.logOtpGeneration(
                        otpId = otpId,
                        phoneNumber = request.phoneNumber,
                        serviceProviderId = request.serviceProviderId,
                        status = "SUCCESS"
                    )
                    
                    ServiceProviderNotifier.notifyOtpGenerated(
                        serviceProviderId = request.serviceProviderId,
                        otpId = otpId
                    )
                    
                    call.respond(
                        HttpStatusCode.OK,
                        OtpResponse(
                            otpId = otpId,
                            status = "SUCCESS",
                            message = "OTP generated successfully"
                        )
                    )
                } catch (e: Exception) {
                    val errorId = UUID.randomUUID().toString()
                    
                    OtpLogger.logOtpGeneration(
                        otpId = errorId,
                        phoneNumber = "UNKNOWN",
                        serviceProviderId = "UNKNOWN",
                        status = "FAILED",
                        error = e.message
                    )
                    
                    ServiceProviderNotifier.notifyOtpGenerationFailed(
                        errorId = errorId,
                        error = e.message ?: "Unknown error"
                    )
                    
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        OtpResponse(
                            otpId = errorId,
                            status = "FAILED",
                            message = "Failed to generate OTP: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}

@Serializable
data class OtpRequest(
    val phoneNumber: String,
    val serviceProviderId: String
)

@Serializable
data class OtpResponse(
    val otpId: String,
    val status: String,
    val message: String
)

private fun isValidPhoneNumber(phoneNumber: String): Boolean {
    return phoneNumber.matches(Regex("^\\+[1-9]\\d{1,14}$"))
}