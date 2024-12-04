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
    val otpId: String = "",
    val phoneNumber: String = "",
    val serviceProviderId: String = "",
    val otp: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 600000, // 10 minutes expiry
    val status: String = "PENDING"
)

@Serializable
data class OtpVerificationRequest(
    val phoneNumber: String,
    val otp: String
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
fun Application.configureOtpRouting() {
    // Initialize Firestore when the application starts
    FirestoreClient.initialize()

    routing {
        route("api/v1") {
            // Generate OTP
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
            
                    val firestore = FirestoreClient.getFirestore()
            
                    // Check if an OTP already exists for this phone number
                    val existingOtpQuery = firestore.collection("otps")
                        .whereEqualTo("phoneNumber", request.phoneNumber)
                        .get()
                        .get() // Wait for the operation to complete
            
                    val otp = (100000..999999).random().toString()
                    val otpId = UUID.randomUUID().toString()
            
                    if (existingOtpQuery.documents.isNotEmpty()) {
                        // Update existing OTP
                        val existingDoc = existingOtpQuery.documents.first()
                        firestore.collection("otps")
                            .document(existingDoc.id)
                            .update(
                                mapOf(
                                    "otp" to otp,
                                    "createdAt" to System.currentTimeMillis(),
                                    "expiresAt" to System.currentTimeMillis() + 600000, // 10 minutes expiry
                                    "status" to "PENDING"
                                )
                            )
                            .get() // Wait for the operation to complete
            
                        OtpLogger.logOtpGeneration(
                            otpId = existingDoc.id,
                            phoneNumber = request.phoneNumber,
                            serviceProviderId = request.serviceProviderId,
                            status = "UPDATED"
                        )
            
                        call.respond(
                            HttpStatusCode.OK,
                            OtpResponse(
                                otpId = existingDoc.id,
                                status = "SUCCESS",
                                message = "OTP updated successfully"
                            )
                        )
                    } else {
                        // Create a new OTP entry
                        val firestoreOtpData = FirestoreOtpData(
                            otpId = otpId,
                            phoneNumber = request.phoneNumber,
                            serviceProviderId = request.serviceProviderId,
                            otp = otp
                        )
                        firestore.collection("otps")
                            .document(otpId)
                            .set(firestoreOtpData)
                            .get() // Wait for the operation to complete
            
                        OtpLogger.logOtpGeneration(
                            otpId = otpId,
                            phoneNumber = request.phoneNumber,
                            serviceProviderId = request.serviceProviderId,
                            status = "CREATED"
                        )
            
                        call.respond(
                            HttpStatusCode.OK,
                            OtpResponse(
                                otpId = otpId,
                                status = "SUCCESS",
                                message = "OTP generated successfully"
                            )
                        )
                    }
                } catch (e: Exception) {
                    val errorId = UUID.randomUUID().toString()
            
                    OtpLogger.logOtpGeneration(
                        otpId = errorId,
                        phoneNumber = "UNKNOWN",
                        serviceProviderId = "UNKNOWN",
                        status = "FAILED",
                        error = e.message
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
            
            // Fetch all OTP logs
            get("otp-logs") {
                try {
                    val firestore = FirestoreClient.getFirestore()
                    val logs = firestore.collection("otp_logs")
                        .get()
                        .get()
                        .documents
                        .mapNotNull { it.toObject(OtpLogEntry::class.java) }

                    call.respond(HttpStatusCode.OK, logs)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("status" to "FAILED", "message" to "Error fetching logs: ${e.message}")
                    )
                }
            }

            // Verify OTP
            post("verify-otp") {
                try {
                    val request = call.receiveNullable<OtpVerificationRequest>() ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            OtpResponse(
                                otpId = "UNKNOWN",
                                status = "FAILED",
                                message = "Invalid request body"
                            )
                        )
                        return@post
                    }
            
                    val firestore = FirestoreClient.getFirestore()
                    val querySnapshot = firestore.collection("otps")
                        .whereEqualTo("phoneNumber", request.phoneNumber)
                        .whereEqualTo("otp", request.otp)
                        .get()
                        .get() // Wait for the operation to complete
            
                    if (querySnapshot.documents.isEmpty()) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            OtpResponse(
                                otpId = "UNKNOWN",
                                status = "FAILED",
                                message = "OTP not found or invalid"
                            )
                        )
                        return@post
                    }
            
                    val otpDoc = querySnapshot.documents.first()
                    val otpData = otpDoc.toObject(FirestoreOtpData::class.java)
            
                    if (otpData != null && otpData.expiresAt > System.currentTimeMillis()) {
                        firestore.collection("otps")
                            .document(otpDoc.id)
                            .update("status", "VERIFIED")
                            .get()
            
                        OtpLogger.logOtpVerification(
                            otpId = otpData.otpId,
                            phoneNumber = otpData.phoneNumber,
                            serviceProviderId = otpData.serviceProviderId,
                            status = "SUCCESS"
                        )
            
                        call.respond(
                            HttpStatusCode.OK,
                            OtpResponse(
                                otpId = otpData.otpId,
                                status = "SUCCESS",
                                message = "OTP verified successfully"
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            OtpResponse(
                                otpId = "UNKNOWN",
                                status = "FAILED",
                                message = "OTP expired or invalid"
                            )
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        OtpResponse(
                            otpId = "UNKNOWN",
                            status = "FAILED",
                            message = "An error occurred: ${e.message}"
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