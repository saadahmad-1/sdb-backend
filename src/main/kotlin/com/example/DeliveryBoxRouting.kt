package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import java.util.UUID

// Firestore DeliveryBox Data Class for Storage
@Serializable
data class FirestoreDeliveryBoxData(
    val boxId: String,
    val type: BoxType,
    val address: String,
    val isSecured: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "CREATED"
)

// Enum for DeliveryBox Types
enum class BoxType {
    SMALL, MEDIUM, LARGE
}

fun Application.configureDeliveryBoxRouting() {
    routing {
        route("api/v1") {
            post("create-delivery-box") {
                try {
                    val request = call.receiveNullable<DeliveryBoxRequest>() ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            DeliveryBoxResponse(
                                boxId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                message = "Invalid request body"
                            )
                        )
                        return@post
                    }

                    // Validate request
                    if (!isValidAddress(request.address)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            DeliveryBoxResponse(
                                boxId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                message = "Invalid address"
                            )
                        )
                        return@post
                    }

                    val boxId = UUID.randomUUID().toString()

                    // Store DeliveryBox in Firestore
                    val firestoreDeliveryBoxData = FirestoreDeliveryBoxData(
                        boxId = boxId,
                        type = request.type,
                        address = request.address,
                        isSecured = request.isSecured
                    )

                    val firestore = FirestoreClient.getFirestore()
                    firestore.collection("delivery_boxes")
                        .document(boxId)
                        .set(firestoreDeliveryBoxData)
                        .get() // Wait for the operation to complete

                    DeliveryBoxLogger.logDeliveryBoxCreation(
                        boxId = boxId,
                        type = request.type,
                        address = request.address,
                        isSecured = request.isSecured,
                        status = "SUCCESS"
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        DeliveryBoxResponse(
                            boxId = boxId,
                            status = "SUCCESS",
                            message = "DeliveryBox created successfully"
                        )
                    )
                } catch (e: Exception) {
                    val errorId = UUID.randomUUID().toString()

                    DeliveryBoxLogger.logDeliveryBoxCreation(
                        boxId = errorId,
                        type = null,
                        address = "UNKNOWN",
                        isSecured = false,
                        status = "FAILED",
                        error = e.message
                    )

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        DeliveryBoxResponse(
                            boxId = errorId,
                            status = "FAILED",
                            message = "Failed to create delivery box: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}

// Request and Response Data Classes
@Serializable
data class DeliveryBoxRequest(
    val type: BoxType,
    val address: String,
    val isSecured: Boolean
)

@Serializable
data class DeliveryBoxResponse(
    val boxId: String,
    val status: String,
    val message: String
)

// Utility function for address validation
private fun isValidAddress(address: String): Boolean {
    return address.isNotBlank() && address.length <= 200
}

object DeliveryBoxLogger {
    fun logDeliveryBoxCreation(
        boxId: String,
        type: BoxType?,
        address: String,
        isSecured: Boolean,
        status: String,
        error: String? = null
    ) {
        println("DeliveryBox Creation Log: ID=$boxId, Type=$type, Address=$address, Secured=$isSecured, Status=$status, Error=$error")
    }
}
