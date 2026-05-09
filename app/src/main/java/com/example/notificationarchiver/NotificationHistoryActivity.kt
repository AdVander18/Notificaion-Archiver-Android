package com.example.notificationarchiver

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notificationarchiver.databinding.ActivityNotificationHistoryBinding
import com.google.android.material.color.DynamicColors

class NotificationHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationHistoryBinding
    private lateinit var viewModel: NotificationHistoryViewModel
    private lateinit var adapter: NotificationAdapter
    private var packageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivitiesIfAvailable(application)
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainContainer)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this)[NotificationHistoryViewModel::class.java]
        packageName = intent.getStringExtra("packageName")

        val appName = packageName?.let { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) { pkg }
        } ?: "История уведомлений"
        // Используем Toolbar вместо отдельного TextView
        binding.toolbar.title = "Уведомления от $appName"

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(
            this,
            R.layout.item_notification,
            emptyList(),
            onItemLongClick = { entry ->
                showNotificationMenu(entry)
                true
            }
        )
        binding.historyRecyclerView.adapter = adapter

        viewModel.notifications.observe(this) { list ->
            adapter.updateData(list)
        }
        viewModel.loadNotifications(packageName)

        if (packageName != null) {
            binding.openAppFab.show()
            binding.openAppFab.setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(packageName!!)
                if (intent != null) startActivity(intent)
                else Toast.makeText(this, "Не удалось открыть", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.openAppFab.hide()
        }
    }
    private fun showNotificationMenu(entry: NotificationDatabaseHelper.NotificationEntry) {
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (!entry.title.isNullOrEmpty() || !entry.text.isNullOrEmpty()) {
            items.add("Копировать текст")
            actions.add { /* реализация копирования */ }
        }
        if (entry.image != null && entry.image.isNotEmpty()) {
            items.add("Копировать изображение")
            actions.add { /* реализация копирования */ }
            items.add("Удалить изображение")
            actions.add {
                ConfirmationHelper.confirmIfNeeded(this, viewModel.preferences.skipDeleteImages,
                    "Удалить изображение", "Удалить изображение из уведомления?") {
                    viewModel.removeImage(entry.id)
                    viewModel.loadNotifications(packageName)
                    Toast.makeText(this, "Изображение удалено", Toast.LENGTH_SHORT).show()
                }
            }
        }
        items.add("Удалить уведомление")
        actions.add {
            ConfirmationHelper.confirmIfNeeded(this, viewModel.preferences.skipDeleteNotifications,
                "Удалить уведомление", "Удалить это уведомление?") {
                viewModel.deleteNotification(entry.id)
                viewModel.loadNotifications(packageName)
                Toast.makeText(this, "Уведомление удалено", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Действия с уведомлением")
            .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
            .setNegativeButton("Отмена", null)
            .show()
    }
}