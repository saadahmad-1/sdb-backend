package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class OtpRequest(
    val phoneNumber: String,
    val serviceProviderId: String
)

@Serializable
data class OtpResponse(
    val otpId: String,
    val status: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun Application.configureRouting() {
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

private fun isValidPhoneNumber(phoneNumber: String): Boolean {
    return phoneNumber.matches(Regex("^\\+[1-9]\\d{1,14}$"))
}
