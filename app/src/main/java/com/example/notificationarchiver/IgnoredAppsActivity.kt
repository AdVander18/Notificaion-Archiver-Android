package com.example.notificationarchiver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.notificationarchiver.databinding.ActivityIgnoredAppsBinding

class IgnoredAppsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIgnoredAppsBinding
    private lateinit var viewModel: IgnoredAppsViewModel
    private lateinit var adapter: IgnoredAppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIgnoredAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[IgnoredAppsViewModel::class.java]

        adapter = IgnoredAppAdapter(this, viewModel)
        binding.listViewIgnoredApps.adapter = adapter

        viewModel.apps.observe(this) { apps ->
            adapter.clear()
            adapter.addAll(apps)
            adapter.notifyDataSetChanged()
        }
    }
}

class IgnoredAppAdapter(
    context: AppCompatActivity,
    private val viewModel: IgnoredAppsViewModel
) : ArrayAdapter<IgnoredAppsViewModel.AppInfo>(context, R.layout.item_ignored_app) {

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
        checkBox.isChecked = viewModel.isIgnored(app.packageName)
        checkBox.setOnCheckedChangeListener { _, checked ->
            viewModel.toggleIgnored(app.packageName, checked)
        }
        return view
    }
}