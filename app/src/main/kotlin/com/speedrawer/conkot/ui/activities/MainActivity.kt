package com.speedrawer.conkot.ui.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var appAdapter: AppGridAdapter
    private lateinit var historyAdapter: SearchHistoryAdapter
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        try {
            // Check critical permissions first
            if (!hasRequiredPermissions()) {
                Log.w(TAG, "Missing required permissions")
                showPermissionError()
                return
            }
            
            Log.d(TAG, "Inflating layout...")
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "Layout inflated successfully")
            
            Log.d(TAG, "Setting up UI...")
            setupUI()
            Log.d(TAG, "UI setup complete")
            
            Log.d(TAG, "Setting up RecyclerView...")
            setupRecyclerView()
            Log.d(TAG, "RecyclerView setup complete")
            
            Log.d(TAG, "Setting up search...")
            setupSearch()
            Log.d(TAG, "Search setup complete")
            
            Log.d(TAG, "Setting up observers...")
            setupObservers()
            Log.d(TAG, "Observers setup complete")
            
            // Auto-focus search if enabled
            try {
                if (viewModel.showKeyboard) {
                    showKeyboard()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing keyboard", e)
            }
            
            Log.d(TAG, "onCreate completed successfully")
        } catch (e: Exception) {
            // Handle initialization errors gracefully
            Log.e(TAG, "Critical error in onCreate", e)
            handleCriticalError(e)
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Android 11+ (including Android 15) - Check multiple permission methods
                    val hasQueryPackages = try {
                        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        true
                    } catch (e: SecurityException) {
                        Log.w(TAG, "QUERY_ALL_PACKAGES permission not available", e)
                        false
                    }
                    
                    val hasOverlayPermission = Settings.canDrawOverlays(this)
                    
                    Log.d(TAG, "Android ${Build.VERSION.RELEASE}: QueryPackages=$hasQueryPackages, Overlay=$hasOverlayPermission")
                    
                    // For Android 11+, we need at least overlay permission or query packages permission
                    hasQueryPackages || hasOverlayPermission
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // Android 6-10 - Check overlay permission
                    Settings.canDrawOverlays(this)
                }
                else -> {
                    // Android 5 and below - No special permissions needed
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }
    
    private fun showPermissionError() {
        try {
            AlertDialog.Builder(this)
                .setTitle("App Discovery Permissions Required")
                .setMessage("""
                    CONKOT needs special permissions to discover your installed apps on Android ${Build.VERSION.RELEASE}.
                    
                    This is required for the app to function properly.
                    
                    Tap "Setup Permissions" to be guided through the process.
                """.trimIndent())
                .setPositiveButton("Setup Permissions") { _, _ ->
                    try {
                        val intent = Intent(this, PermissionActivity::class.java)
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open permission activity", e)
                        openManualSettings()
                    }
                }
                .setNegativeButton("Manual Setup") { _, _ ->
                    openManualSettings()
                }
                .setNeutralButton("Exit") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show permission dialog", e)
            Toast.makeText(this, "Permission required to access installed apps", Toast.LENGTH_LONG).show()
            openManualSettings()
        }
    }
    
    private fun openManualSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            Toast.makeText(this, "Please go to Settings → Apps → CONKOT and enable permissions", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun handleCriticalError(e: Exception) {
        try {
            val errorMessage = "App initialization failed: ${e.message}"
            Log.e(TAG, errorMessage, e)
            
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Sorry, the app couldn't start properly. Please try restarting the app or check if you have granted all required permissions.")
                .setPositiveButton("Retry") { _, _ ->
                    recreate()
                }
                .setNegativeButton("Exit") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        } catch (dialogError: Exception) {
            Log.e(TAG, "Failed to show error dialog", dialogError)
            Toast.makeText(this, "Critical error occurred", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupUI() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupUI", e)
            throw e
        }
    }
    
    private fun setupRecyclerView() {
        try {
            // App grid adapter
            appAdapter = AppGridAdapter(
                onAppClick = { app ->
                    try {
                        viewModel.launchApp(app)
                        if (viewModel.preferencesManager.clearSearchOnClose) {
                            clearSearch()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching app: ${app.displayName}", e)
                        Toast.makeText(this, "Failed to launch ${app.displayName}", Toast.LENGTH_SHORT).show()
                    }
                },
                onAppLongClick = { app ->
                    try {
                        viewModel.onAppLongPress(app)
                        showAppOptionsDialog(app)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in app long click", e)
                    }
                },
                getAppIcon = { app -> 
                    try {
                        viewModel.getAppIcon(app)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting app icon", e)
                        ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon)
                    }
                },
                animationsEnabled = { 
                    try {
                        viewModel.animationsEnabled
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking animations enabled", e)
                        true // Default to enabled
                    }
                }
            )
            
            // History adapter
            historyAdapter = SearchHistoryAdapter { query ->
                try {
                    viewModel.searchFromHistory(query)
                    binding.searchEditText.setText(query as CharSequence)
                    binding.searchEditText.setSelection(query.length)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in search history click", e)
                }
            }
            
            // Setup RecyclerView
            setupAppGrid()
            binding.historyRecyclerView.adapter = historyAdapter
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupRecyclerView", e)
            throw e
        }
    }
    
    private fun setupAppGrid() {
        try {
            val spanCount = calculateSpanCount()
            binding.appsRecyclerView.apply {
                layoutManager = GridLayoutManager(this@MainActivity, spanCount)
                adapter = appAdapter
                setHasFixedSize(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupAppGrid", e)
            // Fallback to simple setup
            try {
                binding.appsRecyclerView.apply {
                    layoutManager = GridLayoutManager(this@MainActivity, 4)
                    adapter = appAdapter
                }
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Even fallback setup failed", fallbackError)
                throw fallbackError
            }
        }
    }
    
    private fun calculateSpanCount(): Int {
        return try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val iconSize = (viewModel.iconSize.value ?: 64f) + AppConstants.ITEM_PADDING * 2
            (screenWidth / iconSize).toInt().coerceAtLeast(3).coerceAtMost(6)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating span count", e)
            4 // Default fallback
        }
    }
    
    private fun setupSearch() {
        try {
            binding.searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    try {
                        val query = s?.toString() ?: ""
                        viewModel.search(query)
                        
                        // Show/hide clear button
                        binding.clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                        
                        // Show/hide search history
                        updateSearchHistoryVisibility(query.isEmpty())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in search text changed", e)
                    }
                }
                
                override fun afterTextChanged(s: Editable?) {}
            })
            
            // Handle search actions
            binding.searchEditText.setOnEditorActionListener { _, _, _ ->
                try {
                    hideKeyboard()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error in search editor action", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupSearch", e)
            throw e
        }
    }
    
    private fun setupObservers() {
        try {
            // Observe loading state
            viewModel.isLoading.observe(this) { isLoading ->
                try {
                    binding.swipeRefresh.isRefreshing = isLoading
                    binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating loading state", e)
                }
            }
            
            // Observe filtered apps
            lifecycleScope.launch {
                try {
                    viewModel.filteredApps.collectLatest { apps ->
                        try {
                            appAdapter.submitList(apps)
                            
                            // Show/hide empty state
                            val isEmpty = apps.isEmpty()
                            binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
                            binding.appsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating app list", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error observing filtered apps", e)
                }
            }
            
            // Observe search history
            lifecycleScope.launch {
                try {
                    val history = viewModel.searchHistory.toList()
                    historyAdapter.submitList(history)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating search history", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupObservers", e)
            throw e
        }
    }
    
    private fun updateSearchHistoryVisibility(show: Boolean) {
        try {
            binding.historyRecyclerView.visibility = if (show && viewModel.searchHistory.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating search history visibility", e)
        }
    }
    
    private fun clearSearch() {
        try {
            binding.searchEditText.text.clear()
            viewModel.clearSearch()
            
            if (viewModel.showKeyboard) {
                showKeyboard()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing search", e)
        }
    }
    
    private fun showKeyboard() {
        try {
            binding.searchEditText.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing keyboard", e)
        }
    }
    
    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding keyboard", e)
        }
    }
    
    private fun showAppOptionsDialog(app: AppInfo) {
        try {
            val options = mutableListOf<String>()
            options.add(if (app.isFavorite) "Remove from favorites" else "Add to favorites")
            options.add("App info")
            options.add("Open app settings")
            
            AlertDialog.Builder(this)
                .setTitle(app.displayName)
                .setItems(options.toTypedArray()) { _, which ->
                    when (which) {
                        0 -> viewModel.toggleFavorite(app)
                        1 -> showAppInfo(app)
                        2 -> openAppSettings(app)
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing app options dialog", e)
            Toast.makeText(this, "Error showing app options", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showAppInfo(app: AppInfo) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${app.packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing app info", e)
            Toast.makeText(this, "Could not open app info", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openAppSettings(app: AppInfo) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${app.packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings", e)
            Toast.makeText(this, "Could not open app settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return try {
            menuInflater.inflate(R.menu.main_menu, menu)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating options menu", e)
            false
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return try {
            when (item.itemId) {
                R.id.action_settings -> {
                    // TODO: Open settings activity
                    Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_refresh -> {
                    viewModel.refreshApps()
                    true
                }
                R.id.action_clear_history -> {
                    viewModel.clearSearchHistory()
                    Toast.makeText(this, "Search history cleared", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling menu item selection", e)
            false
        }
    }
    
    override fun onBackPressed() {
        try {
            if (binding.searchEditText.text.isNotEmpty()) {
                clearSearch()
            } else {
                super.onBackPressed()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling back press", e)
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            // Refresh apps when returning to activity
            viewModel.refreshApps()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
        }
    }
    
    override fun onDestroy() {
        try {
            super.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    private fun checkPermissionsAndSetup() {
        try {
            Log.d(TAG, "Checking permissions for Android ${Build.VERSION.SDK_INT}")
            
            // Enhanced permission checking for Android 15
            when {
                Build.VERSION.SDK_INT >= 35 -> {
                    // Android 15 - Most restrictive
                    if (!hasAndroid15Permissions()) {
                        Log.w(TAG, "Android 15 permissions not granted, launching PermissionActivity")
                        launchPermissionActivity()
                        return
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Android 11-14
                    if (!hasQueryAllPackagesPermission()) {
                        Log.w(TAG, "QUERY_ALL_PACKAGES permission not granted, launching PermissionActivity")
                        launchPermissionActivity()
                        return
                    }
                }
                else -> {
                    // Android 10 and below - usually no issues
                    Log.d(TAG, "Android ${Build.VERSION.SDK_INT} - checking basic permissions")
                }
            }
            
            // If we get here, permissions should be OK, proceed with setup
            setupUI()
            loadApps()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            showErrorDialog("Permission Check Failed", 
                "Unable to verify app permissions. Error: ${e.message}")
        }
    }
    
    private fun hasAndroid15Permissions(): Boolean {
        return try {
            // Multiple permission checks for Android 15
            val hasQueryPackages = hasQueryAllPackagesPermission()
            val canQueryInstalledApps = canQueryInstalledApplications()
            val hasBasicAccess = hasBasicAppAccess()
            
            Log.d(TAG, "Android 15 permission check - Query: $hasQueryPackages, Apps: $canQueryInstalledApps, Basic: $hasBasicAccess")
            
            // Android 15 is more lenient, if we can query any apps, continue
            return hasQueryPackages || canQueryInstalledApps || hasBasicAccess
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Android 15 permissions", e)
            false
        }
    }
    
    private fun canQueryInstalledApplications(): Boolean {
        return try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            Log.d(TAG, "Can query ${apps.size} applications")
            apps.size > 10 // If we can see more than 10 apps, permissions are likely OK
        } catch (e: Exception) {
            Log.e(TAG, "Cannot query installed applications", e)
            false
        }
    }
    
    private fun hasBasicAppAccess(): Boolean {
        return try {
            val pm = packageManager
            // Try to get launcher apps
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            Log.d(TAG, "Can query ${apps.size} launcher apps")
            apps.size > 5 // If we can see some launcher apps, basic access is working
        } catch (e: Exception) {
            Log.e(TAG, "Cannot query launcher apps", e)
            false
        }
    }
    
    private fun hasQueryAllPackagesPermission(): Boolean {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // For Android 11+, check multiple ways
                    val permissionGranted = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.QUERY_ALL_PACKAGES
                    ) == PackageManager.PERMISSION_GRANTED
                    
                    // Try to query all packages as a test
                    val pm = packageManager
                    val installedApps = try {
                        pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    
                    val hasAppAccess = installedApps.size > 50 // Reasonable threshold
                    
                    Log.d(TAG, "Query All Packages - Permission: $permissionGranted, Apps found: ${installedApps.size}, HasAccess: $hasAppAccess")
                    
                    // For Android 15, be more lenient
                    if (Build.VERSION.SDK_INT >= 35) {
                        return hasAppAccess || permissionGranted
                    }
                    
                    return permissionGranted && hasAppAccess
                }
                else -> {
                    // For older versions, just check permission
                    val granted = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.QUERY_ALL_PACKAGES
                    ) == PackageManager.PERMISSION_GRANTED
                    
                    Log.d(TAG, "Query All Packages permission (older Android): $granted")
                    return granted
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking QUERY_ALL_PACKAGES permission", e)
            false
        }
    }
    
    private fun launchPermissionActivity() {
        try {
            Log.d(TAG, "Launching PermissionActivity")
            val intent = Intent(this, PermissionActivity::class.java)
            startActivity(intent)
            
            // Don't finish MainActivity immediately, let user return
        } catch (e: Exception) {
            Log.e(TAG, "Error launching PermissionActivity", e)
            showErrorDialog("Permission Setup Failed", 
                "Unable to open permission setup. Please enable permissions manually in Settings.")
        }
    }
    
    private fun showErrorDialog(title: String, message: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { _, _ ->
                    // Handle OK button click
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error dialog", e)
            Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
        }
    }
} 