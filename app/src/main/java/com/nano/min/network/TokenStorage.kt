package com.nano.min.network

/**
 * Simple token storage interface. Implementations can persist token to DataStore/Prefs.
 */
interface TokenStorage {
    fun getToken(): String?
    fun setToken(token: String?)
}

