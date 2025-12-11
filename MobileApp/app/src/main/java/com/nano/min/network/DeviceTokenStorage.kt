package com.nano.min.network

import android.content.Context
import androidx.core.content.edit

class DeviceTokenStorage(private val context: Context) : TokenStorage {

    private val sharedPreferences by lazy {
        context.getSharedPreferences("app_auth", Context.MODE_PRIVATE)
    }

    companion object {
        private const val TOKEN_KEY = "jwt_auth_token"
    }

    override fun getToken(): String? {
        return sharedPreferences.getString(TOKEN_KEY, null)
    }

    override fun setToken(token: String?) {
        sharedPreferences.edit {
            putString(TOKEN_KEY, token)
        }
    }
}
