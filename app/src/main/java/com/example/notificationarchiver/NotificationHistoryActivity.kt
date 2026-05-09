package com.example.notificationarchiver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream

class NotificationHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private lateinit var listView: ListView
    private var packageName: String? = null
    private val prefs by lazy { getSharedPreferences("app_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_history)

        listView = findViewById(R.id.historyListView)
        val titleView = findViewById<TextView>(R.id.historyTitle)
        val openAppFab = findViewById<FloatingActionButton>(R.id.openAppFab)

        packageName = intent.getStringExtra("packageName")
        val dbHelper = NotificationDatabaseHelper.getInstance(this)

        val notifications = getNotifications(dbHelper)

        titleView.text = if (packageName != null) {
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName!!, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName!!
            }
            "Уведомления от $appName"
        } else {
            "История уведомлений"
        }

        adapter = NotificationAdapter(this, R.layout.item_notification, notifications)
        listView.adapter = adapter

        // Долгое нажатие — меню действий (копирование / удаление)
        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position) ?: return@OnItemLongClickListener true

            val items = mutableListOf<String>()
            val actions = mutableListOf<() -> Unit>()

            val hasText = !entry.title.isNullOrEmpty() || !entry.text.isNullOrEmpty()
            val hasImage = entry.image != null && entry.image.isNotEmpty()

            // Копирование текста / изображения
            if (hasText) {
                items.add("Копировать текст")
                actions.add {
                    // ... (без изменений)
                }
            } else if (hasImage) {
                items.add("Копировать изображение")
                actions.add {
                    // ... (без изменений)
                }
            }

            // --- НОВОЕ: удаление изображения (только для одного приложения) ---
            if (hasImage && packageName != null) {
                items.add("Удалить изображение")
                actions.add {
                    if (prefs.getBoolean("skipDeleteImages", false)) {
                        // Удаление без подтверждения
                        dbHelper.removeImageForNotification(entry.id)
                        refreshNotifications(dbHelper)
                        Toast.makeText(this@NotificationHistoryActivity, "Изображение удалено", Toast.LENGTH_SHORT).show()
                    } else {
                        // С подтверждением
                        AlertDialog.Builder(this@NotificationHistoryActivity)
                            .setTitle("Удалить изображение?")
                            .setMessage("Удалить изображение из этого уведомления?")
                            .setPositiveButton("Удалить") { _, _ ->
                                dbHelper.removeImageForNotification(entry.id)
                                refreshNotifications(dbHelper)
                                Toast.makeText(this@NotificationHistoryActivity, "Изображение удалено", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Отмена", null)
                            .show()
                    }
                }
            }

            // Удаление уведомления целиком
            items.add("Удалить уведомление")
            actions.add {
                if (prefs.getBoolean("skipDeleteNotifications", false)) {
                    dbHelper.deleteNotification(entry.id)
                    refreshNotifications(dbHelper)
                    Toast.makeText(this@NotificationHistoryActivity, "Уведомление удалено", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this@NotificationHistoryActivity)
                        .setTitle("Удалить уведомление?")
                        .setMessage("Вы действительно хотите удалить это уведомление?")
                        .setPositiveButton("Удалить") { _, _ ->
                            dbHelper.deleteNotification(entry.id)
                            refreshNotifications(dbHelper)
                            Toast.makeText(this@NotificationHistoryActivity, "Уведомление удалено", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
            }

            AlertDialog.Builder(this@NotificationHistoryActivity)
                .setTitle("Действия с уведомлением")
                .setItems(items.toTypedArray()) { _, which ->
                    actions[which].invoke()
                }
                .setNegativeButton("Отмена", null)
                .show()

            true
        }

        // FAB открытия приложения
        if (packageName != null) {
            openAppFab.show()
            openAppFab.setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName!!)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "Невозможно открыть приложение", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            openAppFab.hide()
        }
    }

    private fun getNotifications(dbHelper: NotificationDatabaseHelper): List<NotificationDatabaseHelper.NotificationEntry> {
        return if (packageName != null) {
            dbHelper.getNotificationsByPackage(packageName!!)
        } else {
            dbHelper.getAllNotifications()
        }
    }

    private fun refreshNotifications(dbHelper: NotificationDatabaseHelper) {
        val newList = getNotifications(dbHelper)
        adapter.updateData(newList)
    }
}