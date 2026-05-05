package com.example.notificationarchiver

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NotificationViewModel : ViewModel() {

    val latestNotification: LiveData<NotificationListener.NotificationData> = NotificationListener.notificationLiveData

    fun setServiceActive(active: Boolean) {
        NotificationListener.isServiceActive.postValue(active)
    }
}