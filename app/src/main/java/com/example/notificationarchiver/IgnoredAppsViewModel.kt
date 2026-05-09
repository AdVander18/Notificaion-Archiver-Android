package com.example.notificationarchiver

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class IgnoredAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as App).preferencesManager
    private val pm = application.packageManager

    data class AppInfo(val packageName: String, val appName: String)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    init {
        loadApps()
    }

    private fun loadApps() {
        val ignored = prefs.ignoredPackages
        val allApps = pm.getInstalledApplications(0)
            .filter { it.packageName != getApplication<App>().packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString()) }
            .sortedWith(compareBy<AppInfo> { !ignored.contains(it.packageName) }.thenBy { it.appName.lowercase() })
        _apps.value = allApps
    }

    fun isIgnored(packageName: String) = prefs.ignoredPackages.contains(packageName)

    fun toggleIgnored(packageName: String, isChecked: Boolean) {
        if (isChecked) prefs.addIgnoredPackage(packageName)
        else prefs.removeIgnoredPackage(packageName)
        // обновим список для пересортировки
        loadApps()
    }
}