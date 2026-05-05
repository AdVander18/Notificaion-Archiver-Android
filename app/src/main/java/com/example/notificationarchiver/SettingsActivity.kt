package com.example.notificationarchiver

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchDisableDuplicates: Switch

    // Элементы статистики
    private lateinit var tvNotificationsMemory: TextView
    private lateinit var tvImagesMemory: TextView
    private lateinit var tvTotalNotifications: TextView
    private lateinit var tvNotifications24h: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        switchDisableDuplicates = findViewById(R.id.switchDisableDuplicateNotifications)

        tvNotificationsMemory = findViewById(R.id.tvNotificationsMemory)
        tvImagesMemory = findViewById(R.id.tvImagesMemory)
        tvTotalNotifications = findViewById(R.id.tvTotalNotifications)
        tvNotifications24h = findViewById(R.id.tvNotifications24h)

        // Загружаем сохранённое состояние переключателя
        switchDisableDuplicates.isChecked = prefs.getBoolean("disable_duplicate_notifications", false)
        switchDisableDuplicates.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("disable_duplicate_notifications", isChecked).apply()
        }

        // Первичное заполнение статистики
        loadStatistics()
    }

    override fun onResume() {
        super.onResume()
        // При возврате на экран (например, после удаления уведомлений) обновляем статистику
        loadStatistics()
    }

    private fun loadStatistics() {
        val dbHelper = NotificationDatabaseHelper.getInstance(this)
        val stats = dbHelper.getStatistics()

        tvTotalNotifications.text = formatCount(stats.totalNotifications)
        tvNotifications24h.text = formatCount(stats.notificationsLast24h)
        tvNotificationsMemory.text = formatBytes(stats.textMemoryBytes)
        tvImagesMemory.text = formatBytes(stats.imageMemoryBytes)
    }

    private fun formatCount(count: Int): String {
        return count.toString()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024.0 && unitIndex < units.size - 1) {
            size /= 1024.0
            unitIndex++
        }
        return "%.1f %s".format(size, units[unitIndex])
    }
}