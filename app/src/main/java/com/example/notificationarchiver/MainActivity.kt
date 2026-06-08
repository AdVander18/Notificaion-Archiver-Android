package com.example.notificationarchiver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notificationarchiver.databinding.ActivityMainBinding
import com.google.android.material.color.DynamicColors
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    private lateinit var packageSummaryAdapter: PackageSummaryAdapter
    private lateinit var searchNotificationAdapter: NotificationAdapter

    private lateinit var swipeLayout: SwipeToSettingsLayout
    private lateinit var searchPanel: View
    private lateinit var searchEditText: android.widget.EditText
    private var isSearchPanelOpen = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkNotificationPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        swipeLayout = binding.root as SwipeToSettingsLayout
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }

        setSupportActionBar(binding.topAppBar)
        binding.topAppBar.setNavigationOnClickListener { swipeLayout.openPanel() }
        binding.topAppBar.navigationContentDescription = "Настройки"

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Инициализация адаптеров
        packageSummaryAdapter = PackageSummaryAdapter(
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

        searchNotificationAdapter = NotificationAdapter(
            this,
            R.layout.item_notification,
            emptyList(),
            onItemClick = { entry ->
                val intent = packageManager.getLaunchIntentForPackage(entry.packageName)
                if (intent != null) startActivity(intent)
                else Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show()
            },
            onItemLongClick = { entry ->
                askNavigateToNotification(entry)
                true
            }
        )

        binding.notificationRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.notificationRecyclerView.adapter = packageSummaryAdapter

        viewModel.packageSummaries.observe(this) { summaries ->
            if (searchEditText.text.isEmpty()) {
                packageSummaryAdapter.updateData(summaries)
            }
        }
        viewModel.latestNotification.observe(this) { _ ->
            viewModel.loadPackageSummaries()
        }

        viewModel.searchResults.observe(this) { notifications ->
            if (searchEditText.text.isNotEmpty()) {
                if (binding.notificationRecyclerView.adapter != searchNotificationAdapter) {
                    binding.notificationRecyclerView.adapter = searchNotificationAdapter
                }
                searchNotificationAdapter.updateData(notifications)
            }
        }

        checkNotificationPermission()
        if (!isNotificationListenerEnabled()) {
            showPermissionRequestDialog()
        }

        initSearchPanel()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isSearchPanelOpen -> hideSearchPanel()
                    swipeLayout.isPanelOpen() -> swipeLayout.closePanel()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        if (savedInstanceState?.getBoolean("settings_open", false) == true) {
            swipeLayout.requestOpen()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("settings_open", swipeLayout.isPanelOpen())
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        val themeMode = prefs.getString("theme_mode", "auto") ?: "auto"
        ThemeHelper.applyTheme(themeMode)
    }

    private fun initSearchPanel() {
        searchPanel = binding.searchPanel
        searchEditText = binding.searchEditText

        // Кнопка очистки
        binding.searchClearButton.setOnClickListener {
            searchEditText.text.clear()
        }

        // Обработка ввода текста
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.isNotEmpty()) {
                    viewModel.searchNotifications(query)
                } else {
                    // Возвращаемся к списку приложений
                    binding.notificationRecyclerView.adapter = packageSummaryAdapter
                    viewModel.loadPackageSummaries()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        searchPanel.visibility = View.GONE
        searchPanel.translationY = -dpToPx(48f)
    }

    private fun toggleSearchPanel() {
        if (isSearchPanelOpen) hideSearchPanel() else showSearchPanel()
    }

    private fun showSearchPanel() {
        if (isSearchPanelOpen) return
        isSearchPanelOpen = true
        searchPanel.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(searchPanel, "translationY", searchPanel.translationY, 0f)
            .setDuration(250).start()
        searchEditText.requestFocus()
    }

    private fun hideSearchPanel() {
        if (!isSearchPanelOpen) return
        isSearchPanelOpen = false
        searchEditText.text.clear()   // очистка текста вернёт список приложений через TextWatcher
        val anim = ObjectAnimator.ofFloat(searchPanel, "translationY", searchPanel.translationY, -dpToPx(48f))
        anim.duration = 250
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                searchPanel.visibility = View.GONE
            }
        })
        anim.start()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val colorOnSurface = com.google.android.material.color.MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            "colorOnSurface"
        )

        menu?.findItem(R.id.action_search)?.icon?.setTint(colorOnSurface)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                toggleSearchPanel()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun askNavigateToNotification(entry: NotificationDatabaseHelper.NotificationEntry) {
        AlertDialog.Builder(this)
            .setMessage("Перейти на это уведомление?")
            .setPositiveButton("Да") { _, _ -> navigateToNotification(entry) }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun navigateToNotification(entry: NotificationDatabaseHelper.NotificationEntry) {
        val intent = Intent(this, NotificationHistoryActivity::class.java).apply {
            putExtra("packageName", entry.packageName)
            putExtra("highlight_notification_id", entry.id)
        }
        startActivity(intent)
    }

    fun closeSettingsPanel() {
        swipeLayout.closePanel()
    }

    private fun showContextMenu(packageName: String) {
        val items = arrayOf(
            "Открыть приложение",
            "Удалить изображения",
            "Удалить уведомления",
            "Игнорировать",
            "Игнорировать изображения",
            "Удалить и игнорировать"
        )
        AlertDialog.Builder(this)
            .setTitle("Действия")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openApp(packageName)
                    1 -> removeImages(packageName)
                    2 -> deleteNotifications(packageName)
                    3 -> ignore(packageName)
                    4 -> ignoreImages(packageName)
                    5 -> deleteAndIgnore(packageName)
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

    private fun ignoreImages(pkg: String) {
        ConfirmationHelper.confirmIfNeeded(this, viewModel.preferences.skipIgnoreImages,
            "Игнорировать изображения",
            "Удалить все изображения и прекратить их сохранение для этого приложения?") {
            (application as App).repository.removeImagesForPackage(pkg)
            viewModel.preferences.addIgnoredImagePackage(pkg)
            viewModel.loadPackageSummaries()
            Toast.makeText(this, "Изображения игнорируются", Toast.LENGTH_SHORT).show()
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
}