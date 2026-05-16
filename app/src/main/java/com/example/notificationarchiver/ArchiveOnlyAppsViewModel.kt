package com.example.notificationarchiver

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ArchiveOnlyAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as App).preferencesManager
    private val pm = application.packageManager

    data class AppInfo(val packageName: String, val appName: String)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _query = MutableLiveData("")
    val filteredApps = MediatorLiveData<List<AppInfo>>().apply {
        addSource(_apps) { recomputeFilter() }
        addSource(_query) { recomputeFilter() }
    }

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private fun recomputeFilter() {
        val appsList = _apps.value ?: emptyList()
        val query = _query.value ?: ""
        filteredApps.value = if (query.isBlank()) appsList
        else appsList.filter { it.appName.contains(query, ignoreCase = true) }
    }

    init {
        loadAppsAsync()
    }

    private fun loadAppsAsync() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(400)
            loadApps()
            _isLoading.value = false
        }
    }

    private fun loadApps() {
        val archiveOnly = prefs.archiveOnlyPackages
        val allApps = pm.getInstalledApplications(0)
            .filter { it.packageName != getApplication<App>().packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString()) }
            .sortedWith(
                compareByDescending<AppInfo> { archiveOnly.contains(it.packageName) }
                    .thenBy { it.appName.lowercase() }
            )
        _apps.value = allApps
    }

    fun isArchiveOnly(packageName: String) = prefs.archiveOnlyPackages.contains(packageName)

    fun toggleArchiveOnly(packageName: String, isChecked: Boolean) {
        if (isChecked) prefs.addArchiveOnlyPackage(packageName)
        else prefs.removeArchiveOnlyPackage(packageName)
        loadApps()
    }

    fun setQuery(query: String) {
        _query.value = query
    }
}