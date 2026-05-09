package com.example.notificationarchiver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val repository = app.repository
    val preferences = app.preferencesManager

    private val _packageSummaries = MutableLiveData<List<NotificationDatabaseHelper.PackageSummary>>()
    val packageSummaries: LiveData<List<NotificationDatabaseHelper.PackageSummary>> = _packageSummaries

    val latestNotification: LiveData<NotificationListener.NotificationData> =
        NotificationListener.notificationLiveData

    val isServiceActive: LiveData<Boolean> = NotificationListener.isServiceActive

    init {
        loadPackageSummaries()
    }

    fun loadPackageSummaries() {
        _packageSummaries.postValue(repository.getPackageSummaries())
    }

    fun deleteNotificationsByPackage(packageName: String) {
        repository.deleteByPackage(packageName)
        loadPackageSummaries()
    }

    fun ignorePackage(packageName: String) {
        preferences.addIgnoredPackage(packageName)
    }
}