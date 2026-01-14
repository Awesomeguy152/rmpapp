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
    val role: Role = Role.USER,
    val adminSecret: String? = null
)

@Serializable
data class LoginRq(
    val email: String,
    val password: String
)

@Serializable
data class LoginRs(val token: String)

@Serializable
data class ErrorRs(val error: String)

@Serializable
data class ForgotRs(val ok: Boolean, val mailOk: Boolean, val mailError: String? = null)

@Serializable
data class ResetRs(val status: String)

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

@Serializable
data class DirectResetRq(
    val email: String,
    val newPassword: String
)

@Serializable
data class RequestCodeRq(val email: String)

@Serializable
data class RequestCodeRs(val sent: Boolean, val message: String? = null)

@Serializable
data class VerifyCodeRq(val email: String, val code: String)

@Serializable
data class VerifyCodeRs(val valid: Boolean)

@Serializable
data class ResetWithCodeRq(val email: String, val code: String, val newPassword: String)

@Serializable
data class ResetWithCodeRs(val success: Boolean, val message: String? = null)

fun Route.authRoutes() {
    val service = UserService()
    val users = service
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
                        call.respond(HttpStatusCode.Forbidden, ErrorRs("admin_registration_disabled"))
                        return@post
                    }

                    rq.adminSecret.isNullOrBlank() || rq.adminSecret != requiredSecret -> {
                        call.respond(HttpStatusCode.Forbidden, ErrorRs("admin_secret_invalid"))
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
                call.respond(HttpStatusCode.Unauthorized, ErrorRs("invalid credentials"))
            } else {
                val token = JWT.create()
                    .withIssuer(JwtConfig.issuer)
                    .withAudience(JwtConfig.audience)
                    .withClaim("sub", dto.id)
                    .withClaim("email", dto.email)
                    .withClaim("role", dto.role.name)
                    .withExpiresAt(Date(System.currentTimeMillis() + 1000L * 60 * 60 * 12))
                    .sign(JwtConfig.algorithm)

                call.respond(LoginRs(token))
            }
        }

        post("/forgot") {
            val rq = call.receive<ForgotRq>()
            val token = reset.requestReset(rq.email.trim())
            var mailError: String? = null
            var mailOk = false
            if (token != null) {
                try {
                    mail.sendPasswordReset(rq.email.trim(), token)
                    mailOk = true
                } catch (e: Exception) {
                    mailError = e.message ?: "Unknown error"
                }
            }
            call.respond(HttpStatusCode.OK, ForgotRs(
                ok = token != null && mailOk,
                mailOk = mailOk,
                mailError = mailError
            ))
        }

        post("/reset") {
            val rq = call.receive<ResetRq>()
            val ok = reset.resetPassword(rq.email.trim(), rq.token, rq.newPassword)
            if (ok) {
                call.respond(HttpStatusCode.OK, ResetRs("password_updated"))
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorRs("invalid_or_expired_token"))
            }
        }

        // Прямой сброс пароля без email верификации
        post("/direct-reset") {
            val rq = call.receive<DirectResetRq>()
            val ok = users.directResetPassword(rq.email.trim(), rq.newPassword)
            if (ok) {
                call.respond(HttpStatusCode.OK, ResetRs("password_updated"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorRs("user_not_found"))
            }
        }
        
        // ============ Мобильное приложение - сброс через 6-значный код ============
        
        // Запрос кода на email
        post("/request-code") {
            val rq = call.receive<RequestCodeRq>()
            val sent = try {
                reset.requestResetWithCode(rq.email.trim().lowercase())
            } catch (e: Exception) {
                call.application.log.error("Failed to send reset code: ${e.message}")
                false
            }
            // Всегда возвращаем успех для безопасности (не раскрываем существует ли email)
            call.respond(HttpStatusCode.OK, RequestCodeRs(
                sent = true,
                message = if (sent) "Код отправлен на указанную почту" else "Если аккаунт существует, код будет отправлен"
            ))
        }
        
        // Проверка кода
        post("/verify-code") {
            val rq = call.receive<VerifyCodeRq>()
            val valid = reset.verifyCode(rq.email.trim().lowercase(), rq.code)
            call.respond(HttpStatusCode.OK, VerifyCodeRs(valid = valid))
        }
        
        // Сброс пароля с кодом
        post("/reset-with-code") {
            val rq = call.receive<ResetWithCodeRq>()
            val ok = reset.resetPassword(rq.email.trim().lowercase(), rq.code, rq.newPassword)
            if (ok) {
                call.respond(HttpStatusCode.OK, ResetWithCodeRs(success = true, message = "Пароль успешно изменён"))
            } else {
                call.respond(HttpStatusCode.BadRequest, ResetWithCodeRs(success = false, message = "Неверный или просроченный код"))
            }
        }
    }
}
