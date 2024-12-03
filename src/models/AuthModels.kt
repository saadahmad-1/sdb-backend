package models

import kotlinx.serialization.Serializable

@Serializable
data class UserRegistrationRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String // Added role field
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
    val role: String? = null // Include role in the response
)
