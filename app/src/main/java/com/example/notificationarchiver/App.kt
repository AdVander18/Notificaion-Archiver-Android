package com.example.notificationarchiver

import android.app.Application

class App : Application() {
    lateinit var database: NotificationDatabaseHelper
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var repository: NotificationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = NotificationDatabaseHelper(this)
        preferencesManager = PreferencesManager(this)
        repository = NotificationRepository(database, preferencesManager)
    }
}