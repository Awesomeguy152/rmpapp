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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Properties

class MailService(private val app: Application) {
    private val host = env("SMTP_HOST", "mailhog")
    private val port = env("SMTP_PORT", "1025").toInt()
    private val username = env("SMTP_USER", "")
    private val password = env("SMTP_PASS", "")
    private val from = env("SMTP_FROM", "no-reply@example.com")
    private val startTls = env("SMTP_STARTTLS", "false").toBoolean()
    private val resetBase = env("RESET_LINK_BASE", "http://localhost:3000")

    private fun env(key: String, default: String): String = System.getenv(key) ?: default

    private fun session(): Session {
        val props = Properties()
        props["mail.smtp.host"] = host
        props["mail.smtp.port"] = port.toString()
        props["mail.smtp.auth"] = username.isNotBlank().toString()
        props["mail.smtp.starttls.enable"] = startTls.toString()

        return if (username.isNotBlank()) {
            Session.getInstance(props, object : jakarta.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(username, password)
            })
        } else {
            Session.getInstance(props)
        }
    }

    fun send(to: String, subject: String, html: String) {
        val message = MimeMessage(session())
        message.setFrom(InternetAddress(from))
        val recipients: Array<Address> = InternetAddress.parse(to, false)
            .map { it as Address }
            .toTypedArray()
        message.setRecipients(Message.RecipientType.TO, recipients)
        message.setSubject(subject, "UTF-8")
        message.setContent(html, "text/html; charset=UTF-8")
        Transport.send(message)
        app.log.info("MailService: sent email to=$to, subject=\"$subject\"")
    }

    fun sendPasswordReset(to: String, token: String) {
        val resetLink = buildResetLink(resetBase, to, token)
        val html = buildResetEmailHtml(resetLink)
        send(to, "Password reset", html)
    }

    private fun buildResetLink(base: String, email: String, token: String): String {
        val encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8)
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
        return "$base/reset?token=$encodedToken&email=$encodedEmail"
    }

    private fun buildResetEmailHtml(resetLink: String): String = """
        <div style="font-family: Arial, sans-serif; background:#f7f7f7; padding:24px;">
            <div style="max-width:480px; margin:0 auto; background:#ffffff; border:1px solid #e5e5e5; padding:24px;">
                <h2 style="color:#333333; margin-top:0;">Reset your password</h2>
                <p style="color:#555555; line-height:1.5;">Click the button below to set a new password.</p>
                <p style="text-align:center; margin:24px 0;">
                    <a href="$resetLink" style="display:inline-block; padding:12px 20px; background:#2563eb; color:#ffffff; text-decoration:none; border-radius:4px;">Reset password</a>
                </p>
                <p style="color:#777777; font-size:12px; line-height:1.4;">If the button does not work, copy and paste this link into your browser:</p>
                <p style="word-break:break-all; color:#2563eb; font-size:12px;">$resetLink</p>
            </div>
        </div>
    """.trimIndent()
}

// val mail = MailService(this)
// mail.send("user@example.com", "Hello", "<b>Hi!</b>")
// mail.sendPasswordReset("user@example.com", token)
