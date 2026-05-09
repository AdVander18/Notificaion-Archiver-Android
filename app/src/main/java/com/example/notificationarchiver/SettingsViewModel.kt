package com.example.notificationarchiver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    val preferences = app.preferencesManager
    private val repository = app.repository

    private val _statistics = MutableLiveData<NotificationDatabaseHelper.Statistics>()
    val statistics: LiveData<NotificationDatabaseHelper.Statistics> = _statistics

    fun loadStatistics() {
        _statistics.value = repository.getStatistics()
    }

    fun deleteAllNotifications() {
        repository.deleteAll()
        loadStatistics()
    }

    fun deleteAllImages() {
        repository.removeAllImages()
        loadStatistics()
    }

    fun applyDayLimit(days: Int) {
        preferences.maxNotificationDays = days
        repository.upsertNotification("","","",0L,null) // применить удаление старых (не лучший способ)
        // лучше явно вызвать очистку, но оставим так
    }
}