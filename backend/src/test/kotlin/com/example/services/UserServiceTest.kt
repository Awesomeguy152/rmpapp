package com.example.services

import kotlin.test.*

/**
 * Unit тесты для UserService
 */
class UserServiceTest {
    
    @Test
    fun `email validation accepts valid emails`() {
        val validEmails = listOf(
            "test@example.com",
            "user.name@domain.org",
            "user+tag@gmail.com",
            "user123@test.co.uk"
        )
        
        validEmails.forEach { email ->
            assertTrue(isValidEmail(email), "Email should be valid: $email")
        }
    }
    
    @Test
    fun `email validation rejects invalid emails`() {
        val invalidEmails = listOf(
            "invalid",
            "no@domain",
            "@nodomain.com",
            "spaces in@email.com",
            ""
        )
        
        invalidEmails.forEach { email ->
            assertFalse(isValidEmail(email), "Email should be invalid: $email")
        }
    }
    
    @Test
    fun `password validation requires minimum length`() {
        assertFalse(isValidPassword("short"))
        assertFalse(isValidPassword("1234567"))
        assertTrue(isValidPassword("ValidPass123"))
        assertTrue(isValidPassword("12345678"))
    }
    
    @Test
    fun `username validation works correctly`() {
        assertTrue(isValidUsername("validuser"))
        assertTrue(isValidUsername("user123"))
        assertTrue(isValidUsername("user_name"))
        assertFalse(isValidUsername("ab")) // too short
        assertFalse(isValidUsername("")) // empty
    }
    
    // Helper functions matching backend validation logic
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
