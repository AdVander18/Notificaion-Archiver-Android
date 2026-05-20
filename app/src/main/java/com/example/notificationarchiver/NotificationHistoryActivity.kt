package com.example.notificationarchiver

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import java.io.File

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
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

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
            },
            onItemClick = { entry ->
                // Открываем приложение-источник только если это история одного приложения
                if (packageName != null) {
                    val intent = packageManager.getLaunchIntentForPackage(entry.packageName)
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show()
                    }
                }
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
            actions.add {
                // Получаем массив байтов из entry.image
                val imageBytes = entry.image
                // Преобразуем в Bitmap
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                // Сохраняем во временный файл
                val cacheDir = File(cacheDir, "shared_images")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val file = File(cacheDir, "notification_image_${System.currentTimeMillis()}.png")
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                // Получаем URI через FileProvider (authority должен совпадать с манифестом)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                // Помещаем URI в системный буфер обмена
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newUri(contentResolver, "image", uri)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Изображение скопировано", Toast.LENGTH_SHORT).show()
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