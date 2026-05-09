package com.example.notificationarchiver

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.lifecycle.MutableLiveData
import android.content.SharedPreferences
import java.io.ByteArrayOutputStream

class NotificationListener : NotificationListenerService() {

    companion object {
        val notificationLiveData = MutableLiveData<NotificationData>()
        val isServiceActive = MutableLiveData(false)
    }

    data class NotificationData(
        val packageName: String,
        val title: String,
        val text: String,
        val timestamp: Long
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceActive.postValue(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceActive.postValue(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val packageName = sbn.packageName
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val ignored = prefs.getStringSet("ignored_packages", emptySet()) ?: emptySet()

        if (packageName in ignored) return  // пропускаем игнорируемое приложение
        sbn?.let {
            val prefs = applicationContext.getSharedPreferences("app_settings", MODE_PRIVATE)
            val ignoredPackages = prefs.getStringSet("ignored_packages", emptySet()) ?: emptySet()
            if (ignoredPackages.contains(it.packageName)) {
                return  // игнорируем уведомление
            }
            val notification: Notification = it.notification
            val extras = notification.extras

            val packageName = it.packageName
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val timestamp = System.currentTimeMillis()
            val key = it.key

            // Извлечение картинки (основное изображение BigPicture)
            val picture: Bitmap? = if (extras.containsKey(Notification.EXTRA_PICTURE)) {
                @Suppress("DEPRECATION")
                extras.getParcelable(Notification.EXTRA_PICTURE)
            } else null

            val imageBytes: ByteArray? = if (picture != null) {
                // Сохраняем в оригинальном разрешении без ресайза
                val stream = ByteArrayOutputStream()
                picture.compress(Bitmap.CompressFormat.PNG, 80, stream)
                stream.toByteArray()
            } else null

            val notificationData = NotificationData(packageName, title, text, timestamp)
            notificationLiveData.postValue(notificationData)

            val disableDuplicates = prefs.getBoolean("disable_duplicate_notifications", false)

            val dbHelper = NotificationDatabaseHelper.getInstance(applicationContext)
            dbHelper.upsertNotification(packageName, title, text, timestamp, key, disableDuplicates, imageBytes)
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}