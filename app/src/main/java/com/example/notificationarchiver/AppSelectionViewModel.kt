package com.example.notificationarchiver

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppSelectionType { IGNORE_APPS, IGNORE_IMAGES, ARCHIVE_ONLY }

class AppSelectionViewModel(
    application: Application,
    private val selectionType: AppSelectionType
) : AndroidViewModel(application) {

    companion object {
        fun factory(application: Application, type: AppSelectionType) = Factory(application, type)
    }
    private val prefs = (application as App).preferencesManager
    private val pm = application.packageManager

    data class AppInfo(val packageName: String, val appName: String)

    private val _apps = MutableLiveData<List<AppInfo>>()
    private val _query = MutableLiveData("")

    val filteredApps = MediatorLiveData<List<AppInfo>>().apply {
        addSource(_apps) { recomputeFilter() }
        addSource(_query) { recomputeFilter() }
    }

    private fun recomputeFilter() {
        val appsList = _apps.value ?: return
        val query = _query.value ?: ""
        filteredApps.value = if (query.isBlank()) appsList
        else appsList.filter { it.appName.contains(query, ignoreCase = true) }
    }

    init {
        loadAppsAsync()
    }

    private fun loadAppsAsync() {
        viewModelScope.launch {
            val appList = withContext(Dispatchers.IO) { fetchApps() }
            _apps.value = appList
        }
    }

    private fun fetchApps(): List<AppInfo> {
        val selectedSet = when (selectionType) {
            AppSelectionType.IGNORE_APPS -> prefs.ignoredPackages
            AppSelectionType.IGNORE_IMAGES -> prefs.ignoredImagePackages
            AppSelectionType.ARCHIVE_ONLY -> prefs.archiveOnlyPackages
        }
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != getApplication<App>().packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString()) }
            .sortedWith(
                compareByDescending<AppInfo> { selectedSet.contains(it.packageName) }
                    .thenBy { it.appName.lowercase() }
            )
    }

    fun isPackageSelected(packageName: String): Boolean {
        val set = when (selectionType) {
            AppSelectionType.IGNORE_APPS -> prefs.ignoredPackages
            AppSelectionType.IGNORE_IMAGES -> prefs.ignoredImagePackages
            AppSelectionType.ARCHIVE_ONLY -> prefs.archiveOnlyPackages
        }
        return set.contains(packageName)
    }

    fun togglePackage(packageName: String, checked: Boolean) {
        when (selectionType) {
            AppSelectionType.IGNORE_APPS -> if (checked) prefs.addIgnoredPackage(packageName)
            else prefs.removeIgnoredPackage(packageName)
            AppSelectionType.IGNORE_IMAGES -> if (checked) prefs.addIgnoredImagePackage(packageName)
            else prefs.removeIgnoredImagePackage(packageName)
            AppSelectionType.ARCHIVE_ONLY -> if (checked) prefs.addArchiveOnlyPackage(packageName)
            else prefs.removeArchiveOnlyPackage(packageName)
        }
        _apps.value = fetchApps()
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    class Factory(private val application: Application, private val selectionType: AppSelectionType) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AppSelectionViewModel(application, selectionType) as T
        }
    }
}

// Для удобства создания фабрики
fun AppSelectionViewModel.Companion.factory(application: Application, type: AppSelectionType) =
    AppSelectionViewModel.Factory(application, type)