package com.example.notificationarchiver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.notificationarchiver.databinding.ActivityIgnoredAppsBinding

class IgnoredAppsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIgnoredAppsBinding
    private lateinit var viewModel: IgnoredAppsViewModel
    private lateinit var adapter: IgnoredAppAdapter

    private var firstLoadDone = false

    // Обработчик предсказывающего жеста «назад»
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            animateAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIgnoredAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Регистрируем callback для жеста «назад»
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        viewModel = ViewModelProvider(this)[IgnoredAppsViewModel::class.java]

        adapter = IgnoredAppAdapter(this, viewModel)
        binding.listViewIgnoredApps.adapter = adapter

        binding.loadingDotsView.visibility = View.VISIBLE
        binding.loadingDotsView.startAnimationLoop()
        binding.listViewIgnoredApps.visibility = View.INVISIBLE

        viewModel.filteredApps.observe(this) { apps ->
            adapter.clear()
            adapter.addAll(apps)
            adapter.notifyDataSetChanged()

            if (!firstLoadDone) {
                firstLoadDone = true
                binding.listViewIgnoredApps.visibility = View.VISIBLE
                binding.listViewIgnoredApps.post {
                    binding.loadingDotsView.stopAnimationLoop()
                    binding.loadingDotsView.visibility = View.GONE
                }
            }
        }

        binding.buttonBack.setOnClickListener {
            animateAndFinish()
        }

        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun animateAndFinish() {
        val targetX = binding.root.width.toFloat()
        ValueAnimator.ofFloat(binding.root.translationX, targetX).apply {
            duration = 250L
            addUpdateListener { binding.root.translationX = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                    overridePendingTransition(0, 0)
                }
            })
            start()
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