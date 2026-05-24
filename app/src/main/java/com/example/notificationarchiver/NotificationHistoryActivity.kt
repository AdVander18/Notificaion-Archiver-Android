package com.example.notificationarchiver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationarchiver.databinding.ActivityNotificationHistoryBinding
import com.google.android.material.color.DynamicColors
import java.io.File

class NotificationHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationHistoryBinding
    private lateinit var viewModel: NotificationHistoryViewModel
    private lateinit var adapter: NotificationAdapter
    private var packageName: String? = null
    private var highlightNotificationId: Long = -1L

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
        highlightNotificationId = intent.getLongExtra("highlight_notification_id", -1L)

        val appName = packageName?.let { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) { pkg }
        } ?: "История уведомлений"
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
            // Если нужно подсветить уведомление и список загружен
            if (highlightNotificationId != -1L) {
                performScrollAndHighlight(list)
                highlightNotificationId = -1L // однократно
            }
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

    private fun performScrollAndHighlight(list: List<NotificationDatabaseHelper.NotificationEntry>) {
        val pos = list.indexOfFirst { it.id == highlightNotificationId }
        if (pos == -1) return

        val recyclerView = binding.historyRecyclerView
        // Плавная прокрутка к элементу
        recyclerView.smoothScrollToPosition(pos)

        // После остановки скролла центрируем (если нужно) и подсвечиваем
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    rv.removeOnScrollListener(this)
                    val layoutManager = rv.layoutManager as LinearLayoutManager
                    val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
                    val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()

                    if (pos in firstVisible..lastVisible) {
                        // Элемент уже виден полностью – просто подсветка
                        val vh = rv.findViewHolderForAdapterPosition(pos)
                        vh?.itemView?.let { highlightView(it) }
                    } else {
                        // Элемент не в зоне полной видимости – доводим до центра
                        rv.post {
                            val vh = rv.findViewHolderForAdapterPosition(pos)
                            if (vh != null) {
                                val itemHeight = vh.itemView.height
                                if (itemHeight > 0) {
                                    val offset = (rv.height / 2) - (itemHeight / 2)
                                    layoutManager.scrollToPositionWithOffset(pos, offset)
                                }
                                highlightView(vh.itemView)
                            }
                        }
                    }
                }
            }
        })
    }

    private fun highlightView(view: android.view.View) {
        val originalBg = view.background
        val startColor = 0x6635B5E8  // полупрозрачный голубой
        val endColor = Color.TRANSPARENT

        val colorAnim = ValueAnimator.ofArgb(startColor, endColor)
        colorAnim.addUpdateListener { animator ->
            view.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.background = originalBg
            }
        })
        colorAnim.duration = 1500
        colorAnim.start()
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
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
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