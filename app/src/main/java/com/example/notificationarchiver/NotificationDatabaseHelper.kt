package com.example.notificationarchiver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NotificationDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

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
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT,
                title TEXT,
                text TEXT,
                timestamp INTEGER,
                notification_key TEXT,
                image BLOB
            )
        """.trimIndent())
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

    fun insertNotification(entry: NotificationEntry) {
        writableDatabase.insert("notifications", null, ContentValues().apply {
            put("package_name", entry.packageName)
            put("title", entry.title)
            put("text", entry.text)
            put("timestamp", entry.timestamp)
            put("notification_key", "${entry.packageName}_${System.nanoTime()}")
            put("image", entry.image)
        })
    }

    fun updateNotificationByKey(key: String, entry: NotificationEntry) {
        writableDatabase.update("notifications", ContentValues().apply {
            put("package_name", entry.packageName)
            put("title", entry.title)
            put("text", entry.text)
            put("timestamp", entry.timestamp)
            put("image", entry.image)
        }, "notification_key = ?", arrayOf(key))
    }

    fun notificationKeyExists(key: String): Boolean {
        readableDatabase.query("notifications", arrayOf("id"),
            "notification_key = ?", arrayOf(key), null, null, null
        ).use { return it.moveToFirst() }
    }

    fun deleteOldestForPackage(packageName: String, limit: Int) {
        if (limit <= 0) return
        writableDatabase.execSQL(
            "DELETE FROM notifications WHERE id IN (" +
                    "SELECT id FROM notifications WHERE package_name = ? " +
                    "ORDER BY timestamp ASC, id ASC LIMIT ?)",
            arrayOf(packageName, limit.toString())
        )
    }

    fun deleteOlderThan(timestampMillis: Long) {
        writableDatabase.delete("notifications", "timestamp < ?", arrayOf(timestampMillis.toString()))
    }

    fun getAllNotifications(): List<NotificationEntry> = query(null, null)

    fun getNotificationsByPackage(packageName: String): List<NotificationEntry> =
        query("package_name = ?", arrayOf(packageName))

    private fun query(selection: String?, args: Array<String>?): List<NotificationEntry> {
        val list = mutableListOf<NotificationEntry>()
        readableDatabase.query("notifications", null, selection, args, null, null, "timestamp DESC").use { cursor ->
            while (cursor.moveToNext()) {
                list.add(NotificationEntry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    image = cursor.getBlob(cursor.getColumnIndexOrThrow("image"))
                ))
            }
        }
        return list
    }

    data class PackageSummary(
        val packageName: String,
        val notificationCount: Int,
        val latestTimestamp: Long
    )

    fun getPackageSummaryList(): List<PackageSummary> {
        val list = mutableListOf<PackageSummary>()
        readableDatabase.rawQuery(
            "SELECT package_name, COUNT(*) AS cnt, MAX(timestamp) AS latest " +
                    "FROM notifications GROUP BY package_name ORDER BY latest DESC", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(PackageSummary(
                    packageName = cursor.getString(0),
                    notificationCount = cursor.getInt(1),
                    latestTimestamp = cursor.getLong(2)
                ))
            }
        }
        return list
    }

    fun deleteNotification(id: Long) =
        writableDatabase.delete("notifications", "id = ?", arrayOf(id.toString()))

    fun deleteNotificationsByPackage(packageName: String) =
        writableDatabase.delete("notifications", "package_name = ?", arrayOf(packageName))

    fun removeImagesForPackage(packageName: String) {
        writableDatabase.update("notifications",
            ContentValues().apply { putNull("image") },
            "package_name = ?", arrayOf(packageName))
    }

    fun removeImageForNotification(id: Long) {
        writableDatabase.update("notifications",
            ContentValues().apply { putNull("image") },
            "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllNotifications() = writableDatabase.delete("notifications", null, null)

    fun removeAllImages() {
        writableDatabase.update("notifications",
            ContentValues().apply { putNull("image") }, null, null)
    }

    data class Statistics(
        val totalNotifications: Int,
        val notificationsLast24h: Int,
        val textMemoryBytes: Long,
        val imageMemoryBytes: Long
    )

    fun getStatistics(): Statistics {
        val db = readableDatabase
        val totalCursor = db.rawQuery("SELECT COUNT(*) FROM notifications", null)
        val total = if (totalCursor.moveToFirst()) totalCursor.getInt(0) else 0
        totalCursor.close()

        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        val recentCursor = db.rawQuery("SELECT COUNT(*) FROM notifications WHERE timestamp > ?", arrayOf(dayAgo.toString()))
        val recent = if (recentCursor.moveToFirst()) recentCursor.getInt(0) else 0
        recentCursor.close()

        val textCursor = db.rawQuery("SELECT SUM(LENGTH(title) + LENGTH(text)) FROM notifications", null)
        val textMem = if (textCursor.moveToFirst()) textCursor.getLong(0) else 0L
        textCursor.close()

        val imageCursor = db.rawQuery("SELECT SUM(COALESCE(LENGTH(image), 0)) FROM notifications", null)
        val imageMem = if (imageCursor.moveToFirst()) imageCursor.getLong(0) else 0L
        imageCursor.close()

        return Statistics(total, recent, textMem, imageMem)
    }
}