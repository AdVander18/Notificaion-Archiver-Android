package com.example.notificationarchiver

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
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
        applySavedTheme()

        // Применяем динамические цвета один раз для всего приложения
        DynamicColors.applyToActivitiesIfAvailable(this)

        database = NotificationDatabaseHelper(this)
        repository = NotificationRepository(database, preferencesManager)
    }

    private fun applySavedTheme() {
        val mode = when (preferencesManager.themeMode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
            else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}