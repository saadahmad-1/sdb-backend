package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient

// Data class for User
@Serializable
data class User(
    val name: String,
    val email: String,
    val role: String
)

// Response Data Class for User List
@Serializable
data class UserListResponse(
    val status: String,
    val message: String,
    val users: List<User>
)

fun Application.configureUserRouting() {
    routing {
        route("api/v1") {
            get("get-users") {
                try {
                    // Get Firestore instance
                    val firestore: Firestore = FirestoreClient.getFirestore()
                    
                    // Fetch user data from Firestore
                    val usersSnapshot = firestore.collection("users").get().get()

                    // Rest of the code remains similar to previous implementation
                    val users = usersSnapshot.documents.mapNotNull { document ->
                        try {
                            val name = document.getString("name") ?: ""
                            val email = document.getString("email") ?: ""
                            val role = document.getString("role") ?: ""
                            if (name.isNotBlank() && email.isNotBlank() && role.isNotBlank()) {
                                User(name = name, email = email, role = role)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            println("Error parsing document: ${document.id}, Error: ${e.message}")
                            null
                        }
                    }

                    // Response handling remains the same
                    if (users.isEmpty()) {
                        call.respond(
                            HttpStatusCode.NoContent,
                            UserListResponse(
                                status = "SUCCESS",
                                message = "No users found",
                                users = users
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.OK,
                            UserListResponse(
                                status = "SUCCESS",
                                message = "Users retrieved successfully",
                                users = users
                            )
                        )
                    }
                } catch (e: Exception) {
                    println("Firestore retrieval error: ${e.message}")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        UserListResponse(
                            status = "FAILED",
                            message = "Failed to retrieve users: ${e.message}",
                            users = emptyList()
                        )
                    )
                }
            }
        }
    }
}