package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import java.util.UUID

// Firestore Parcel Data Class for Storage
@Serializable
data class FirestoreParcelData(
    val parcelId: String,
    val size: ParcelSize,
    val destination: String,
    val isFragile: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "CREATED"
)

// Enum for Parcel Sizes
enum class ParcelSize {
    SMALL, MEDIUM, LARGE
}

fun Application.configureParcelRouting() {
    routing {
        route("api/v1") {
            post("create-parcel") {
                try {
                    val request = call.receiveNullable<ParcelRequest>() ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ParcelResponse(
                                parcelId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                message = "Invalid request body"
                            )
                        )
                        return@post
                    }

                    // Validate request
                    if (!isValidDestination(request.destination)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ParcelResponse(
                                parcelId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                message = "Invalid destination"
                            )
                        )
                        return@post
                    }
                    
                    val parcelId = UUID.randomUUID().toString()
                    
                    // Store Parcel in Firestore
                    val firestoreParcelData = FirestoreParcelData(
                        parcelId = parcelId,
                        size = request.size,
                        destination = request.destination,
                        isFragile = request.isFragile
                    )
                    
                    val firestore = FirestoreClient.getFirestore()
                    firestore.collection("parcels")
                        .document(parcelId)
                        .set(firestoreParcelData)
                        .get() // Wait for the operation to complete
                    
                    ParcelLogger.logParcelCreation(
                        parcelId = parcelId,
                        size = request.size,
                        destination = request.destination,
                        isFragile = request.isFragile,
                        status = "SUCCESS"
                    )
                    
                    call.respond(
                        HttpStatusCode.OK,
                        ParcelResponse(
                            parcelId = parcelId,
                            status = "SUCCESS",
                            message = "Parcel created successfully"
                        )
                    )
                } catch (e: Exception) {
                    val errorId = UUID.randomUUID().toString()
                    
                    ParcelLogger.logParcelCreation(
                        parcelId = errorId,
                        size = null,
                        destination = "UNKNOWN",
                        isFragile = false,
                        status = "FAILED",
                        error = e.message
                    )
                    
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ParcelResponse(
                            parcelId = errorId,
                            status = "FAILED",
                            message = "Failed to create parcel: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}

// Request and Response Data Classes
@Serializable
data class ParcelRequest(
    val size: ParcelSize,
    val destination: String,
    val isFragile: Boolean
)

@Serializable
data class ParcelResponse(
    val parcelId: String,
    val status: String,
    val message: String
)

// Utility function for destination validation
private fun isValidDestination(destination: String): Boolean {
    return destination.isNotBlank() && destination.length <= 100
}

// Placeholder Logger Object (you'd implement actual logging)
object ParcelLogger {
    fun logParcelCreation(
        parcelId: String,
        size: ParcelSize?,
        destination: String,
        isFragile: Boolean,
        status: String,
        error: String? = null
    ) {
        // Implement actual logging logic
        println("Parcel Creation Log: ID=$parcelId, Size=$size, Destination=$destination, Fragile=$isFragile, Status=$status, Error=$error")
    }
}