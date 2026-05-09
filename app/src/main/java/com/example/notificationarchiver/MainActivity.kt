package com.example.notificationarchiver

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notificationarchiver.databinding.ActivityMainBinding
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: PackageSummaryAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkNotificationPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivitiesIfAvailable(application)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainContainer)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Инициализация RecyclerView
        binding.notificationRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PackageSummaryAdapter(
            this,
            R.layout.item_app_summary,
            emptyList(),
            onItemClick = { summary ->
                startActivity(Intent(this, NotificationHistoryActivity::class.java).apply {
                    putExtra("packageName", summary.packageName)
                })
            },
            onItemLongClick = { summary ->
                showContextMenu(summary.packageName)
                true
            }
        )
        binding.notificationRecyclerView.adapter = adapter

        viewModel.packageSummaries.observe(this) { summaries ->
            adapter.updateData(summaries)
        }

        viewModel.latestNotification.observe(this) { _ ->
            viewModel.loadPackageSummaries()
        }

        checkNotificationPermission()
        if (!isNotificationListenerEnabled()) {
            showPermissionRequestDialog()
        }
    }

    private fun showContextMenu(packageName: String) {
        val items = arrayOf("Открыть приложение", "Удалить изображения",
            "Удалить уведомления", "Игнорировать", "Удалить и игнорировать")
        AlertDialog.Builder(this)
            .setTitle("Действия")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openApp(packageName)
                    1 -> removeImages(packageName)
                    2 -> deleteNotifications(packageName)
                    3 -> ignore(packageName)
                    4 -> deleteAndIgnore(packageName)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) startActivity(intent)
        else Toast.makeText(this, "Не удалось открыть", Toast.LENGTH_SHORT).show()
    }

    private fun removeImages(pkg: String) {
        ConfirmationHelper.confirmIfNeeded(this, viewModel.preferences.skipDeleteImages,
            "Удалить изображения", "Удалить все изображения уведомлений для этого приложения?") {
            (application as App).repository.removeImagesForPackage(pkg)
            viewModel.loadPackageSummaries()
            Toast.makeText(this, "Изображения удалены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteNotifications(pkg: String) {
        ConfirmationHelper.confirmIfNeeded(this, viewModel.preferences.skipDeleteNotifications,
            "Удалить уведомления", "Удалить все уведомления для этого приложения?") {
            viewModel.deleteNotificationsByPackage(pkg)
            Toast.makeText(this, "Уведомления удалены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ignore(pkg: String) {
        ConfirmationHelper.confirmIfNeeded(this, viewModel.preferences.skipIgnoreApps,
            "Игнорировать", "Добавить приложение в игнор-лист?") {
            viewModel.ignorePackage(pkg)
            Toast.makeText(this, "Добавлено в игнор-лист", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAndIgnore(pkg: String) {
        val skipDelete = viewModel.preferences.skipDeleteNotifications
        val skipIgnore = viewModel.preferences.skipIgnoreApps
        if (skipDelete && skipIgnore) {
            viewModel.deleteNotificationsByPackage(pkg)
            viewModel.ignorePackage(pkg)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Удалить и игнорировать")
                .setMessage("Удалить уведомления и добавить в игнор-лист?")
                .setPositiveButton("Да") { _, _ ->
                    viewModel.deleteNotificationsByPackage(pkg)
                    viewModel.ignorePackage(pkg)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun checkNotificationPermission() {
        val enabled = isNotificationListenerEnabled()
        viewModel.isServiceActive.observe(this) {}
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = ComponentName(this, NotificationListener::class.java).flattenToString()
        return Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(flat) == true
    }

    private fun showPermissionRequestDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение")
            .setMessage("Предоставьте доступ к уведомлениям в настройках.")
            .setPositiveButton("Перейти") { _, _ ->
                notificationPermissionLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}