package com.example.services

import io.ktor.server.application.Application
import io.ktor.server.application.log
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
import java.util.concurrent.Executors
import kotlinx.serialization.json.*

class MailService(private val app: Application) {
    // Читаем переменные каждый раз чтобы подхватывать изменения после рестарта
    private val brevoApiKey: String get() = env("BREVO_API_KEY", "")
    private val resendApiKey: String get() = env("RESEND_API_KEY", "")
    private val forceSmtp: Boolean get() = env("FORCE_SMTP", "false").toBoolean()
    
    // SMTP (Яндекс, Mail.ru, Gmail и др.)
    private val host: String get() = env("SMTP_HOST", "smtp.yandex.ru")
    private val port: Int get() = env("SMTP_PORT", "587").toInt()
    private val username: String get() = env("SMTP_USER", "")
    private val password: String get() = env("SMTP_PASS", "")
    private val from: String get() = env("SMTP_FROM", "noreply@rmpapp.ru")
    private val fromName: String get() = env("SMTP_FROM_NAME", "RMP App")
    private val useSsl: Boolean get() = env("SMTP_SSL", "false").toBoolean()
    private val startTls: Boolean get() = env("SMTP_STARTTLS", "true").toBoolean()
    private val resetBase: String get() = env("RESET_LINK_BASE", "http://localhost:3000")
    
    private val httpClient = HttpClient.newHttpClient()
    private val emailExecutor = Executors.newSingleThreadExecutor()

    private fun env(key: String, default: String): String = System.getenv(key) ?: default
    
    /**
     * Проверяет, настроен ли хотя бы один провайдер email
     */
    fun isConfigured(): Boolean = resendApiKey.isNotBlank() || brevoApiKey.isNotBlank() || username.isNotBlank()
    
    /**
     * Возвращает текущий провайдер email
     */
    fun getProvider(): String = when {
        forceSmtp && username.isNotBlank() -> "SMTP (forced)"
        resendApiKey.isNotBlank() -> "RESEND_API"
        brevoApiKey.isNotBlank() -> "BREVO_API"
        username.isNotBlank() -> "SMTP"
        else -> "NONE"
    }

    private fun session(): Session {
        val props = Properties()
        props["mail.smtp.host"] = host
        props["mail.smtp.port"] = port.toString()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.connectiontimeout"] = "15000"
        props["mail.smtp.timeout"] = "15000"
        props["mail.smtp.writetimeout"] = "15000"
        
        if (useSsl) {
            props["mail.smtp.ssl.enable"] = "true"
            props["mail.smtp.socketFactory.port"] = port.toString()
            props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
        }
        if (startTls) {
            props["mail.smtp.starttls.enable"] = "true"
            props["mail.smtp.starttls.required"] = "true"
        }

        return Session.getInstance(props, object : jakarta.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(username, password)
        })
    }

    fun send(to: String, subject: String, html: String) {
        app.log.info("📧 Attempting to send email to: $to")
        app.log.info("📧 Provider: ${getProvider()}, RESEND=${resendApiKey.isNotBlank()}, BREVO=${brevoApiKey.isNotBlank()}, SMTP=${username.isNotBlank()}, FORCE_SMTP=$forceSmtp")
        
        if (!isConfigured()) {
            app.log.error("❌ No email provider configured! Set RESEND_API_KEY, BREVO_API_KEY or SMTP_USER/SMTP_PASS environment variables.")
            throw RuntimeException("Email not configured. Set RESEND_API_KEY, BREVO_API_KEY or SMTP_USER/SMTP_PASS in Railway environment variables.")
        }
        
        // Отправляем асинхронно, чтобы не блокировать HTTP запрос
        emailExecutor.submit {
            try {
                sendWithFallback(to, subject, html)
            } catch (e: Exception) {
                app.log.error("❌ Email send error: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun sendWithFallback(to: String, subject: String, html: String) {
        // Если принудительно SMTP
        if (forceSmtp && username.isNotBlank()) {
            app.log.info("📧 FORCE_SMTP=true, using SMTP directly")
            sendViaSMTP(to, subject, html)
            return
        }
        
        // Приоритет: Resend -> Brevo -> SMTP
        val providers = mutableListOf<Pair<String, () -> Unit>>()
        
        if (resendApiKey.isNotBlank()) {
            providers.add("Resend API" to { sendViaResend(to, subject, html) })
        }
        if (brevoApiKey.isNotBlank()) {
            providers.add("Brevo API" to { sendViaBrevo(to, subject, html) })
        }
        if (username.isNotBlank()) {
            providers.add("SMTP" to { sendViaSMTP(to, subject, html) })
        }
        
        var lastError: Exception? = null
        for ((name, sender) in providers) {
            try {
                app.log.info("📧 Trying $name...")
                sender()
                app.log.info("✅ Email sent via $name")
                return
            } catch (e: Exception) {
                app.log.warn("⚠️ $name failed: ${e.message}")
                lastError = e
            }
        }
        
        throw lastError ?: RuntimeException("No email provider available")
    }
    
    private fun sendViaResend(to: String, subject: String, html: String) {
        val senderEmail = if (from == "noreply@rmpapp.ru") "onboarding@resend.dev" else from
        app.log.info("📧 Resend: sender=$senderEmail, to=$to")
        
        val requestBody = buildJsonObject {
            put("from", "$fromName <$senderEmail>")
            put("to", buildJsonArray { add(to) })
            put("subject", subject)
            put("html", html)
        }.toString()
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.resend.com/emails"))
            .header("Authorization", "Bearer $resendApiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() in 200..299) {
            app.log.info("✅ Resend: sent email to=$to, response=${response.body()}")
        } else {
            app.log.error("❌ Resend error: ${response.statusCode()} - ${response.body()}")
            throw RuntimeException("Resend API error: ${response.statusCode()} - ${response.body()}")
        }
    }
    
    private fun sendViaBrevo(to: String, subject: String, html: String) {
        // Для Brevo используем email из SMTP_USER если SMTP_FROM не настроен
        val senderEmail = if (from == "noreply@rmpapp.ru") username.ifBlank { from } else from
        app.log.info("📧 Brevo: sender=$senderEmail, to=$to")
        
        val requestBody = buildJsonObject {
            put("sender", buildJsonObject {
                put("name", fromName)
                put("email", senderEmail)
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
            // Больше не падаем на SMTP — возвращаем ошибку Brevo
            throw RuntimeException("Brevo API error: ${response.statusCode()} - ${response.body()}")
        }
    }
    
    private fun sendViaSMTP(to: String, subject: String, html: String) {
        app.log.info("📧 SMTP: Connecting to $host:$port (SSL=$useSsl, STARTTLS=$startTls)")
        app.log.info("📧 SMTP: From=$from ($fromName), User=$username")
        
        // Яндекс требует чтобы From совпадал с User
        val actualFrom = if (from.isBlank() || from == "noreply@rmpapp.ru") username else from
        app.log.info("📧 SMTP: Actual From=$actualFrom")
        
        try {
            val message = MimeMessage(session())
            message.setFrom(InternetAddress(actualFrom, fromName, "UTF-8"))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            message.setSubject(subject, "UTF-8")
            message.setContent(html, "text/html; charset=UTF-8")
            app.log.info("📧 SMTP: Sending message...")
            Transport.send(message)
            app.log.info("✅ SMTP: sent email to=$to")
        } catch (e: Exception) {
            app.log.error("❌ SMTP send failed: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            throw e
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
    
    /**
     * Синхронная отправка для тестирования - возвращает ошибку если есть
     */
    fun sendSync(to: String, subject: String, html: String): String? {
        app.log.info("📧 [SYNC] Attempting to send email to: $to")
        app.log.info("📧 [SYNC] Provider: ${getProvider()}, RESEND=${resendApiKey.isNotBlank()}, BREVO=${brevoApiKey.isNotBlank()}, SMTP=${username.isNotBlank()}")
        
        if (!isConfigured()) {
            return "No email provider configured. Set RESEND_API_KEY, BREVO_API_KEY or SMTP credentials."
        }
        
        return try {
            sendWithFallback(to, subject, html)
            null
        } catch (e: Exception) {
            app.log.error("❌ [SYNC] Email error: ${e.javaClass.simpleName}: ${e.message}")
            "${e.javaClass.simpleName}: ${e.message}"
        }
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
