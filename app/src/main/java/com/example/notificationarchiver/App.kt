package com.example.notificationarchiver

import android.app.Application
import com.google.android.material.color.DynamicColors

class App : Application() {
    lateinit var database: NotificationDatabaseHelper
        private set
    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var repository: NotificationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        ThemeHelper.applyTheme(preferencesManager.themeMode)
        DynamicColors.applyToActivitiesIfAvailable(this)
        database = NotificationDatabaseHelper(this)
        repository = NotificationRepository(database, preferencesManager)
    }
}