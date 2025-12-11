package com.example.plugins

import com.example.schema.Role
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

object JwtConfig {
    val issuer = System.getenv("JWT_ISSUER") ?: "ktor.io"
    val audience = System.getenv("JWT_AUDIENCE") ?: "ktor-audience"
    val secret = System.getenv("JWT_SECRET") ?: "please-change-me"
    val algorithm: Algorithm = Algorithm.HMAC256(secret)
}

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JWT.require(JwtConfig.algorithm).withAudience(JwtConfig.audience).withIssuer(JwtConfig.issuer).build())
            validate { cred ->
                if (cred.payload.getClaim("email").asString().isNullOrBlank()) null else JWTPrincipal(cred.payload)
            }
        }
        jwt("admin-jwt") {
            verifier(JWT.require(JwtConfig.algorithm).withAudience(JwtConfig.audience).withIssuer(JwtConfig.issuer).build())
            validate { cred ->
                val role = cred.payload.getClaim("role").asString()
                if (role == Role.ADMIN.name) JWTPrincipal(cred.payload) else null
            }
        }
    }
}
