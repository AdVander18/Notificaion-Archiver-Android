package com.example.notificationarchiver

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView

class AppSelectionAdapter(
    context: Context,
    private val isPackageSelected: (String) -> Boolean,
    private val onToggle: (String, Boolean) -> Unit
) : ArrayAdapter<AppSelectionViewModel.AppInfo>(context, R.layout.item_ignored_app) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_ignored_app, parent, false)

        val app = getItem(position) ?: return view

        val iconView = view.findViewById<ImageView>(R.id.imageAppIcon)
        val textView = view.findViewById<TextView>(R.id.textAppName)
        val checkBox = view.findViewById<CheckBox>(R.id.checkboxIgnoreApp)

        iconView.setImageDrawable(context.packageManager.getApplicationIcon(app.packageName))
        textView.text = app.appName

        checkBox.setOnCheckedChangeListener(null)
        checkBox.isChecked = isPackageSelected(app.packageName)
        checkBox.setOnCheckedChangeListener { _, checked ->
            onToggle(app.packageName, checked)
        }
        return view
    }
}