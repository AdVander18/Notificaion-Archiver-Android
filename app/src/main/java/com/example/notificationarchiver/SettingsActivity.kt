package com.example.notificationarchiver

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchDisableDuplicates: Switch
    private lateinit var btnDeleteAllNotifications: Button
    private lateinit var btnDeleteAllImages: Button
    private lateinit var btnManageIgnoredApps: Button
    private lateinit var btnSkipConfirmationSettings: Button
    private lateinit var etMaxNotificationsPerApp: EditText
    private lateinit var btnSaveMaxNotificationsPerApp: Button
    private lateinit var etMaxDays: EditText
    private lateinit var btnSaveMaxDays: Button
    private lateinit var tvNotificationsMemory: TextView
    private lateinit var tvImagesMemory: TextView
    private lateinit var tvTotalNotifications: TextView
    private lateinit var tvNotifications24h: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        switchDisableDuplicates = findViewById(R.id.switchDisableDuplicateNotifications)
        btnDeleteAllNotifications = findViewById(R.id.btnDeleteAllNotifications)
        btnDeleteAllImages = findViewById(R.id.btnDeleteAllImages)
        btnSkipConfirmationSettings = findViewById(R.id.btnSkipConfirmationSettings)

        // Лимиты
        etMaxNotificationsPerApp = findViewById(R.id.etMaxNotificationsPerApp)
        btnSaveMaxNotificationsPerApp = findViewById(R.id.btnSaveMaxNotificationsPerApp)
        etMaxDays = findViewById(R.id.etMaxDays)
        btnSaveMaxDays = findViewById(R.id.btnSaveMaxDays)

        tvNotificationsMemory = findViewById(R.id.tvNotificationsMemory)
        tvImagesMemory = findViewById(R.id.tvImagesMemory)
        tvTotalNotifications = findViewById(R.id.tvTotalNotifications)
        tvNotifications24h = findViewById(R.id.tvNotifications24h)
        btnManageIgnoredApps = findViewById(R.id.btnManageIgnoredApps)
        btnManageIgnoredApps.setOnClickListener {
            startActivity(Intent(this, IgnoredAppsActivity::class.java))
        }

        switchDisableDuplicates.isChecked = prefs.getBoolean("disable_duplicate_notifications", false)
        switchDisableDuplicates.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("disable_duplicate_notifications", isChecked).apply()
        }

        // Загрузка лимитов
        val currentMax = prefs.getInt("max_notifications_per_app", 100)
        etMaxNotificationsPerApp.setText(currentMax.toString())
        val currentDays = prefs.getInt("max_notification_days", 365)
        etMaxDays.setText(currentDays.toString())

        // Сохранение лимита количества
        btnSaveMaxNotificationsPerApp.setOnClickListener {
            val maxStr = etMaxNotificationsPerApp.text.toString()
            if (maxStr.isNotEmpty()) {
                val max = maxStr.toIntOrNull()
                if (max != null && max >= 0) {
                    prefs.edit().putInt("max_notifications_per_app", max).apply()
                    Toast.makeText(this, "Лимит сохранён: $max", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Введите число >= 0", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Введите число", Toast.LENGTH_SHORT).show()
            }
        }
        // Сохранение лимита дней
        btnSaveMaxDays.setOnClickListener {
            val daysStr = etMaxDays.text.toString()
            if (daysStr.isNotEmpty()) {
                val days = daysStr.toIntOrNull()
                if (days != null && days >= 0) {
                    prefs.edit().putInt("max_notification_days", days).apply()
                    Toast.makeText(this, "Лимит дней сохранён: $days", Toast.LENGTH_SHORT).show()
                    // Опционально: сразу применить удаление старых записей
                    val dbHelper = NotificationDatabaseHelper.getInstance(this)
                    dbHelper.writableDatabase.use { db ->
                        db.execSQL("DELETE FROM notifications WHERE timestamp < ?",
                            arrayOf((System.currentTimeMillis() - days * 24 * 60 * 60 * 1000).toString()))
                    }
                    loadStatistics()
                } else {
                    Toast.makeText(this, "Введите число >= 0", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Введите число дней", Toast.LENGTH_SHORT).show()
            }
        }

        btnSkipConfirmationSettings.setOnClickListener {
            showSkipConfirmationDialog()
        }

        btnDeleteAllNotifications.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удаление уведомлений")
                .setMessage("Вы уверены, что хотите удалить ВСЕ сохранённые уведомления?")
                .setPositiveButton("Удалить") { _, _ ->
                    val dbHelper = NotificationDatabaseHelper.getInstance(this)
                    dbHelper.deleteAllNotifications()
                    loadStatistics()
                    Toast.makeText(this, "Все уведомления удалены", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        btnDeleteAllImages.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удаление изображений")
                .setMessage("Вы действительно хотите удалить ВСЕ сохранённые изображения из уведомлений?")
                .setPositiveButton("Удалить") { _, _ ->
                    val dbHelper = NotificationDatabaseHelper.getInstance(this)
                    dbHelper.removeAllImages()
                    loadStatistics()
                    Toast.makeText(this, "Все изображения удалены", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        loadStatistics()
    }

    private fun showSkipConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_skip_confirmation, null)
        val cbSkipDelete = dialogView.findViewById<CheckBox>(R.id.cbSkipDeleteNotifications)
        val cbSkipIgnore = dialogView.findViewById<CheckBox>(R.id.cbSkipIgnoreApps)
        val cbSkipImages = dialogView.findViewById<CheckBox>(R.id.cbSkipDeleteImages)

        // Устанавливаем текущие сохранённые значения
        cbSkipDelete.isChecked = prefs.getBoolean("skipDeleteNotifications", false)
        cbSkipIgnore.isChecked = prefs.getBoolean("skipIgnoreApps", false)
        cbSkipImages.isChecked = prefs.getBoolean("skipDeleteImages", false)

        // Немедленное сохранение при переключении чекбоксов
        val listener = { _: Any? ->
            prefs.edit()
                .putBoolean("skipDeleteNotifications", cbSkipDelete.isChecked)
                .putBoolean("skipIgnoreApps", cbSkipIgnore.isChecked)
                .putBoolean("skipDeleteImages", cbSkipImages.isChecked)
                .apply()
        }
        cbSkipDelete.setOnCheckedChangeListener { _, _ -> listener(null) }
        cbSkipIgnore.setOnCheckedChangeListener { _, _ -> listener(null) }
        cbSkipImages.setOnCheckedChangeListener { _, _ -> listener(null) }

        AlertDialog.Builder(this)
            .setTitle("Не показывать подтверждение для")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)   // просто закрываем, настройки уже сохранены
            .show()
    }

    override fun onResume() {
        super.onResume()
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

    private fun formatCount(count: Int): String = count.toString()

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