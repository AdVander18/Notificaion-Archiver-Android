package com.example.notificationarchiver

import android.content.Context
import androidx.appcompat.app.AlertDialog

object ConfirmationHelper {
    fun confirmIfNeeded(
        context: Context,
        skipPref: Boolean,
        title: String,
        message: String,
        positiveAction: () -> Unit
    ) {
        if (skipPref) {
            positiveAction()
        } else {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Да") { _, _ -> positiveAction() }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}