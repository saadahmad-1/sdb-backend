package utils

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import com.google.firebase.auth.FirebaseAuth

suspend fun ApplicationCall.requireRole(role: String): Boolean {
    val token = this.request.queryParameters["token"] ?: return false
    val decodedToken = FirebaseAuth.getInstance().verifyIdToken(token)
    val userRole = decodedToken.claims["role"] as? String

    return if (userRole == role) {
        true
    } else {
        this.respond(HttpStatusCode.Forbidden, "Access denied. Required role: $role")
        false
    }
}
