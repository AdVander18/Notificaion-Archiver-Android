package com.example.notificationarchiver

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.notificationarchiver.databinding.ActivityAppSelectionBinding

class AppSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppSelectionBinding
    private lateinit var viewModel: AppSelectionViewModel
    private lateinit var adapter: AppSelectionAdapter
    private var firstLoadDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val selectionType = intent.getStringExtra(EXTRA_SELECTION_TYPE)
            ?.let { runCatching { AppSelectionType.valueOf(it) }.getOrDefault(AppSelectionType.IGNORE_APPS) }
            ?: AppSelectionType.IGNORE_APPS

        viewModel = ViewModelProvider(
            this, AppSelectionViewModel.Factory(application, selectionType)
        )[AppSelectionViewModel::class.java]

        adapter = AppSelectionAdapter(this,
            isPackageSelected = { pkg -> viewModel.isPackageSelected(pkg) },
            onToggle = { pkg, checked -> viewModel.togglePackage(pkg, checked) }
        )
        binding.listView.adapter = adapter

        binding.loadingDotsView.visibility = View.VISIBLE
        binding.loadingDotsView.startAnimationLoop()
        binding.listView.visibility = View.INVISIBLE

        viewModel.filteredApps.observe(this) { apps ->
            adapter.clear()
            adapter.addAll(apps)
            adapter.notifyDataSetChanged()

            if (!firstLoadDone) {
                firstLoadDone = true
                binding.listView.visibility = View.VISIBLE
                binding.listView.post {
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

    companion object {
        const val EXTRA_SELECTION_TYPE = "selection_type"
    }
}