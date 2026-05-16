package com.example.notificationarchiver

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var disableDuplicateNotifications: Boolean
        get() = prefs.getBoolean("disable_duplicate_notifications", false)
        set(value) = prefs.edit().putBoolean("disable_duplicate_notifications", value).apply()

    var maxNotificationsPerApp: Int
        get() = prefs.getInt("max_notifications_per_app", 100)
        set(value) = prefs.edit().putInt("max_notifications_per_app", value).apply()

    var maxNotificationDays: Int
        get() = prefs.getInt("max_notification_days", 365)
        set(value) = prefs.edit().putInt("max_notification_days", value).apply()

    val ignoredPackages: Set<String>
        get() = prefs.getStringSet("ignored_packages", emptySet()) ?: emptySet()

    fun addIgnoredPackage(packageName: String) {
        val set = ignoredPackages.toMutableSet()
        set.add(packageName)
        prefs.edit().putStringSet("ignored_packages", set).apply()
    }

    fun removeIgnoredPackage(packageName: String) {
        val set = ignoredPackages.toMutableSet()
        set.remove(packageName)
        prefs.edit().putStringSet("ignored_packages", set).apply()
    }

    // пропуск подтверждений
    var skipDeleteNotifications: Boolean
        get() = prefs.getBoolean("skipDeleteNotifications", false)
        set(value) = prefs.edit().putBoolean("skipDeleteNotifications", value).apply()

    var skipIgnoreApps: Boolean
        get() = prefs.getBoolean("skipIgnoreApps", false)
        set(value) = prefs.edit().putBoolean("skipIgnoreApps", value).apply()

    var skipDeleteImages: Boolean
        get() = prefs.getBoolean("skipDeleteImages", false)
        set(value) = prefs.edit().putBoolean("skipDeleteImages", value).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", "auto") ?: "auto"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    // Приложения, уведомления которых сохраняются, но удаляются из статус-бара
    val archiveOnlyPackages: Set<String>
        get() = prefs.getStringSet("archive_only_packages", emptySet()) ?: emptySet()

    fun addArchiveOnlyPackage(packageName: String) {
        val set = archiveOnlyPackages.toMutableSet()
        set.add(packageName)
        prefs.edit().putStringSet("archive_only_packages", set).apply()
    }

    fun removeArchiveOnlyPackage(packageName: String) {
        val set = archiveOnlyPackages.toMutableSet()
        set.remove(packageName)
        prefs.edit().putStringSet("archive_only_packages", set).apply()
    }

    var saveImages: Boolean
        get() = prefs.getBoolean("save_images", true)
        set(value) = prefs.edit().putBoolean("save_images", value).apply()
}