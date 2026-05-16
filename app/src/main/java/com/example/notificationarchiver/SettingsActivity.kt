package com.example.notificationarchiver

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.notificationarchiver.databinding.ActivitySettingsBinding
import com.google.android.material.color.DynamicColors

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var viewModel: SettingsViewModel
    private var popupWindow: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainContainer)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        binding.switchDisableDuplicateNotifications.isChecked = viewModel.preferences.disableDuplicateNotifications
        binding.switchDisableDuplicateNotifications.setOnCheckedChangeListener { _, checked ->
            viewModel.preferences.disableDuplicateNotifications = checked
        }

        binding.switchSaveImages.isChecked = viewModel.preferences.saveImages
        binding.switchSaveImages.setOnCheckedChangeListener { _, checked ->
            viewModel.preferences.saveImages = checked
        }

        binding.btnNotificationPermission.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.etMaxNotificationsPerApp.setText(viewModel.preferences.maxNotificationsPerApp.toString())
        binding.etMaxDays.setText(viewModel.preferences.maxNotificationDays.toString())

        binding.btnSaveLimits.setOnClickListener {
            val maxNotif = binding.etMaxNotificationsPerApp.text.toString().toIntOrNull()
            val maxDays = binding.etMaxDays.text.toString().toIntOrNull()
            if (maxNotif != null && maxNotif >= 0) {
                viewModel.preferences.maxNotificationsPerApp = maxNotif
            }
            if (maxDays != null && maxDays >= 0) {
                viewModel.preferences.maxNotificationDays = maxDays
                viewModel.applyDayLimit(maxDays)
            }
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        }

        binding.btnManageIgnoredApps.setOnClickListener {
            startActivity(Intent(this, IgnoredAppsActivity::class.java))
        }

        binding.btnManageArchiveOnlyApps.setOnClickListener {
            startActivity(Intent(this, ArchiveOnlyAppsActivity::class.java))
        }

        // Новый обработчик для строки выбора подтверждений
        binding.rowSkipConfirmation.setOnClickListener {
            showSkipDropdown(it)
        }

        // Обновляем текст строки при запуске
        updateSkipText()

        binding.btnDeleteAllNotifications.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удаление")
                .setMessage("Удалить все уведомления?")
                .setPositiveButton("Удалить") { _, _ ->
                    viewModel.deleteAllNotifications()
                    Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        binding.toggleThemeGroup.apply {
            when (viewModel.preferences.themeMode) {
                "light" -> check(binding.btnThemeLight.id)
                "dark"  -> check(binding.btnThemeDark.id)
                else    -> check(binding.btnThemeAuto.id)
            }
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val newMode = when (checkedId) {
                        binding.btnThemeLight.id -> "light"
                        binding.btnThemeDark.id  -> "dark"
                        else                     -> "auto"
                    }
                    viewModel.preferences.themeMode = newMode
                    applyThemeMode(newMode)
                }
            }
        }

        binding.btnDeleteAllImages.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удаление")
                .setMessage("Удалить все изображения?")
                .setPositiveButton("Удалить") { _, _ ->
                    viewModel.deleteAllImages()
                    Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        viewModel.statistics.observe(this) { stats ->
            binding.tvTotalNotifications.text = stats.totalNotifications.toString()
            binding.tvNotifications24h.text = stats.notificationsLast24h.toString()
            binding.tvNotificationsMemory.text = formatBytes(stats.textMemoryBytes)
            binding.tvImagesMemory.text = formatBytes(stats.imageMemoryBytes)
        }
        viewModel.loadStatistics()

        binding.btnTestNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                sendTestNotification()
            } else {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.btnBatteryOptimization.setOnClickListener { requestBatteryOptimization() }
        binding.textVersion.text = getAppVersion()
    }

    /**
     * Показывает выпадающий список с чекбоксами для настройки пропуска подтверждений.
     */
    private fun showSkipDropdown(anchorView: View) {
        if (popupWindow?.isShowing == true) {
            popupWindow?.dismiss()
            return
        }

        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.popup_skip_confirmation, null)

        val cbDelete = popupView.findViewById<CheckBox>(R.id.cbPopupDeleteNotifications)
        val cbIgnore = popupView.findViewById<CheckBox>(R.id.cbPopupIgnoreApps)
        val cbImages = popupView.findViewById<CheckBox>(R.id.cbPopupDeleteImages)

        // Устанавливаем текущие состояния
        cbDelete.isChecked = viewModel.preferences.skipDeleteNotifications
        cbIgnore.isChecked = viewModel.preferences.skipIgnoreApps
        cbImages.isChecked = viewModel.preferences.skipDeleteImages

        // Слушатели изменений
        val checkedChangeListener = { _: Any? ->
            viewModel.preferences.skipDeleteNotifications = cbDelete.isChecked
            viewModel.preferences.skipIgnoreApps = cbIgnore.isChecked
            viewModel.preferences.skipDeleteImages = cbImages.isChecked
            updateSkipText()
        }

        cbDelete.setOnCheckedChangeListener { _, _ -> checkedChangeListener(null) }
        cbIgnore.setOnCheckedChangeListener { _, _ -> checkedChangeListener(null) }
        cbImages.setOnCheckedChangeListener { _, _ -> checkedChangeListener(null) }

        popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 8f
            setBackgroundDrawable(null)
            showAsDropDown(anchorView, 0, 0, Gravity.END)
        }
    }

    private fun updateSkipText() {
        val prefs = viewModel.preferences
        val selected = mutableListOf<String>()

        if (prefs.skipDeleteNotifications) selected.add("Удаления уведомлений")
        if (prefs.skipIgnoreApps) selected.add("Игнора приложений")
        if (prefs.skipDeleteImages) selected.add("Удаления изображений")

        val text = when (selected.size) {
            0 -> "Ничего не выбрано"
            1 -> selected[0]
            2, 3 -> "Выбрано ${selected.size} параметра"
            else -> "Ничего не выбрано"
        }
        binding.tvSkipConfirmationValue.text = text
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var idx = 0
        while (size >= 1024.0 && idx < units.size - 1) {
            size /= 1024.0
            idx++
        }
        return "%.1f %s".format(size, units[idx])
    }

    private fun applyThemeMode(mode: String) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
            else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
            // Перезапускаем активность, чтобы тема применилась сразу
            val intent = intent
            finish()
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun sendTestNotification() {
        val channelId = "test_channel"
        val channelName = "Тестовые уведомления"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Канал для проверки работы архиватора"
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Тестовое уведомление")
            .setContentText("Это проверка работы Notification Archiver")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
            .also { notification ->
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.notify(999, notification)
            }
        Toast.makeText(this, "Тестовое уведомление отправлено", Toast.LENGTH_SHORT).show()
    }

    private fun requestBatteryOptimization() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val powerManager = getSystemService(android.os.PowerManager::class.java)
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) == true) {
                Toast.makeText(this, "Оптимизация батареи уже отключена", Toast.LENGTH_SHORT).show()
            } else {
                // Надёжный способ — открыть системные настройки батареи для всех приложений
                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        } else {
            Toast.makeText(this, "Не требуется на данном устройстве", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            "Версия ${pInfo.versionName ?: "—"} (${pInfo.versionCode})"
        } catch (e: Exception) {
            "Версия неизвестна"
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                sendTestNotification()    // Повторно вызываем отправку после получения разрешения
            } else {
                Toast.makeText(this, "Разрешение на уведомления не предоставлено", Toast.LENGTH_SHORT).show()
            }
        }
}