package com.example.routes

import com.auth0.jwt.JWT
import com.example.plugins.JwtConfig
import com.example.schema.Role
import com.example.services.MailService
import com.example.services.PasswordResetService
import com.example.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class RegisterRq(
    val email: String,
    val password: String,
    val role: Role,
    val adminSecret: String? = null
)

@Serializable
data class LoginRq(
    val email: String,
    val password: String
)

@Serializable
data class ForgotRq(
    val email: String
)

@Serializable
data class ResetRq(
    val email: String,
    val token: String,
    val newPassword: String
)

fun Route.authRoutes() {
    val service = UserService()
    val mail = MailService(application)
    val reset = PasswordResetService(service, mail)

    route("/api/auth") {
        post("/register") {
            val rq = call.receive<RegisterRq>()

            if (rq.role == Role.ADMIN) {
                val requiredSecret = System.getenv("ADMIN_REGISTRATION_SECRET")?.takeIf { it.isNotBlank() }

                when {
                    requiredSecret == null -> {
                        application.log.warn("Admin registration blocked: ADMIN_REGISTRATION_SECRET not configured")
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "admin_registration_disabled"))
                        return@post
                    }

                    rq.adminSecret.isNullOrBlank() || rq.adminSecret != requiredSecret -> {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "admin_secret_invalid"))
                        return@post
                    }
                }
            }

            val dto = service.create(rq.email.trim(), rq.password, rq.role)
            call.respond(HttpStatusCode.Created, dto)
        }

        post("/login") {
            val rq = call.receive<LoginRq>()
            val dto = service.verifyAndGet(rq.email.trim(), rq.password)
            if (dto == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid credentials"))
            } else {
                val token = JWT.create()
                    .withIssuer(JwtConfig.issuer)
                    .withAudience(JwtConfig.audience)
                    .withClaim("sub", dto.id)
                    .withClaim("email", dto.email)
                    .withClaim("role", dto.role.name)
                    .withExpiresAt(Date(System.currentTimeMillis() + 1000L * 60 * 60 * 12))
                    .sign(JwtConfig.algorithm)

                call.respond(mapOf("token" to token))
            }
        }

        post("/forgot") {
            val rq = call.receive<ForgotRq>()
            val token = reset.requestReset(rq.email.trim())
            if (token != null) {
                mail.sendPasswordReset(rq.email.trim(), token)
            }
            call.respond(HttpStatusCode.OK, mapOf("ok" to true))
        }

        post("/reset") {
            val rq = call.receive<ResetRq>()
            val ok = reset.resetPassword(rq.email.trim(), rq.token, rq.newPassword)
            if (ok) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "password_updated"))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_or_expired_token"))
            }
        }
    }
}
