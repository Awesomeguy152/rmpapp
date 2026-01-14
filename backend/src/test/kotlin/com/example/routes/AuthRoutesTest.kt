package com.example.routes

import kotlin.test.*

/**
 * Unit тесты для валидации данных аутентификации
 */
class AuthRoutesTest {
    
    @Test
    fun `valid email format is accepted`() {
        val validEmails = listOf(
            "test@example.com",
            "user.name@domain.org",
            "user123@test.co.uk"
        )
        
        validEmails.forEach { email ->
            assertTrue(isValidEmail(email), "Should accept: $email")
        }
    }
    
    @Test
    fun `invalid email format is rejected`() {
        val invalidEmails = listOf(
            "invalid",
            "no@domain",
            "@nodomain.com",
            ""
        )
        
        invalidEmails.forEach { email ->
            assertFalse(isValidEmail(email), "Should reject: $email")
        }
    }
    
    @Test
    fun `password must be at least 8 characters`() {
        assertFalse(isValidPassword("short"))
        assertFalse(isValidPassword("1234567"))
        assertTrue(isValidPassword("12345678"))
        assertTrue(isValidPassword("StrongPass123!"))
    }
    
    @Test
    fun `username must be at least 3 characters`() {
        assertFalse(isValidUsername("ab"))
        assertFalse(isValidUsername(""))
        assertTrue(isValidUsername("abc"))
        assertTrue(isValidUsername("validuser"))
    }
    
    @Test
    fun `username can only contain alphanumeric and underscore`() {
        assertTrue(isValidUsername("user_name"))
        assertTrue(isValidUsername("user123"))
        assertFalse(isValidUsername("user name"))
        assertFalse(isValidUsername("user@name"))
    }
    
    // Helper validation functions
    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }
    
    private fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }
    
    private fun isValidUsername(username: String): Boolean {
        return username.length >= 3 && username.matches("^[a-zA-Z0-9_]+$".toRegex())
    }
}
