package com.nano.min.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nano.min.MainActivity
import com.nano.min.R
import com.nano.min.network.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val authService: AuthService by inject()

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "chat_messages"
        private const val CHANNEL_NAME = "Сообщения чата"
        private const val PREFS_NAME = "fcm_prefs"
        private const val KEY_TOKEN = "fcm_token"

        /**
         * Получает текущий FCM токен и отправляет на сервер
         */
        fun registerToken(context: Context, authService: AuthService) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "FCM Token obtained: ${token.take(20)}...")
                    
                    // Сохраняем локально
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_TOKEN, token)
                        .apply()
                    
                    // Отправляем на сервер
                    CoroutineScope(Dispatchers.IO).launch {
                        val success = authService.registerDeviceToken(token)
                        Log.d(TAG, "Token registration: ${if (success) "SUCCESS" else "FAILED"}")
                    }
                } else {
                    Log.e(TAG, "Failed to get FCM token", task.exception)
                }
            }
        }

        /**
         * Получает сохранённый токен
         */
        fun getSavedToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, null)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token: ${token.take(20)}...")
        
        // Сохраняем локально
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        
        // Отправляем на сервер
        CoroutineScope(Dispatchers.IO).launch {
            val success = authService.registerDeviceToken(token)
            Log.d(TAG, "New token registration: ${if (success) "SUCCESS" else "FAILED"}")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received: ${message.data}")

        val title = message.notification?.title ?: message.data["title"] ?: "Новое сообщение"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val conversationId = message.data["conversationId"]

        showNotification(title, body, conversationId)
    }

    private fun showNotification(title: String, body: String, conversationId: String?) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Создание канала уведомлений (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent для открытия приложения при нажатии
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            conversationId?.let { putExtra("conversationId", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
