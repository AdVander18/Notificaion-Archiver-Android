package com.example.notificationarchiver

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: NotificationViewModel
    private lateinit var statusTextView: TextView
    private lateinit var notificationListView: ListView
    private lateinit var adapter: PackageSummaryAdapter
    private val prefs by lazy { getSharedPreferences("app_settings", MODE_PRIVATE) }

    private val notificationListenerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkNotificationListenerPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[NotificationViewModel::class.java]

        statusTextView = findViewById(R.id.statusTextView)
        notificationListView = findViewById(R.id.notificationListView)

        // Адаптер для сводки по приложениям
        adapter = PackageSummaryAdapter(this, R.layout.item_app_summary, emptyList())
        notificationListView.adapter = adapter

        viewModel.latestNotification.observe(this, Observer { _ ->
            loadPackageSummaries()
        })

        checkNotificationListenerPermission()
        loadPackageSummaries()

        if (!isNotificationListenerEnabled()) {
            showPermissionRequestDialog()
        }

        // Обработка нажатия – переход к уведомлениям приложения
        notificationListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val summary = adapter.getItem(position)
            summary?.let {
                val intent = Intent(this, NotificationHistoryActivity::class.java).apply {
                    putExtra("packageName", it.packageName)
                }
                startActivity(intent)
            }
        }

        // Долгое нажатие – меню действий
        notificationListView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val summary = adapter.getItem(position) ?: return@OnItemLongClickListener true
            val packageName = summary.packageName

            val items = arrayOf(
                "Открыть приложение",
                "Удалить изображения с уведомлений",
                "Удалить уведомления",
                "Игнорировать уведомление приложения",
                "Удалить уведомления и игнорировать приложение"
            )

            AlertDialog.Builder(this)
                .setTitle("Действия")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> openApp(packageName)
                        1 -> removeImagesForPackage(packageName)
                        2 -> deleteNotificationsForPackage(packageName)
                        3 -> ignorePackage(packageName)
                        4 -> deleteAndIgnore(packageName) // используем новый метод
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
            true
        }
    }

    // --------------- Методы с учётом настроек подтверждений ---------------

    private fun openApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeImagesForPackage(packageName: String) {
        if (prefs.getBoolean("skipDeleteImages", false)) {
            performRemoveImagesForPackage(packageName)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Удалить изображения")
                .setMessage("Удалить все изображения уведомлений для этого приложения?")
                .setPositiveButton("Удалить") { _, _ -> performRemoveImagesForPackage(packageName) }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun performRemoveImagesForPackage(packageName: String) {
        val dbHelper = NotificationDatabaseHelper.getInstance(this)
        dbHelper.removeImagesForPackage(packageName)
        loadPackageSummaries()
        Toast.makeText(this, "Изображения удалены", Toast.LENGTH_SHORT).show()
    }

    private fun deleteNotificationsForPackage(packageName: String) {
        if (prefs.getBoolean("skipDeleteNotifications", false)) {
            performDeleteNotificationsForPackage(packageName)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Удалить уведомления")
                .setMessage("Удалить все уведомления для этого приложения?")
                .setPositiveButton("Удалить") { _, _ -> performDeleteNotificationsForPackage(packageName) }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun performDeleteNotificationsForPackage(packageName: String) {
        val dbHelper = NotificationDatabaseHelper.getInstance(this)
        dbHelper.deleteNotificationsByPackage(packageName)
        loadPackageSummaries()
        Toast.makeText(this, "Уведомления удалены", Toast.LENGTH_SHORT).show()
    }

    private fun ignorePackage(packageName: String) {
        if (prefs.getBoolean("skipIgnoreApps", false)) {
            performIgnorePackage(packageName)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Игнорировать приложение")
                .setMessage("Добавить это приложение в игнор-лист? Уведомления от него больше не будут сохраняться.")
                .setPositiveButton("Игнорировать") { _, _ -> performIgnorePackage(packageName) }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun performIgnorePackage(packageName: String) {
        val ignored = prefs.getStringSet("ignored_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        ignored.add(packageName)
        prefs.edit().putStringSet("ignored_packages", ignored).apply()
        Toast.makeText(this, "Приложение добавлено в игнор-лист", Toast.LENGTH_SHORT).show()
    }

    // Комбинированное действие: удаление уведомлений + игнор
    private fun deleteAndIgnore(packageName: String) {
        val skipDelete = prefs.getBoolean("skipDeleteNotifications", false)
        val skipIgnore = prefs.getBoolean("skipIgnoreApps", false)

        if (skipDelete && skipIgnore) {
            performDeleteNotificationsForPackage(packageName)
            performIgnorePackage(packageName)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Удалить и игнорировать")
                .setMessage("Удалить все уведомления этого приложения и добавить его в игнор-лист?")
                .setPositiveButton("Да") { _, _ ->
                    performDeleteNotificationsForPackage(packageName)
                    performIgnorePackage(packageName)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    // --------------- Прочие методы (статистика, разрешения и т.д.) ---------------

    private fun loadPackageSummaries() {
        val dbHelper = NotificationDatabaseHelper.getInstance(this)
        val summaryList = dbHelper.getPackageSummaryList()
        adapter.updateData(summaryList)
    }

    private fun checkNotificationListenerPermission() {
        val isEnabled = isNotificationListenerEnabled()
        viewModel.setServiceActive(isEnabled)
        if (!isEnabled) {
            statusTextView.text = "Статус: Не активно"
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val myListener = ComponentName(this, NotificationListener::class.java).flattenToString()
        return enabledListeners?.contains(myListener) == true
    }

    private fun showPermissionRequestDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение")
            .setMessage("Для работы приложения необходимо предоставить доступ к уведомлениям. Перейти в настройки?")
            .setPositiveButton("Да") { _, _ ->
                requestNotificationListenerPermission()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun requestNotificationListenerPermission() {
        if (!isNotificationListenerEnabled()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            notificationListenerLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Доступ уже предоставлен", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}