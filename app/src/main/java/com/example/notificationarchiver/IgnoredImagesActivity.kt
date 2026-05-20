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
import com.example.notificationarchiver.databinding.ActivityIgnoredImagesBinding

class IgnoredImagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIgnoredImagesBinding
    private lateinit var viewModel: IgnoredImagesViewModel
    private lateinit var adapter: IgnoredImageAdapter
    private var firstLoadDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIgnoredImagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[IgnoredImagesViewModel::class.java]
        adapter = IgnoredImageAdapter(this, viewModel)
        binding.listViewIgnoredImages.adapter = adapter

        binding.loadingDotsView.visibility = View.VISIBLE
        binding.loadingDotsView.startAnimationLoop()
        binding.listViewIgnoredImages.visibility = View.INVISIBLE

        viewModel.filteredApps.observe(this) { apps ->
            adapter.clear()
            adapter.addAll(apps)
            adapter.notifyDataSetChanged()

            if (!firstLoadDone) {
                firstLoadDone = true
                binding.listViewIgnoredImages.visibility = View.VISIBLE
                binding.listViewIgnoredImages.post {
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

class IgnoredImageAdapter(
    context: AppCompatActivity,
    private val viewModel: IgnoredImagesViewModel
) : ArrayAdapter<IgnoredImagesViewModel.AppInfo>(context, R.layout.item_ignored_image) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_ignored_image, parent, false)

        val app = getItem(position) ?: return view

        val iconView = view.findViewById<ImageView>(R.id.imageAppIcon)
        val textView = view.findViewById<TextView>(R.id.textAppName)
        val checkBox = view.findViewById<CheckBox>(R.id.checkboxIgnoreImage)

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