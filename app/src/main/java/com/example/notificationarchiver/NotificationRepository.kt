package com.example.notificationarchiver

class NotificationRepository(
    private val db: NotificationDatabaseHelper,
    private val prefs: PreferencesManager
) {
    fun upsertNotification(
        packageName: String,
        title: String,
        text: String,
        timestamp: Long,
        notificationKey: String?,
        image: ByteArray? = null
    ) {
        val key = notificationKey ?: "${packageName}_${System.nanoTime()}"
        if (prefs.disableDuplicateNotifications) {
            if (db.notificationKeyExists(key)) {
                db.updateNotificationByKey(key, NotificationDatabaseHelper.NotificationEntry(
                    0, packageName, title, text, timestamp, image
                ))
                enforceDayLimit()
                return
            }
        }
        val uniqueKey = if (prefs.disableDuplicateNotifications) key else "$key _${System.nanoTime()}"
        db.insertNotification(NotificationDatabaseHelper.NotificationEntry(
            0, packageName, title, text, timestamp, image
        ).copy(id = 0)) // id не важен при insert
        enforceLimit(packageName)
        enforceDayLimit()
    }

    private fun enforceLimit(packageName: String) {
        val max = prefs.maxNotificationsPerApp
        if (max <= 0) return
        val count = db.getNotificationsByPackage(packageName).size
        if (count > max) {
            db.deleteOldestForPackage(packageName, count - max)
        }
    }

    private fun enforceDayLimit() {
        val days = prefs.maxNotificationDays
        if (days > 0) {
            val oldest = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            db.deleteOlderThan(oldest)
        }
    }

    fun getAllNotifications() = db.getAllNotifications()
    fun getNotificationsByPackage(pkg: String) = db.getNotificationsByPackage(pkg)
    fun getPackageSummaries() = db.getPackageSummaryList()
    fun deleteNotification(id: Long) = db.deleteNotification(id)
    fun deleteByPackage(pkg: String) = db.deleteNotificationsByPackage(pkg)
    fun removeImagesForPackage(pkg: String) = db.removeImagesForPackage(pkg)
    fun removeImageForNotification(id: Long) = db.removeImageForNotification(id)
    fun deleteAll() = db.deleteAllNotifications()
    fun removeAllImages() = db.removeAllImages()
    fun getStatistics() = db.getStatistics()
}