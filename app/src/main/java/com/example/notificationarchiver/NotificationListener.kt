package com.example.notificationarchiver

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.lifecycle.MutableLiveData
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

        val prefs = (applicationContext as App).preferencesManager
        if (prefs.ignoredPackages.contains(sbn.packageName)) return

        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val timestamp = System.currentTimeMillis()

        val picture: Bitmap? = if (extras.containsKey(Notification.EXTRA_PICTURE)) {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_PICTURE)
        } else null

        val imageBytes: ByteArray? = picture?.let {
            ByteArrayOutputStream().use { stream ->
                it.compress(Bitmap.CompressFormat.PNG, 80, stream)
                stream.toByteArray()
            }
        }

        notificationLiveData.postValue(NotificationData(sbn.packageName, title, text, timestamp))

        val repo = (applicationContext as App).repository
        repo.upsertNotification(sbn.packageName, title, text, timestamp, sbn.key, imageBytes)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}