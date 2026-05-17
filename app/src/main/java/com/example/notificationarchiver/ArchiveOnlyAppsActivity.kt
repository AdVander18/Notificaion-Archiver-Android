package com.example.notificationarchiver

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.notificationarchiver.databinding.ActivityArchiveOnlyAppsBinding

class ArchiveOnlyAppsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArchiveOnlyAppsBinding
    private lateinit var viewModel: ArchiveOnlyAppsViewModel
    private lateinit var adapter: ArchiveOnlyAppAdapter

    // Флаг завершения первой загрузки данных
    private var firstLoadDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArchiveOnlyAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ArchiveOnlyAppsViewModel::class.java]

        adapter = ArchiveOnlyAppAdapter(this, viewModel)
        binding.listViewArchiveOnlyApps.adapter = adapter

        // Запуск анимации при старте
        binding.loadingDotsView.visibility = View.VISIBLE
        binding.loadingDotsView.startAnimationLoop()
        binding.listViewArchiveOnlyApps.visibility = View.INVISIBLE

        // Наблюдаем за отфильтрованным списком – здесь узнаём, что данные готовы
        viewModel.filteredApps.observe(this) { apps ->
            adapter.clear()
            adapter.addAll(apps)
            adapter.notifyDataSetChanged()

            if (!firstLoadDone) {
                firstLoadDone = true
                // Делаем список видимым, но он пока скрыт анимацией
                binding.listViewArchiveOnlyApps.visibility = View.VISIBLE
                binding.listViewArchiveOnlyApps.post {
                    // Когда список отрисован – останавливаем анимацию и прячем точки
                    binding.loadingDotsView.stopAnimationLoop()
                    binding.loadingDotsView.visibility = View.GONE
                }
            }
        }

        binding.buttonBack.setOnClickListener { finish() }

        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}

class ArchiveOnlyAppAdapter(
    context: AppCompatActivity,
    private val viewModel: ArchiveOnlyAppsViewModel
) : ArrayAdapter<ArchiveOnlyAppsViewModel.AppInfo>(context, R.layout.item_ignored_app) {

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
        checkBox.isChecked = viewModel.isArchiveOnly(app.packageName)
        checkBox.setOnCheckedChangeListener { _, checked ->
            viewModel.toggleArchiveOnly(app.packageName, checked)
        }
        return view
    }
}