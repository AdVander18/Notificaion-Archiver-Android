package com.example.notificationarchiver

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NotificationHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private lateinit var listView: ListView
    private var packageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_history)

        listView = findViewById(R.id.historyListView)
        val titleView = findViewById<TextView>(R.id.historyTitle)
        val openAppFab = findViewById<FloatingActionButton>(R.id.openAppFab)

        packageName = intent.getStringExtra("packageName")
        val dbHelper = NotificationDatabaseHelper.getInstance(this)

        // Загрузка данных
        val notifications = getNotifications(dbHelper)

        // Установка заголовка
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

        // Долгое нажатие — удаление
        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position)
            entry?.let {
                AlertDialog.Builder(this)
                    .setTitle("Удалить уведомление?")
                    .setMessage("Вы действительно хотите удалить это уведомление?")
                    .setPositiveButton("Удалить") { _, _ ->
                        dbHelper.deleteNotification(it.id)
                        refreshNotifications(dbHelper)
                        Toast.makeText(this, "Уведомление удалено", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
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