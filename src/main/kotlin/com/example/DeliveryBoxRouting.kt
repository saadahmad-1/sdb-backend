package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import java.util.UUID
import com.google.cloud.firestore.Firestore
import io.ktor.serialization.kotlinx.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Updated data classes to include location
@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class FirestoreDeliveryBoxData(
    val boxId: String = "",
    val type: BoxType = BoxType.SMALL,
    val address: String = "",
    val location: Location? = null,
    val isSecured: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "CREATED"
)

enum class BoxType {
    SMALL, MEDIUM, LARGE
}

@Serializable
data class DeliveryBoxRequest(
    val type: BoxType,
    val address: String,
    val location: Location? = null,
    val isSecured: Boolean
)

@Serializable
data class DeliveryBoxResponse(
    val boxId: String,
    val status: String,
    val message: String
)

@Serializable
data class DeliveryBoxListResponse(
    val status: String = "",
    val message: String = "",
    val deliveryBoxes: List<FirestoreDeliveryBoxData>
)

fun Application.configureDeliveryBoxRouting() {
    routing {
        route("api/v1") {
            post("create-delivery-box") {
                try {
                    val request = call.receive<DeliveryBoxRequest>()

                    // Validate address and location
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

                    // Validate location if provided
                    if (request.location != null && !isValidLocation(request.location)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            DeliveryBoxResponse(
                                boxId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                message = "Invalid location coordinates"
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
                        location = request.location,
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
                        location = request.location,
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
                        location = null,
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

            get("get-delivery-boxes") {
                try {
                    val firestore = FirestoreClient.getFirestore()
                    val deliveryBoxesSnapshot = firestore.collection("delivery_boxes")
                        .get()
                        .get() // Wait for the operation to complete

                    val deliveryBoxes = deliveryBoxesSnapshot.documents.mapNotNull { document ->
                        document.toObject(FirestoreDeliveryBoxData::class.java)
                    }

                    call.respond(
                        HttpStatusCode.OK,
                        DeliveryBoxListResponse(
                            status = "SUCCESS",
                            message = "Delivery boxes retrieved successfully",
                            deliveryBoxes = deliveryBoxes
                        )
                    )
                } catch (e: Exception) {
                    val errorId = UUID.randomUUID().toString()

                    DeliveryBoxLogger.logDeliveryBoxRetrieval(
                        status = "FAILED",
                        error = e.message
                    )

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        DeliveryBoxListResponse(
                            status = "FAILED",
                            message = "Failed to retrieve delivery boxes: ${e.message}",
                            deliveryBoxes = emptyList()
                        )
                    )
                }
            }
        }
    }
}

// Utility functions for validation
private fun isValidAddress(address: String): Boolean {
    return address.isNotBlank() && address.length <= 200
}

private fun isValidLocation(location: Location): Boolean {
    return location.latitude in -90.0..90.0 && 
           location.longitude in -180.0..180.0
}

object DeliveryBoxLogger {
    fun logDeliveryBoxCreation(
        boxId: String,
        type: BoxType?,
        address: String,
        location: Location?,
        isSecured: Boolean,
        status: String,
        error: String? = null
    ) {
        println("DeliveryBox Creation Log: " +
            "ID=$boxId, " +
            "Type=$type, " +
            "Address=$address, " +
            "Location=${location?.let { "(${it.latitude}, ${it.longitude})" } ?: "N/A"}, " +
            "Secured=$isSecured, " +
            "Status=$status, " +
            "Error=$error")
    }

    fun logDeliveryBoxRetrieval(
        status: String,
        error: String? = null
    ) {
        println("DeliveryBox Retrieval Log: Status=$status, Error=$error")
    }
}