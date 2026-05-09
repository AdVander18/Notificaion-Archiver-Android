package com.example.notificationarchiver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NotificationDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext: Context = context.applicationContext

    data class NotificationEntry(
        val id: Long,
        val packageName: String,
        val title: String,
        val text: String,
        val timestamp: Long,
        val image: ByteArray? = null
    )

    companion object {
        private const val DATABASE_NAME = "notifications.db"
        private const val DATABASE_VERSION = 5
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: NotificationDatabaseHelper? = null

        fun getInstance(context: Context): NotificationDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: NotificationDatabaseHelper(context.applicationContext)
                    .also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT,
                title TEXT,
                text TEXT,
                timestamp INTEGER,
                notification_key TEXT,
                image BLOB
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL("UPDATE notifications SET notification_key = notification_key || '_' || id")
        }
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS notifications")
            onCreate(db)
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN image BLOB")
        }
    }

    fun upsertNotification(
        packageName: String,
        title: String,
        text: String,
        timestamp: Long,
        notificationKey: String?,
        disableDuplicates: Boolean,
        image: ByteArray? = null
    ) {
        val db = writableDatabase
        val safeKey = notificationKey ?: "${packageName}_${System.nanoTime()}"

        if (disableDuplicates) {
            val cursor = db.query(
                "notifications", arrayOf("id"),
                "notification_key = ?", arrayOf(safeKey),
                null, null, null
            )
            val exists = cursor.use { it.moveToFirst() }

            if (exists) {
                val values = ContentValues().apply {
                    put("package_name", packageName)
                    put("title", title)
                    put("text", text)
                    put("timestamp", timestamp)
                    put("image", image)
                }
                db.update("notifications", values, "notification_key = ?", arrayOf(safeKey))
                enforceDayLimit(db)
            } else {
                insertNew(db, packageName, title, text, timestamp, safeKey, image)
                enforceLimit(db, packageName)
                enforceDayLimit(db)
            }
        } else {
            val uniqueKey = safeKey + "_" + System.nanoTime().toString()
            insertNew(db, packageName, title, text, timestamp, uniqueKey, image)
            // Применяем лимит
            enforceLimit(db, packageName)
            enforceDayLimit(db)
        }
    }

    private fun insertNew(
        db: SQLiteDatabase,
        packageName: String,
        title: String,
        text: String,
        timestamp: Long,
        notificationKey: String,
        image: ByteArray? = null
    ) {
        val values = ContentValues().apply {
            put("package_name", packageName)
            put("title", title)
            put("text", text)
            put("timestamp", timestamp)
            put("notification_key", notificationKey)
            put("image", image)
        }
        db.insert("notifications", null, values)
    }

    // Удаление самых старых уведомлений при превышении лимита
    private fun enforceLimit(db: SQLiteDatabase, packageName: String) {
        val prefs = appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val max = prefs.getInt("max_notifications_per_app", 9999)
        if (max <= 0) return  // 0 или меньше — без ограничений

        val countCursor = db.rawQuery(
            "SELECT COUNT(*) FROM notifications WHERE package_name = ?",
            arrayOf(packageName)
        )
        val count = if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
        countCursor.close()

        if (count > max) {
            val excess = count - max
            // Удаляем самые старые (сортировка по timestamp, затем по id)
            db.execSQL(
                "DELETE FROM notifications WHERE id IN (" +
                        "SELECT id FROM notifications WHERE package_name = ? " +
                        "ORDER BY timestamp ASC, id ASC LIMIT ?)",
                arrayOf(packageName, excess.toString())
            )
        }
    }

    private fun enforceDayLimit(db: SQLiteDatabase) {
        val prefs = appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val maxDays = prefs.getInt("max_notification_days", 0) // 0 = без ограничения
        if (maxDays <= 0) return

        val oldestAllowedTime = System.currentTimeMillis() - maxDays * DAY_MILLIS
        db.delete("notifications", "timestamp < ?", arrayOf(oldestAllowedTime.toString()))
    }

    fun getAllNotifications(): List<NotificationEntry> {
        val list = mutableListOf<NotificationEntry>()
        val db = readableDatabase
        val cursor = db.query("notifications", null, null, null, null, null, "timestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                val imageBytes = it.getBlob(it.getColumnIndexOrThrow("image"))
                list.add(
                    NotificationEntry(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        packageName = it.getString(it.getColumnIndexOrThrow("package_name")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        text = it.getString(it.getColumnIndexOrThrow("text")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        image = imageBytes
                    )
                )
            }
        }
        return list
    }

    fun getNotificationsByPackage(packageName: String): List<NotificationEntry> {
        val list = mutableListOf<NotificationEntry>()
        val db = readableDatabase
        val cursor = db.query(
            "notifications", null,
            "package_name = ?", arrayOf(packageName),
            null, null, "timestamp DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val imageBytes = it.getBlob(it.getColumnIndexOrThrow("image"))
                list.add(
                    NotificationEntry(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        packageName = it.getString(it.getColumnIndexOrThrow("package_name")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        text = it.getString(it.getColumnIndexOrThrow("text")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        image = imageBytes
                    )
                )
            }
        }
        return list
    }

    // внутри NotificationDatabaseHelper
    data class PackageSummary(
        val packageName: String,
        val notificationCount: Int,
        val latestTimestamp: Long
    )

    fun getPackageSummaryList(): List<PackageSummary> {
        val list = mutableListOf<PackageSummary>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT package_name, COUNT(*) AS cnt, MAX(timestamp) AS latest " +
                    "FROM notifications GROUP BY package_name ORDER BY latest DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PackageSummary(
                        packageName = it.getString(0),
                        notificationCount = it.getInt(1),
                        latestTimestamp = it.getLong(2)
                    )
                )
            }
        }
        return list
    }

    fun deleteNotification(id: Long) {
        val db = writableDatabase
        db.delete("notifications", "id = ?", arrayOf(id.toString()))
    }

    // Удалить все уведомления конкретного пакета
    fun deleteNotificationsByPackage(packageName: String) {
        val db = writableDatabase
        db.delete("notifications", "package_name = ?", arrayOf(packageName))
    }

    // Удалить все изображения у одного приложения
    fun removeImagesForPackage(packageName: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            putNull("image")
        }
        db.update("notifications", values, "package_name = ?", arrayOf(packageName))
    }

    // Удалить изображение у одного уведомления
    fun removeImageForNotification(id: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            putNull("image")
        }
        db.update("notifications", values, "id = ?", arrayOf(id.toString()))
    }

    data class Statistics(
        val totalNotifications: Int,
        val notificationsLast24h: Int,
        val textMemoryBytes: Long,
        val imageMemoryBytes: Long
    )

    fun getStatistics(): Statistics {
        val db = readableDatabase

        // Общее количество уведомлений
        val totalCursor = db.rawQuery("SELECT COUNT(*) FROM notifications", null)
        val totalCount = if (totalCursor.moveToFirst()) totalCursor.getInt(0) else 0
        totalCursor.close()

        // За последние 24 часа
        val twentyFourHoursAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        val recentCursor = db.rawQuery(
            "SELECT COUNT(*) FROM notifications WHERE timestamp > ?",
            arrayOf(twentyFourHoursAgo.toString())
        )
        val recentCount = if (recentCursor.moveToFirst()) recentCursor.getInt(0) else 0
        recentCursor.close()

        // Суммарная длина текстовых полей (title + text)
        val textCursor = db.rawQuery(
            "SELECT SUM(LENGTH(title) + LENGTH(text)) FROM notifications", null
        )
        val textMemory = if (textCursor.moveToFirst()) textCursor.getLong(0) else 0L
        textCursor.close()

        // Суммарный размер изображений (BLOB)
        val imageCursor = db.rawQuery(
            "SELECT SUM(COALESCE(LENGTH(image), 0)) FROM notifications", null
        )
        val imageMemory = if (imageCursor.moveToFirst()) imageCursor.getLong(0) else 0L
        imageCursor.close()

        return Statistics(totalCount, recentCount, textMemory, imageMemory)
    }

    // Удалить все записи уведомлений
    fun deleteAllNotifications() {
        val db = writableDatabase
        db.delete("notifications", null, null)
    }

    // Установить изображение в null для всех уведомлений
    fun removeAllImages() {
        val db = writableDatabase
        val values = ContentValues().apply {
            putNull("image")
        }
        db.update("notifications", values, null, null)
    }
}