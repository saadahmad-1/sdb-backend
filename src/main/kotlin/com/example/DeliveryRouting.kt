package com.example

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
    val currentStatus: DeliveryStage,
    val statusHistory: List<DeliveryStatusLog>,
    val status: String,
    val message: String
)

@Serializable
data class DeliveryStatusLog(
    val stage: DeliveryStage,
    val location: String,
    val timestamp: Long,
    val additionalDetails: String? = null
)

object DeliveryTracker {
    private val deliveryStatusMap = mutableMapOf<String, MutableList<DeliveryStatusLog>>()

    fun updateDeliveryStatus(request: DeliveryStatusRequest): Boolean {
        val statusLog = DeliveryStatusLog(
            stage = request.status,
            location = request.location,
            timestamp = request.timestamp,
            additionalDetails = request.additionalDetails
        )
        
        deliveryStatusMap.getOrPut(request.parcelId) { mutableListOf() }.add(statusLog)
        return true
    }

    fun getDeliveryStatus(parcelId: String): DeliveryStatusResponse? {
        val statusHistory = deliveryStatusMap[parcelId]
        return statusHistory?.let {
            DeliveryStatusResponse(
                parcelId = parcelId,
                currentStatus = it.last().stage,
                statusHistory = it,
                status = "SUCCESS",
                message = "Delivery status retrieved successfully"
            )
        }
    }
}

fun Application.configureDeliveryStatusRouting() {
    routing {
        route("api/v1/delivery") {
            post("status") {
                try {
                    val request = call.receiveNullable<DeliveryStatusRequest>() ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            DeliveryStatusResponse(
                                parcelId = "UNKNOWN",
                                currentStatus = DeliveryStage.DISPATCHED,
                                statusHistory = emptyList(),
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
                                currentStatus = request.status,
                                statusHistory = emptyList(),
                                status = "FAILED",
                                message = "Invalid parcel ID"
                            )
                        )
                        return@post
                    }

                    val updateResult = DeliveryTracker.updateDeliveryStatus(request)
                    
                    if (updateResult) {
                        call.respond(
                            HttpStatusCode.OK,
                            DeliveryStatusResponse(
                                parcelId = request.parcelId,
                                currentStatus = request.status,
                                statusHistory = listOf(
                                    DeliveryStatusLog(
                                        stage = request.status,
                                        location = request.location,
                                        timestamp = request.timestamp,
                                        additionalDetails = request.additionalDetails
                                    )
                                ),
                                status = "SUCCESS",
                                message = "Delivery status updated successfully"
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            DeliveryStatusResponse(
                                parcelId = request.parcelId,
                                currentStatus = request.status,
                                statusHistory = emptyList(),
                                status = "FAILED",
                                message = "Failed to update delivery status"
                            )
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        DeliveryStatusResponse(
                            parcelId = "UNKNOWN",
                            currentStatus = DeliveryStage.DISPATCHED,
                            statusHistory = emptyList(),
                            status = "FAILED",
                            message = "Error updating delivery status: ${e.message}"
                        )
                    )
                }
            }

            get("status/{parcelId}") {
                val parcelId = call.parameters["parcelId"] ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        DeliveryStatusResponse(
                            parcelId = "UNKNOWN",
                            currentStatus = DeliveryStage.DISPATCHED,
                            statusHistory = emptyList(),
                            status = "FAILED",
                            message = "Parcel ID is required"
                        )
                    )
                    return@get
                }

                val deliveryStatus = DeliveryTracker.getDeliveryStatus(parcelId)
                
                if (deliveryStatus != null) {
                    call.respond(HttpStatusCode.OK, deliveryStatus)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        DeliveryStatusResponse(
                            parcelId = parcelId,
                            currentStatus = DeliveryStage.DISPATCHED,
                            statusHistory = emptyList(),
                            status = "FAILED",
                            message = "Parcel status not found"
                        )
                    )
                }
            }
        }
    }
}