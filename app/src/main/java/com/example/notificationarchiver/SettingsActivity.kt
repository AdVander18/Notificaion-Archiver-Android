package com.example.notificationarchiver

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.notificationarchiver.databinding.ActivitySettingsBinding
import com.google.android.material.color.DynamicColors

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivitiesIfAvailable(application)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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

        binding.etMaxNotificationsPerApp.setText(viewModel.preferences.maxNotificationsPerApp.toString())
        binding.etMaxDays.setText(viewModel.preferences.maxNotificationDays.toString())

        // Единая кнопка сохранения лимитов
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

        // Кнопка "Не показывать подтверждение" теперь с правильным ID
        binding.btnSkipConfirmation.setOnClickListener {
            showSkipDialog()
        }

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
    }

    private fun showSkipDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_skip_confirmation, null)
        val cbDelete = dialogView.findViewById<CheckBox>(R.id.cbSkipDeleteNotifications)
        val cbIgnore = dialogView.findViewById<CheckBox>(R.id.cbSkipIgnoreApps)
        val cbImages = dialogView.findViewById<CheckBox>(R.id.cbSkipDeleteImages)

        cbDelete.isChecked = viewModel.preferences.skipDeleteNotifications
        cbIgnore.isChecked = viewModel.preferences.skipIgnoreApps
        cbImages.isChecked = viewModel.preferences.skipDeleteImages

        val listener = { _: Any? ->
            viewModel.preferences.skipDeleteNotifications = cbDelete.isChecked
            viewModel.preferences.skipIgnoreApps = cbIgnore.isChecked
            viewModel.preferences.skipDeleteImages = cbImages.isChecked
        }
        cbDelete.setOnCheckedChangeListener { _, _ -> listener(null) }
        cbIgnore.setOnCheckedChangeListener { _, _ -> listener(null) }
        cbImages.setOnCheckedChangeListener { _, _ -> listener(null) }

        AlertDialog.Builder(this)
            .setTitle("Не показывать подтверждение")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .show()
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
}