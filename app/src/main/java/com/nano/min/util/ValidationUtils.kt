package com.nano.min.util

import android.util.Patterns

object ValidationUtils {
    
    /**
     * Проверяет валидность email
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    /**
     * Проверяет валидность пароля
     * Минимум 8 символов
     */
    fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }
    
    /**
     * Получить сообщение об ошибке email
     */
    fun getEmailError(email: String): EmailValidationError? {
        return when {
            email.isBlank() -> EmailValidationError.EMPTY
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> EmailValidationError.INVALID_FORMAT
            else -> null
        }
    }
    
    /**
     * Получить сообщение об ошибке пароля
     */
    fun getPasswordError(password: String): PasswordValidationError? {
        return when {
            password.isBlank() -> PasswordValidationError.EMPTY
            password.length < 8 -> PasswordValidationError.TOO_SHORT
            else -> null
        }
    }
}

enum class EmailValidationError {
    EMPTY,
    INVALID_FORMAT
}

enum class PasswordValidationError {
    EMPTY,
    TOO_SHORT
}
