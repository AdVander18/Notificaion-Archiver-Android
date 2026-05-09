package com.example.notificationarchiver

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class PackageSummaryAdapter(
    private val context: Context,
    private val layoutResId: Int,
    private var items: List<NotificationDatabaseHelper.PackageSummary>,
    private val onItemClick: ((NotificationDatabaseHelper.PackageSummary) -> Unit)? = null,
    private val onItemLongClick: ((NotificationDatabaseHelper.PackageSummary) -> Boolean)? = null
) : RecyclerView.Adapter<PackageSummaryAdapter.ViewHolder>() {

    private val iconCache = mutableMapOf<String, Drawable?>()
    private val defaultIcon: Drawable = context.resources.getDrawable(android.R.drawable.sym_def_app_icon, null)
    private val dateFormat = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appNameText: TextView = itemView.findViewById(R.id.appName)
        val countText: TextView = itemView.findViewById(R.id.notificationCount)
        val timeText: TextView = itemView.findViewById(R.id.latestTime)
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

        // Имя приложения
        val appName = try {
            val appInfo = context.packageManager.getApplicationInfo(entry.packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            entry.packageName
        }
        holder.appNameText.text = appName

        // Количество уведомлений
        holder.countText.text = entry.notificationCount.toString()

        val playIcon = ContextCompat.getDrawable(context, R.drawable.ic_play_arrow)
        playIcon?.let {
            val size = (holder.countText.lineHeight * 0.6f).toInt()
            it.setBounds(0, 0, size, size)
            holder.countText.setCompoundDrawables(it, null, null, null)
        }

        // Время последнего уведомления
        holder.timeText.text = dateFormat.format(Date(entry.latestTimestamp))

        // Иконка приложения с кэшированием
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
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<NotificationDatabaseHelper.PackageSummary>) {
        items = newList
        notifyDataSetChanged()
    }
}