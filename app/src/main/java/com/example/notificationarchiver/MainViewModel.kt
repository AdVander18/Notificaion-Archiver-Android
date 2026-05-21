package com.example.notificationarchiver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val repository = app.repository
    val preferences = app.preferencesManager

    private val _packageSummaries = MutableLiveData<List<NotificationDatabaseHelper.PackageSummary>>()

    private val _searchResults = MutableLiveData<List<NotificationDatabaseHelper.NotificationEntry>>()
    val searchResults: LiveData<List<NotificationDatabaseHelper.NotificationEntry>> = _searchResults

    val packageSummaries: LiveData<List<NotificationDatabaseHelper.PackageSummary>> = _packageSummaries

    val latestNotification: LiveData<NotificationListener.NotificationData> =
        NotificationListener.notificationLiveData

    val isServiceActive: LiveData<Boolean> = NotificationListener.isServiceActive

    init {
        loadPackageSummaries()
    }

    fun searchNotifications(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (query.isBlank()) {
                _searchResults.postValue(emptyList())
            } else {
                val results = repository.searchNotifications(query.trim())
                _searchResults.postValue(results)
            }
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNotification(id)
            loadPackageSummaries()
        }
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