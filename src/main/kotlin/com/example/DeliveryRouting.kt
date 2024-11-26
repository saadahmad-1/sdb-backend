package com.example

import com.google.cloud.firestore.Firestore
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DeliveryStatusRequest(
    val parcelId: String,
    val status: DeliveryStage,
    val location: String,
    val serviceProviderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val additionalDetails: String? = null
)

@Serializable
enum class DeliveryStage {
    DISPATCHED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED_DELIVERY,
    RETURNED
}

@Serializable
data class DeliveryStatusResponse(
    val parcelId: String,
    val status: String,
    val message: String
)

@Serializable
data class FirestoreDeliveryStatusData(
    val parcelId: String = "",
    val status: DeliveryStage = DeliveryStage.DISPATCHED,
    val location: String = "",
    val serviceProviderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val additionalDetails: String? = null
)

fun Application.configureDeliveryStatusRouting() {
    routing {
        route("api/v1/delivery") {
            post("status") {
                try {
                    val request = call.receiveNullable<DeliveryStatusRequest>() ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            DeliveryStatusResponse(
                                parcelId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                message = "Invalid request body"
                            )
                        )
                        return@post
                    }

                    if (request.parcelId.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            DeliveryStatusResponse(
                                parcelId = request.parcelId,
                                status = "FAILED",
                                message = "Invalid parcel ID"
                            )
                        )
                        return@post
                    }
                    
                    val firestore = FirestoreClient.getFirestore()
                    
                    // Store Delivery Status in Firestore
                    val firestoreDeliveryStatusData = FirestoreDeliveryStatusData(
                        parcelId = request.parcelId,
                        status = request.status,
                        location = request.location,
                        serviceProviderId = request.serviceProviderId,
                        timestamp = request.timestamp,
                        additionalDetails = request.additionalDetails
                    )
                    
                    firestore.collection("delivery_statuses")
                        .document(request.parcelId)
                        .set(firestoreDeliveryStatusData)
                        .get() // Wait for the operation to complete
                    
                    call.respond(
                        HttpStatusCode.OK,
                        DeliveryStatusResponse(
                            parcelId = request.parcelId,
                            status = "SUCCESS",
                            message = "Delivery status updated successfully"
                        )
                    )
                } catch (e: Exception) {
                    val errorId = UUID.randomUUID().toString()
                    
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        DeliveryStatusResponse(
                            parcelId = errorId,
                            status = "FAILED",
                            message = "Failed to update delivery status: ${e.message}"
                        )
                    )
                }
            }

            get("status/{parcelId}") {
                val parcelId = call.parameters["parcelId"] ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        DeliveryStatusResponse(
                            parcelId = UUID.randomUUID().toString(),
                            status = "FAILED",
                            message = "Parcel ID is required"
                        )
                    )
                    return@get
                }
            
                try {
                    val firestore = FirestoreClient.getFirestore()
                    val docRef = firestore.collection("delivery_statuses").document(parcelId)
                    val snapshot = docRef.get().get()
            
                    if (snapshot.exists()) {
                        val deliveryStatus = snapshot.toObject(FirestoreDeliveryStatusData::class.java)
                        
                        // Check if the delivery status data is not null
                        if (deliveryStatus != null) {
                            call.respond(
                                HttpStatusCode.OK,
                                DeliveryStatusResponse(
                                    parcelId = parcelId,
                                    status = deliveryStatus.status.name, // Get the status from the Firestore data
                                    message = "Delivery status retrieved successfully"
                                )
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                DeliveryStatusResponse(
                                    parcelId = parcelId,
                                    status = "FAILED",
                                    message = "Parcel status not found"
                                )
                            )
                        }
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            DeliveryStatusResponse(
                                parcelId = parcelId,
                                status = "FAILED",
                                message = "Parcel status not found"
                            )
                        )
                    }
                } catch (e: Exception) {
                    val errorId = UUID.randomUUID().toString()
            
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        DeliveryStatusResponse(
                            parcelId = errorId,
                            status = "FAILED",
                            message = "Failed to retrieve delivery status: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}