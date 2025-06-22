package com.speedrawer.conkot.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.speedrawer.conkot.R
import com.speedrawer.conkot.databinding.ActivityMainBinding
import com.speedrawer.conkot.data.models.AppInfo
import com.speedrawer.conkot.ui.adapters.AppGridAdapter
import com.speedrawer.conkot.ui.adapters.SearchHistoryAdapter
import com.speedrawer.conkot.ui.viewmodels.MainViewModel
import com.speedrawer.conkot.utils.AppConstants
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var appAdapter: AppGridAdapter
    private lateinit var historyAdapter: SearchHistoryAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupRecyclerView()
        setupSearch()
        setupObservers()
        
        // Auto-focus search if enabled
        if (viewModel.showKeyboard) {
            showKeyboard()
        }
    }
    
    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Setup clear button
        binding.clearButton.setOnClickListener {
            clearSearch()
        }
        
        // Setup refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshApps()
        }
    }
    
    private fun setupRecyclerView() {
        // App grid adapter
        appAdapter = AppGridAdapter(
            onAppClick = { app ->
                viewModel.launchApp(app)
                if (viewModel.preferencesManager.clearSearchOnClose) {
                    clearSearch()
                }
            },
            onAppLongClick = { app ->
                viewModel.onAppLongPress(app)
                showAppOptionsDialog(app)
            },
            getAppIcon = { app -> viewModel.getAppIcon(app) },
            animationsEnabled = { viewModel.animationsEnabled }
        )
        
        // History adapter
        historyAdapter = SearchHistoryAdapter { query ->
            viewModel.searchFromHistory(query)
            binding.searchEditText.setText(query)
            binding.searchEditText.setSelection(query.length)
        }
        
        // Setup RecyclerView
        setupAppGrid()
        binding.historyRecyclerView.adapter = historyAdapter
    }
    
    private fun setupAppGrid() {
        val spanCount = calculateSpanCount()
        binding.appsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, spanCount)
            adapter = appAdapter
            setHasFixedSize(true)
        }
    }
    
    private fun calculateSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val iconSize = viewModel.iconSize.value + AppConstants.ITEM_PADDING * 2
        return (screenWidth / iconSize).toInt().coerceAtLeast(3).coerceAtMost(6)
    }
    
    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                viewModel.search(query)
                
                // Show/hide clear button
                binding.clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                
                // Show/hide search history
                updateSearchHistoryVisibility(query.isEmpty())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Handle search actions
        binding.searchEditText.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            true
        }
    }
    
    private fun setupObservers() {
        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Observe apps
        lifecycleScope.launch {
            viewModel.getDisplayApps().collect { apps ->
                appAdapter.submitList(apps)
                updateEmptyState(apps.isEmpty() && binding.searchEditText.text.isNotEmpty())
            }
        }
        
        // Observe search history
        updateSearchHistory()
        
        // Observe icon size changes
        lifecycleScope.launch {
            viewModel.iconSize.collect { iconSize ->
                setupAppGrid() // Recalculate grid
                appAdapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun updateSearchHistoryVisibility(show: Boolean) {
        binding.historyRecyclerView.visibility = if (show && viewModel.searchHistory.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
    
    private fun updateSearchHistory() {
        val history = viewModel.searchHistory.toList().takeLast(10).reversed()
        historyAdapter.submitList(history)
        updateSearchHistoryVisibility(binding.searchEditText.text.isEmpty())
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.appsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun clearSearch() {
        binding.searchEditText.text.clear()
        viewModel.clearSearch()
        
        if (viewModel.showKeyboard) {
            showKeyboard()
        }
    }
    
    private fun showKeyboard() {
        binding.searchEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }
    
    private fun showAppOptionsDialog(app: AppInfo) {
        val options = mutableListOf<String>()
        options.add(if (app.isFavorite) "Remove from favorites" else "Add to favorites")
        options.add("App info")
        options.add("Open app settings")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(app.displayName)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> viewModel.toggleFavorite(app)
                    1 -> showAppInfo(app)
                    2 -> openAppSettings(app)
                }
            }
            .show()
    }
    
    private fun showAppInfo(app: AppInfo) {
        val intent = Intent(this, AppInfoActivity::class.java).apply {
            putExtra("package_name", app.packageName)
        }
        startActivity(intent)
    }
    
    private fun openAppSettings(app: AppInfo) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${app.packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open app settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                viewModel.refreshApps()
                true
            }
            R.id.action_clear_history -> {
                viewModel.clearSearchHistory()
                updateSearchHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Auto-focus if enabled and search is empty
        if (viewModel.showKeyboard && binding.searchEditText.text.isEmpty()) {
            binding.searchEditText.postDelayed({
                showKeyboard()
            }, 100)
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Clear search if setting is enabled
        if (viewModel.preferencesManager.clearSearchOnClose) {
            clearSearch()
        }
    }
} 