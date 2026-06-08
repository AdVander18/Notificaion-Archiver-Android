package com.example.notificationarchiver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.notificationarchiver.databinding.ActivitySettingsBinding
import com.google.android.material.button.MaterialButtonToggleGroup

class SettingsFragment : Fragment() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SettingsViewModel
    private var popupWindow: PopupWindow? = null

    private val themeToggleListener =
        MaterialButtonToggleGroup.OnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val newMode = when (checkedId) {
                    binding.btnThemeLight.id -> "light"
                    binding.btnThemeDark.id  -> "dark"
                    else                     -> "auto"
                }
                if (newMode == viewModel.preferences.themeMode) return@OnButtonCheckedListener

                viewModel.preferences.themeMode = newMode
                requireContext().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                    .edit()
                    .putString("theme_mode", newMode)
                    .commit()
                AppCompatDelegate.setDefaultNightMode(ThemeHelper.nightModeFromString(newMode))
                requireActivity().recreate()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivitySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[SettingsViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener {
            (activity as? MainActivity)?.closeSettingsPanel()
        }

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
            Toast.makeText(requireContext(), "Сохранено", Toast.LENGTH_SHORT).show()
        }

        binding.btnManageIgnoredApps.setOnClickListener {
            startActivity(Intent(requireContext(), AppSelectionActivity::class.java)
                .putExtra(AppSelectionActivity.EXTRA_SELECTION_TYPE, AppSelectionType.IGNORE_APPS.name))
        }
        binding.btnManageIgnoredImages.setOnClickListener {
            startActivity(Intent(requireContext(), AppSelectionActivity::class.java)
                .putExtra(AppSelectionActivity.EXTRA_SELECTION_TYPE, AppSelectionType.IGNORE_IMAGES.name))
        }
        binding.btnManageArchiveOnlyApps.setOnClickListener {
            startActivity(Intent(requireContext(), AppSelectionActivity::class.java)
                .putExtra(AppSelectionActivity.EXTRA_SELECTION_TYPE, AppSelectionType.ARCHIVE_ONLY.name))
        }

        binding.rowSkipConfirmation.setOnClickListener { showSkipDropdown(it) }
        updateSkipText()

        binding.btnDeleteAllNotifications.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Удаление")
                .setMessage("Удалить все уведомления?")
                .setPositiveButton("Удалить") { _, _ ->
                    viewModel.deleteAllNotifications()
                    Toast.makeText(requireContext(), "Удалено", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        binding.btnDeleteAllImages.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Удаление")
                .setMessage("Удалить все изображения?")
                .setPositiveButton("Удалить") { _, _ ->
                    viewModel.deleteAllImages()
                    Toast.makeText(requireContext(), "Удалено", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        viewModel.statistics.observe(viewLifecycleOwner) { stats ->
            binding.tvTotalNotifications.text = stats.totalNotifications.toString()
            binding.tvNotifications24h.text = stats.notificationsLast24h.toString()
            binding.tvNotificationsMemory.text = formatBytes(stats.textMemoryBytes)
            binding.tvImagesMemory.text = formatBytes(stats.imageMemoryBytes)
        }
        viewModel.loadStatistics()

        binding.btnTestNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                sendTestNotification()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.btnBatteryOptimization.setOnClickListener { requestBatteryOptimization() }
        binding.textVersion.text = getAppVersion()
        val currentMode = viewModel.preferences.themeMode
        binding.toggleThemeGroup.removeOnButtonCheckedListener(themeToggleListener)
        when (currentMode) {
            "light" -> binding.toggleThemeGroup.check(binding.btnThemeLight.id)
            "dark"  -> binding.toggleThemeGroup.check(binding.btnThemeDark.id)
            else    -> binding.toggleThemeGroup.check(binding.btnThemeAuto.id)
        }
        binding.toggleThemeGroup.addOnButtonCheckedListener(themeToggleListener)
    }

    private fun showSkipDropdown(anchorView: View) {
        if (popupWindow?.isShowing == true) {
            popupWindow?.dismiss()
            return
        }
        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.popup_skip_confirmation, null)

        val cbDelete = popupView.findViewById<CheckBox>(R.id.cbPopupDeleteNotifications)
        val cbIgnore = popupView.findViewById<CheckBox>(R.id.cbPopupIgnoreApps)
        val cbImages = popupView.findViewById<CheckBox>(R.id.cbPopupDeleteImages)
        val cbIgnoreImages = popupView.findViewById<CheckBox>(R.id.cbPopupIgnoreImages)

        cbDelete.isChecked = viewModel.preferences.skipDeleteNotifications
        cbIgnore.isChecked = viewModel.preferences.skipIgnoreApps
        cbImages.isChecked = viewModel.preferences.skipDeleteImages
        cbIgnoreImages.isChecked = viewModel.preferences.skipIgnoreImages

        val checkedChangeListener = { _: Any? ->
            viewModel.preferences.skipDeleteNotifications = cbDelete.isChecked
            viewModel.preferences.skipIgnoreApps = cbIgnore.isChecked
            viewModel.preferences.skipDeleteImages = cbImages.isChecked
            viewModel.preferences.skipIgnoreImages = cbIgnoreImages.isChecked
            updateSkipText()
        }
        cbDelete.setOnCheckedChangeListener { _, _ -> checkedChangeListener(null) }
        cbIgnore.setOnCheckedChangeListener { _, _ -> checkedChangeListener(null) }
        cbImages.setOnCheckedChangeListener { _, _ -> checkedChangeListener(null) }
        cbIgnoreImages.setOnCheckedChangeListener { _, _ -> checkedChangeListener(null) }

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
        if (prefs.skipIgnoreImages) selected.add("Игнора изображений")

        val text = when (selected.size) {
            0 -> "Ничего не выбрано"
            1 -> selected[0]
            else -> "Выбрано ${selected.size} параметра"
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

    private fun sendTestNotification() {
        val channelId = "test_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Тестовые уведомления",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Канал для проверки" }
            val manager = requireContext().getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.testnotification)
        val notification = androidx.core.app.NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Неправильный ответ не означает бессмысленности.")
            .setContentText("Эта фраза утверждает, что ошибочный ответ может сохранять смысловую ценность, " +
                    "отражая определённую логику, этап познания или нестандартный взгляд на проблему. " +
                    "Она подчёркивает, что правильность и осмысленность не тождественны, и даже заблуждение способно стимулировать дальнейший поиск истины.")
            .setLargeIcon(largeIcon)
            .setStyle(
                androidx.core.app.NotificationCompat.BigPictureStyle()
                    .bigPicture(largeIcon)
                    .bigLargeIcon(null as android.graphics.Bitmap?)
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val manager = requireContext().getSystemService(android.app.NotificationManager::class.java)
        manager.notify(999, notification)
        Toast.makeText(requireContext(), "Тестовое уведомление отправлено", Toast.LENGTH_SHORT).show()
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = requireContext().getSystemService(android.os.PowerManager::class.java)
            if (powerManager?.isIgnoringBatteryOptimizations(requireContext().packageName) == true) {
                Toast.makeText(requireContext(), "Оптимизация батареи уже отключена", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } else {
            Toast.makeText(requireContext(), "Не требуется", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            "Версия ${pInfo.versionName ?: "—"} (${pInfo.versionCode})"
        } catch (e: Exception) {
            "Версия неизвестна"
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) sendTestNotification()
            else Toast.makeText(requireContext(), "Разрешение на уведомления не предоставлено", Toast.LENGTH_SHORT).show()
        }

    override fun onDestroyView() {
        super.onDestroyView()
        popupWindow?.dismiss()
        _binding = null
    }
}