package com.example.notificationarchiver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class NotificationHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as App).repository
    val preferences = (application as App).preferencesManager

    private val _notifications = MutableLiveData<List<NotificationDatabaseHelper.NotificationEntry>>()
    val notifications: LiveData<List<NotificationDatabaseHelper.NotificationEntry>> = _notifications

    fun loadNotifications(packageName: String?) {
        val list = if (packageName != null) {
            repository.getNotificationsByPackage(packageName)
        } else {
            repository.getAllNotifications()
        }
        _notifications.value = list
    }

    fun deleteNotification(id: Long) {
        repository.deleteNotification(id)
        // перезагрузка будет вызвана после удаления
    }

    fun removeImage(id: Long) {
        repository.removeImageForNotification(id)
    }
}