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
import org.mindrot.jbcrypt.BCrypt
import java.util.*

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
    val role: String = "",
    val hashedPassword: String = "",
    val salt: String = "",
    val lastLoginAttempt: Long = 0,
    val loginAttempts: Int = 0
) {
    // No-argument constructor for Firebase
    constructor() : this("", "", "", "", "", 0, 0)
}

fun Application.configureAuthRouting() {
    routing {
        route("api/v1/auth") {
            post("register") {
                val request = call.receive<UserRegistrationRequest>()
                val validRoles = listOf("Admin", "Courier", "Customer")

                // Input validation
                if (request.name.isBlank() || request.name.length < 2) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "Name must be at least 2 characters long."
                    ))
                    return@post
                }

                if (!isValidEmail(request.email)) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(
                        userId = "",
                        status = "ERROR", 
                        message = "Invalid email format."
                    ))
                    return@post
                }

                if (!isStrongPassword(request.password)) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "Password must be at least 8 characters long and contain uppercase, lowercase, number, and special character."
                    ))
                    return@post
                }

                if (request.role !in validRoles) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "Invalid role. Allowed roles: $validRoles"
                    ))
                    return@post
                }

                try {
                    val auth = FirebaseAuth.getInstance()
                    val firestore = FirestoreClient.getFirestore()

                    // Check if email already exists
                    val existingUser = try {
                        auth.getUserByEmail(request.email)
                        true
                    } catch (e: Exception) {
                        false
                    }

                    if (existingUser) {
                        call.respond(HttpStatusCode.Conflict, AuthResponse(
                            userId = "",
                            status = "ERROR",
                            message = "Email already in use."
                        ))
                        return@post
                    }

                    // Generate salt and hash password
                    val salt = BCrypt.gensalt()
                    val hashedPassword = BCrypt.hashpw(request.password, salt)

                    // Create user in Firebase Authentication
                    val createRequest = UserRecord.CreateRequest()
                        .setEmail(request.email)
                        .setPassword(request.password)
                        .setDisplayName(request.name)

                    val userRecord = auth.createUser(createRequest)

                    // Set custom claims (role)
                    auth.setCustomUserClaims(userRecord.uid, mapOf("role" to request.role))

                    // Store additional user data in Firestore
                    val firestoreUserData = FirestoreUserData(
                        name = request.name,
                        email = request.email,
                        role = request.role,
                        hashedPassword = hashedPassword,
                        salt = salt
                    )

                    firestore.collection("users").document(userRecord.uid).set(firestoreUserData)

                    call.respond(HttpStatusCode.Created, AuthResponse(
                        userId = userRecord.uid, 
                        status = "SUCCESS", 
                        message = "User registered successfully", 
                        role = request.role
                    ))

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "Error registering user: ${e.localizedMessage}"
                    ))
                }
            }

            post("login") {
                val request = call.receive<UserLoginRequest>()
                val email = request.email
                val password = request.password

                if (email.isBlank() || password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "Email and password are required."
                    ))
                    return@post
                }

                try {
                    val auth = FirebaseAuth.getInstance()
                    val firestore = FirestoreClient.getFirestore()

                    // Get user record
                    val userRecord = try {
                        auth.getUserByEmail(email)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.Unauthorized, AuthResponse(
                            userId = "",
                            status = "ERROR", 
                            message = "User not found."
                        ))
                        return@post
                    }

                    // Fetch user data from Firestore
                    val userDoc = firestore.collection("users").document(userRecord.uid).get().get()
                    val userData = userDoc.toObject(FirestoreUserData::class.java)
                        ?: throw Exception("User data not found")

                    // Check for account lockout
                    val currentTime = System.currentTimeMillis()
                    val lockoutDuration = 15 * 60 * 1000 // 15 minutes in milliseconds
                    if (userData.loginAttempts >= 5 && 
                        currentTime - userData.lastLoginAttempt < lockoutDuration) {
                        call.respond(HttpStatusCode.Forbidden, AuthResponse(
                            userId = "",
                            status = "ERROR",
                            message = "Account temporarily locked. Try again in 15 minutes."
                        ))
                        return@post
                    }

                    // Verify password
                    val isPasswordValid = BCrypt.checkpw(password, userData.hashedPassword)

                    if (!isPasswordValid) {
                        // Update login attempts
                        val updatedAttempts = userData.loginAttempts + 1
                        firestore.collection("users").document(userRecord.uid).update(
                            mapOf(
                                "loginAttempts" to updatedAttempts,
                                "lastLoginAttempt" to currentTime
                            )
                        )

                        call.respond(HttpStatusCode.Unauthorized, AuthResponse(
                            userId = "",
                            status = "ERROR",
                            message = "Invalid email or password."
                        ))
                        return@post
                    }

                    // Reset login attempts on successful login
                    firestore.collection("users").document(userRecord.uid).update(
                        mapOf(
                            "loginAttempts" to 0,
                            "lastLoginAttempt" to currentTime
                        )
                    )

                    // Create a custom token for authentication
                    val customToken = auth.createCustomToken(userRecord.uid)

                    call.respond(AuthResponse(
                        userId = userRecord.uid, 
                        status = "SUCCESS", 
                        message = "Login successful", 
                        token = customToken, 
                        role = userData.role
                    ))

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "Authentication failed: ${e.localizedMessage}"
                    ))
                }
            }

            post("reset-password") {
                val request = call.receive<UserLoginRequest>()
                val email = request.email
                val newPassword = request.password

                if (email.isBlank() || newPassword.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "Email and new password are required."
                    ))
                    return@post
                }

                if (!isStrongPassword(newPassword)) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "New password must be at least 8 characters long and contain uppercase, lowercase, number, and special character."
                    ))
                    return@post
                }

                try {
                    val auth = FirebaseAuth.getInstance()
                    val firestore = FirestoreClient.getFirestore()

                    // Get user record
                    val userRecord = try {
                        auth.getUserByEmail(email)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.Unauthorized, AuthResponse(
                            userId = "",
                            status = "ERROR", 
                            message = "User not found."
                        ))
                        return@post
                    }

                    // Generate new salt and hash
                    val newSalt = BCrypt.gensalt()
                    val newHashedPassword = BCrypt.hashpw(newPassword, newSalt)

                    // Update Firebase Authentication password
                    auth.updateUser(
                        UserRecord.UpdateRequest(userRecord.uid)
                            .setPassword(newPassword)
                    )

                    // Update Firestore user data
                    firestore.collection("users").document(userRecord.uid).update(
                        mapOf(
                            "hashedPassword" to newHashedPassword,
                            "salt" to newSalt
                        )
                    )

                    call.respond(AuthResponse(
                        userId = userRecord.uid,
                        status = "SUCCESS",
                        message = "Password reset successful"
                    ))

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, AuthResponse(
                        userId = "",
                        status = "ERROR",
                        message = "Password reset failed: ${e.localizedMessage}"
                    ))
                }
            }
        }
    }
}

// Utility functions for validation
fun isValidEmail(email: String): Boolean {
    val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$")
    return email.matches(emailRegex)
}

fun isStrongPassword(password: String): Boolean {
    // At least 8 characters, one uppercase, one lowercase, one number, one special char
    val passwordRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}$")
    return password.matches(passwordRegex)
}