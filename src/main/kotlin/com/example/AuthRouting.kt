package com.example

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserRecord
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserRegistrationRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String
)

@Serializable
data class UserLoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val userId: String,
    val status: String,
    val message: String,
    val token: String? = null,
    val role: String? = null
)

@Serializable
data class FirestoreUserData(
    val name: String = "",
    val email: String = "",
    val role: String = ""
) {
    // No-argument constructor for Firebase
    constructor() : this("", "", "")
}

fun Application.configureAuthRouting() {
    routing {
        route("api/v1/auth") {
            post("login") {
                val request = call.receive<UserLoginRequest>()
                val email = request.email
                val password = request.password
            
                if (email.isNullOrBlank() || password.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Email and password are required.")
                    return@post
                }
            
                try {
                    // Use Firebase Admin SDK to verify user credentials
                    val auth = FirebaseAuth.getInstance()
                    
                    // Try to get user by email to validate credentials
                    val userRecord = try {
                        auth.getUserByEmail(email)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.Unauthorized, "Authentication failed: Invalid email.")
                        return@post
                    }

                    // Create a custom token for authentication
                    val customToken = auth.createCustomToken(userRecord.uid)
        
                    // Fetch user details from Firestore
                    val firestore = FirestoreClient.getFirestore()
                    val userDoc = firestore.collection("users").document(userRecord.uid).get().get()
        
                    val userData = userDoc.toObject(FirestoreUserData::class.java)
        
                    if (userData != null) {
                        call.respond(AuthResponse(
                            userId = userRecord.uid, 
                            status = "Success", 
                            message = "Login successful", 
                            token = customToken, 
                            role = userData.role
                        ))
                    } else {
                        call.respond(HttpStatusCode.NotFound, "User data not found.")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.Unauthorized, "Authentication failed: ${e.message}")
                }
            }            

            post("register") {
                val request = call.receive<UserRegistrationRequest>()
                val validRoles = listOf("Admin", "Courier", "Customer")

                if (request.role !in validRoles) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid role. Allowed roles: $validRoles")
                    return@post
                }

                try {
                    // Create user in Firebase Authentication
                    val createRequest = UserRecord.CreateRequest()
                        .setEmail(request.email)
                        .setPassword(request.password)
                        .setDisplayName(request.name)

                    val userRecord = FirebaseAuth.getInstance().createUser(createRequest)

                    // Set custom claims (role)
                    FirebaseAuth.getInstance().setCustomUserClaims(userRecord.uid, mapOf("role" to request.role))

                    // Store additional user data in Firestore
                    val firestore = FirestoreClient.getFirestore()
                    val firestoreUserData = FirestoreUserData(
                        name = request.name,
                        email = request.email,
                        role = request.role
                    )

                    firestore.collection("users").document(userRecord.uid).set(firestoreUserData)

                    call.respond(HttpStatusCode.Created, AuthResponse(
                        userId = userRecord.uid, 
                        status = "SUCCESS", 
                        message = "User registered successfully", 
                        role = request.role, 
                        token = null
                    ))

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error registering user: ${e.message}")
                }
            }
        }
    }
}