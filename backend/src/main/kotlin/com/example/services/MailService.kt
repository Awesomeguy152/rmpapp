package com.example.services

import io.ktor.server.application.Application
import io.ktor.server.application.log
import jakarta.mail.Address
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Properties
import kotlinx.serialization.json.*

class MailService(private val app: Application) {
    // Unisender - российский сервис (1500 писем/месяц бесплатно)
    private val unisenderApiKey = env("UNISENDER_API_KEY", "")
    private val unisenderListId = env("UNISENDER_LIST_ID", "1")
    
    // Brevo (Sendinblue) - fallback
    private val brevoApiKey = env("BREVO_API_KEY", "")
    
    // SMTP (Яндекс, Mail.ru и др.)
    private val host = env("SMTP_HOST", "smtp.yandex.ru")
    private val port = env("SMTP_PORT", "465").toInt()
    private val username = env("SMTP_USER", "")
    private val password = env("SMTP_PASS", "")
    private val from = env("SMTP_FROM", "noreply@rmpapp.ru")
    private val fromName = env("SMTP_FROM_NAME", "RMP App")
    private val useSsl = env("SMTP_SSL", "true").toBoolean()
    private val startTls = env("SMTP_STARTTLS", "false").toBoolean()
    private val resetBase = env("RESET_LINK_BASE", "http://localhost:3000")
    
    private val httpClient = HttpClient.newHttpClient()

    private fun env(key: String, default: String): String = System.getenv(key) ?: default

    private fun session(): Session {
        val props = Properties()
        props["mail.smtp.host"] = host
        props["mail.smtp.port"] = port.toString()
        props["mail.smtp.auth"] = "true"
        
        if (useSsl) {
            props["mail.smtp.ssl.enable"] = "true"
            props["mail.smtp.socketFactory.port"] = port.toString()
            props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
        }
        if (startTls) {
            props["mail.smtp.starttls.enable"] = "true"
        }

        return Session.getInstance(props, object : jakarta.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(username, password)
        })
    }

    fun send(to: String, subject: String, html: String) {
        // Приоритет: Unisender -> Brevo -> SMTP (Яндекс/Mail.ru)
        when {
            unisenderApiKey.isNotBlank() -> sendViaUnisender(to, subject, html)
            brevoApiKey.isNotBlank() -> sendViaBrevo(to, subject, html)
            username.isNotBlank() -> sendViaSMTP(to, subject, html)
            else -> app.log.error("❌ No email provider configured!")
        }
    }
    
    private fun sendViaUnisender(to: String, subject: String, html: String) {
        try {
            // Unisender API для отправки email
            val params = mapOf(
                "format" to "json",
                "api_key" to unisenderApiKey,
                "email" to to,
                "sender_name" to fromName,
                "sender_email" to from,
                "subject" to subject,
                "body" to html,
                "list_id" to unisenderListId
            )
            
            val formBody = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
            }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.unisender.com/ru/api/sendEmail"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() in 200..299 && !response.body().contains("\"error\"")) {
                app.log.info("✅ Unisender: sent email to=$to, subject=\"$subject\"")
            } else {
                app.log.error("❌ Unisender error: ${response.body()}")
                // Fallback
                if (brevoApiKey.isNotBlank()) sendViaBrevo(to, subject, html)
                else if (username.isNotBlank()) sendViaSMTP(to, subject, html)
            }
        } catch (e: Exception) {
            app.log.error("❌ Unisender exception: ${e.message}")
            if (brevoApiKey.isNotBlank()) sendViaBrevo(to, subject, html)
            else if (username.isNotBlank()) sendViaSMTP(to, subject, html)
        }
    }
    
    private fun sendViaBrevo(to: String, subject: String, html: String) {
        try {
            val requestBody = buildJsonObject {
                put("sender", buildJsonObject {
                    put("name", fromName)
                    put("email", from)
                })
                put("to", buildJsonArray {
                    add(buildJsonObject { put("email", to) })
                })
                put("subject", subject)
                put("htmlContent", html)
            }.toString()
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", brevoApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() in 200..299) {
                app.log.info("✅ Brevo: sent email to=$to")
            } else {
                app.log.error("❌ Brevo error: ${response.statusCode()} - ${response.body()}")
                if (username.isNotBlank()) sendViaSMTP(to, subject, html)
            }
        } catch (e: Exception) {
            app.log.error("❌ Brevo exception: ${e.message}")
            if (username.isNotBlank()) sendViaSMTP(to, subject, html)
        }
    }
    
    private fun sendViaSMTP(to: String, subject: String, html: String) {
        try {
            val message = MimeMessage(session())
            message.setFrom(InternetAddress(from, fromName, "UTF-8"))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            message.setSubject(subject, "UTF-8")
            message.setContent(html, "text/html; charset=UTF-8")
            Transport.send(message)
            app.log.info("✅ SMTP: sent email to=$to, subject=\"$subject\"")
        } catch (e: Exception) {
            app.log.error("❌ SMTP error: ${e.message}")
            e.printStackTrace()
        }
    }

    fun sendPasswordReset(to: String, token: String) {
        val resetLink = "$resetBase/reset?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}&email=${URLEncoder.encode(to, StandardCharsets.UTF_8)}"
        val html = buildResetEmailHtml(resetLink)
        send(to, "Сброс пароля", html)
    }
    
    fun sendPasswordResetCode(to: String, code: String) {
        val html = buildResetCodeEmailHtml(code)
        send(to, "Код сброса пароля - RMP App", html)
    }

    private fun buildResetEmailHtml(resetLink: String): String = """
        <div style="font-family: Arial, sans-serif; background:#f7f7f7; padding:24px;">
            <div style="max-width:480px; margin:0 auto; background:#ffffff; border:1px solid #e5e5e5; padding:24px;">
                <h2 style="color:#333333; margin-top:0;">Сброс пароля</h2>
                <p style="color:#555555; line-height:1.5;">Нажмите кнопку ниже для сброса пароля.</p>
                <p style="text-align:center; margin:24px 0;">
                    <a href="$resetLink" style="display:inline-block; padding:12px 20px; background:#2563eb; color:#ffffff; text-decoration:none; border-radius:4px;">Сбросить пароль</a>
                </p>
                <p style="color:#777777; font-size:12px;">Если кнопка не работает, скопируйте ссылку:</p>
                <p style="word-break:break-all; color:#2563eb; font-size:12px;">$resetLink</p>
            </div>
        </div>
    """.trimIndent()
    
    private fun buildResetCodeEmailHtml(code: String): String = """
        <div style="font-family: Arial, sans-serif; background:#f7f7f7; padding:24px;">
            <div style="max-width:480px; margin:0 auto; background:#ffffff; border:1px solid #e5e5e5; padding:24px;">
                <h2 style="color:#333333; margin-top:0;">Сброс пароля</h2>
                <p style="color:#555555; line-height:1.5;">Вы запросили сброс пароля для вашего аккаунта в RMP App.</p>
                <p style="color:#555555; line-height:1.5;">Ваш код подтверждения:</p>
                <p style="text-align:center; margin:24px 0;">
                    <span style="display:inline-block; padding:16px 32px; background:#2563eb; color:#ffffff; font-size:32px; font-weight:bold; letter-spacing:8px; border-radius:8px;">$code</span>
                </p>
                <p style="color:#777777; font-size:12px;">Код действителен 15 минут.</p>
                <p style="color:#777777; font-size:12px;">Если вы не запрашивали сброс пароля, проигнорируйте это письмо.</p>
            </div>
        </div>
    """.trimIndent()
}
