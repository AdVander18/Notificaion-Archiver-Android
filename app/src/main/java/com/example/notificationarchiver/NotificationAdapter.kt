package com.example.notificationarchiver

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val context: Context,
    private val layoutResId: Int,
    private var items: List<NotificationDatabaseHelper.NotificationEntry>,
    private val onItemClick: ((NotificationDatabaseHelper.NotificationEntry) -> Unit)? = null,
    private val onItemLongClick: ((NotificationDatabaseHelper.NotificationEntry) -> Boolean)? = null
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    private val iconCache = mutableMapOf<String, Drawable?>()
    private val defaultIcon: Drawable = context.resources.getDrawable(android.R.drawable.sym_def_app_icon, null)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appNameText: TextView = itemView.findViewById(R.id.appName)
        val titleText: TextView = itemView.findViewById(R.id.titleText)
        val bodyText: TextView = itemView.findViewById(R.id.bodyText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
        val notificationImage: ImageView = itemView.findViewById(R.id.notificationImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]

        holder.itemView.setOnClickListener { onItemClick?.invoke(entry) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(entry) ?: false
        }

        val appName = try {
            val appInfo = context.packageManager.getApplicationInfo(entry.packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            entry.packageName
        }
        holder.appNameText.text = appName
        holder.titleText.text = entry.title
        holder.bodyText.text = entry.text

        val sdf = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.getDefault())
        holder.timeText.text = sdf.format(Date(entry.timestamp))

        val icon = iconCache[entry.packageName] ?: run {
            try {
                context.packageManager.getApplicationIcon(entry.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        if (icon != null) {
            iconCache[entry.packageName] = icon
            holder.appIcon.setImageDrawable(icon)
        } else {
            holder.appIcon.setImageDrawable(defaultIcon)
        }

        if (entry.image != null && entry.image.isNotEmpty()) {
            val bitmap = BitmapFactory.decodeByteArray(entry.image, 0, entry.image.size)
            holder.notificationImage.setImageBitmap(bitmap)
            holder.notificationImage.visibility = View.VISIBLE
        } else {
            holder.notificationImage.setImageBitmap(null)
            holder.notificationImage.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<NotificationDatabaseHelper.NotificationEntry>) {
        items = newList
        notifyDataSetChanged()
    }
}