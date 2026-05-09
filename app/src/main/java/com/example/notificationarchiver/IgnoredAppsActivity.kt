package com.example.notificationarchiver

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IgnoredAppsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ignored_apps)

        prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        val listView = findViewById<ListView>(R.id.listViewIgnoredApps)
        val adapter = IgnoredAppAdapter(this, prefs)
        listView.adapter = adapter
    }

    data class AppInfo(val packageName: String, val appName: String)

    class IgnoredAppAdapter(
        private val context: Context,
        private val prefs: SharedPreferences
    ) : ArrayAdapter<AppInfo>(context, R.layout.item_ignored_app) {

        private val apps: List<AppInfo>
        private val ignoredPackages: MutableSet<String>

        init {
            val pm = context.packageManager
            val ignored = prefs.getStringSet("ignored_packages", emptySet())?.toMutableSet()
                ?: mutableSetOf()
            ignoredPackages = ignored

            // Загружаем все приложения, исключая себя
            val allApps = pm.getInstalledApplications(0)
                .filter { it.packageName != context.packageName }
                .map { AppInfo(it.packageName, it.loadLabel(pm).toString()) }

            // Сортировка: сначала игнорируемые (по алфавиту), затем остальные (по алфавиту)
            apps = allApps.sortedWith(
                compareBy<AppInfo> {
                    !ignoredPackages.contains(it.packageName) // false = 0 для игнорируемых
                }.thenBy { it.appName.lowercase() }
            )
            addAll(apps)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_ignored_app, parent, false)

            val app = getItem(position) ?: return view

            val iconView = view.findViewById<ImageView>(R.id.imageAppIcon)
            val textView = view.findViewById<TextView>(R.id.textAppName)
            val checkBox = view.findViewById<CheckBox>(R.id.checkboxIgnoreApp)

            // Иконка приложения
            iconView.setImageDrawable(
                context.packageManager.getApplicationIcon(app.packageName)
            )
            textView.text = app.appName

            // Снимаем слушатель, чтобы не было ложных срабатываний при перерисовке
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = ignoredPackages.contains(app.packageName)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    ignoredPackages.add(app.packageName)
                } else {
                    ignoredPackages.remove(app.packageName)
                }
                // Сразу сохраняем изменения в SharedPreferences
                prefs.edit()
                    .putStringSet("ignored_packages", ignoredPackages)
                    .apply()
            }
            return view
        }
    }
}