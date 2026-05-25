package com.example.notificationarchiver

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.notificationarchiver.databinding.ActivitySettingsBinding
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.hypot

class SettingsFragment : Fragment() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SettingsViewModel
    private var popupWindow: PopupWindow? = null

    private var isThemeAnimating = false
    private var themeChangeAnimator: ValueAnimator? = null
    private var pendingMode: String? = null
    private var pendingCheckedId: Int? = null

    private val themeToggleListener =
        MaterialButtonToggleGroup.OnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked && !isThemeAnimating) {
                val newMode = when (checkedId) {
                    binding.btnThemeLight.id -> "light"
                    binding.btnThemeDark.id -> "dark"
                    else -> "auto"
                }
                if (newMode == viewModel.preferences.themeMode) return@OnButtonCheckedListener

                // Определяем целевой цвет оверлея
                val overlayColor = when (newMode) {
                    "dark"  -> android.graphics.Color.BLACK
                    "light" -> android.graphics.Color.WHITE
                    else    -> {
                        val night = resources.configuration.uiMode and
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                            android.graphics.Color.BLACK
                        else
                            android.graphics.Color.WHITE
                    }
                }

                isThemeAnimating = true

                // 1. Показываем сплошной оверлей нужного цвета
                ThemeOverlaySimplified.show(requireContext(), overlayColor)

                // 2. Применяем тему и пересоздаём активити
                viewModel.preferences.themeMode = newMode
                AppCompatDelegate.setDefaultNightMode(nightModeFromString(newMode))
                requireActivity().intent.putExtra("open_settings_after_recreate", true)
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

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable {
        if (ThemeOverlaySimplified.isTransitioning) {
            ThemeOverlaySimplified.hide()
        }
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
            startActivity(Intent(requireContext(), IgnoredAppsActivity::class.java))
        }
        binding.btnManageIgnoredImages.setOnClickListener {
            startActivity(Intent(requireContext(), IgnoredImagesActivity::class.java))
        }
        binding.btnManageArchiveOnlyApps.setOnClickListener {
            startActivity(Intent(requireContext(), ArchiveOnlyAppsActivity::class.java))
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

        if (ThemeOverlaySimplified.isTransitioning) {
            // Дожидаемся полной прорисовки вьюх
            binding.root.post {
                // 2 секунды паузы, затем резко убираем оверлей
                handler.postDelayed(hideOverlayRunnable, 2000L)
            }
        }
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

    private fun animateThemeSwitch(newMode: String, clickedView: View) {
        val activity = activity ?: return
        val decorView = activity.window.decorView as ViewGroup

        // Capture full screen screenshot of the current (old) theme
        val bitmap = captureFullScreen(decorView)

        // Get click position for the circle centre
        val loc = IntArray(2)
        clickedView.getLocationOnScreen(loc)
        val cx = loc[0] + clickedView.width / 2f
        val cy = loc[1] + clickedView.height / 2f

        // Determine the target background colour (used inside the circle)
        val newColor = when (newMode) {
            "dark"  -> Color.BLACK
            "light" -> Color.WHITE
            else    -> {
                val nightModeFlags = resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.BLACK
                else Color.WHITE
            }
        }

        // 1. Create the circle‑reveal overlay and add it directly to the decor view
        val overlayView = FakeThemeRevealView(requireContext()).apply {
            setRevealData(bitmap, cx, cy, newColor)
            radius = 0f // start invisible
        }

        // Cover the whole screen (including status/nav bars)
        overlayView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        overlayView.isClickable = false
        overlayView.isFocusable = false
        decorView.addView(overlayView)

        isThemeAnimating = true

        val maxRadius = hypot(
            resources.displayMetrics.widthPixels.toFloat(),
            resources.displayMetrics.heightPixels.toFloat()
        )

        // 2. Expand the circle to full screen (400 ms)
        val expandAnimator = ValueAnimator.ofFloat(0f, maxRadius).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                overlayView.radius = anim.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 3. Hand over data to the next activity
                    ThemeOverlayManager.pendingOverlayData = ThemeOverlayManager.OverlayData(
                        bitmap, cx, cy, newColor
                    )
                    // Remove the overlay view – the activity will be recreated
                    (overlayView.parent as? ViewGroup)?.removeView(overlayView)

                    // Apply theme and recreate
                    val modeToApply = pendingMode ?: newMode
                    pendingMode = null
                    pendingCheckedId = null
                    applyThemeAndRecreate(modeToApply)

                    isThemeAnimating = false
                }

                override fun onAnimationCancel(animation: Animator) {
                    isThemeAnimating = false
                    pendingMode = null
                    pendingCheckedId = null
                    (overlayView.parent as? ViewGroup)?.removeView(overlayView)
                }
            })
        }
        expandAnimator.start()
    }

    private fun captureFullScreen(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun applyThemeAndRecreate(mode: String) {
        viewModel.preferences.themeMode = mode
        AppCompatDelegate.setDefaultNightMode(nightModeFromString(mode))
        requireActivity().intent.putExtra("open_settings_after_recreate", true)
        requireActivity().recreate()
    }

    private fun nightModeFromString(mode: String): Int = when (mode) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
        else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
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
        handler.removeCallbacks(hideOverlayRunnable)
        super.onDestroyView()
        popupWindow?.dismiss()
        _binding = null
    }
}