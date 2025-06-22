package com.speedrawer.conkot.ui.activities

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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

// Android 15 Universal Permission Support - v2.0
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var appAdapter: AppGridAdapter
    private lateinit var historyAdapter: SearchHistoryAdapter
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_QUERY_ALL_PACKAGES = 1001
        private const val REQUEST_OVERLAY_PERMISSION = 1002
    }
    
    // Permission launchers for modern Android
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Overlay permission result received")
        checkPermissionsAndLoadApps()
    }
    
    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "App settings result received")
        checkPermissionsAndLoadApps()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate - Android ${Build.VERSION.SDK_INT}")
        
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setSupportActionBar(binding.toolbar)
            supportActionBar?.title = "CONKOT"
            
            setupUI()
            checkPermissionsAndLoadApps()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            handleCriticalError(e)
        }
    }
    
    private fun checkPermissionsAndLoadApps() {
        try {
            Log.d(TAG, "Checking permissions for Android ${Build.VERSION.SDK_INT}")
            
            when {
                Build.VERSION.SDK_INT >= 35 -> {
                    // Android 15 - Check if we can access apps
                    if (canAccessApps()) {
                        Log.d(TAG, "Android 15: Apps accessible, loading apps")
                        loadApps()
                    } else {
                        Log.d(TAG, "Android 15: Apps not accessible, requesting permissions")
                        requestAndroid15Permissions()
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Android 11-14 - Check QUERY_ALL_PACKAGES
                    if (hasQueryAllPackagesPermission()) {
                        Log.d(TAG, "Android 11+: QUERY_ALL_PACKAGES granted, loading apps")
                        loadApps()
                    } else {
                        Log.d(TAG, "Android 11+: QUERY_ALL_PACKAGES not granted, requesting permission")
                        requestQueryAllPackagesPermission()
                    }
                }
                else -> {
                    // Android 10 and below - Should work without special permissions
                    Log.d(TAG, "Android 10 and below: Loading apps directly")
                    loadApps()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            showErrorDialog("Permission Check Failed", "Unable to check app permissions: ${e.message}")
        }
    }
    
    private fun canAccessApps(): Boolean {
        return try {
            val pm = packageManager
            
            // Try multiple methods to check app access
            val launcherApps = try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (e: Exception) {
                emptyList()
            }
            
            val installedApps = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                emptyList()
            }
            
            val canAccessLauncher = launcherApps.size > 5
            val canAccessInstalled = installedApps.size > 20
            
            Log.d(TAG, "App access check - Launcher apps: ${launcherApps.size}, Installed apps: ${installedApps.size}")
            Log.d(TAG, "Can access launcher: $canAccessLauncher, Can access installed: $canAccessInstalled")
            
            canAccessLauncher || canAccessInstalled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking app access", e)
            false
        }
    }
    
    private fun hasQueryAllPackagesPermission(): Boolean {
        return try {
            val permissionGranted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.QUERY_ALL_PACKAGES
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d(TAG, "QUERY_ALL_PACKAGES permission: $permissionGranted")
            
            // Also test if we can actually query apps
            if (permissionGranted) {
                val pm = packageManager
                val apps = try {
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                } catch (e: Exception) {
                    emptyList()
                }
                val actuallyWorks = apps.size > 20
                Log.d(TAG, "Permission granted but can query ${apps.size} apps - actually works: $actuallyWorks")
                return actuallyWorks
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking QUERY_ALL_PACKAGES permission", e)
            false
        }
    }
    
    private fun requestAndroid15Permissions() {
        // For Android 15, show a dialog explaining what we need
        AlertDialog.Builder(this)
            .setTitle("App Access Required")
            .setMessage("""
                CONKOT needs to access your installed apps to display them.
                
                On Android 15, this requires special permission. Please:
                
                1. Tap "Grant Permission" below
                2. Find "Display over other apps" or "Query all packages"
                3. Enable the permission for CONKOT
                4. Return to this app
                
                This is a one-time setup.
            """.trimIndent())
            .setPositiveButton("Grant Permission") { _, _ ->
                openAppPermissionSettings()
            }
            .setNegativeButton("Try Alternative") { _, _ ->
                requestBasicPermissions()
            }
            .setNeutralButton("Skip") { _, _ ->
                showLimitedFunctionalityWarning()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun requestQueryAllPackagesPermission() {
        // For Android 11-14, try runtime permission request first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Try to request the permission directly first
            try {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.QUERY_ALL_PACKAGES),
                    REQUEST_QUERY_ALL_PACKAGES
                )
            } catch (e: Exception) {
                Log.e(TAG, "Cannot request QUERY_ALL_PACKAGES at runtime", e)
                // Fall back to settings
                showQueryAllPackagesDialog()
            }
        } else {
            showQueryAllPackagesDialog()
        }
    }
    
    private fun showQueryAllPackagesDialog() {
        AlertDialog.Builder(this)
            .setTitle("App Discovery Permission")
            .setMessage("""
                To show all your installed apps, CONKOT needs the "Query All Packages" permission.
                
                This is required on Android 11+ for security reasons.
                
                Please enable this permission in the next screen.
            """.trimIndent())
            .setPositiveButton("Enable Permission") { _, _ ->
                openAppPermissionSettings()
            }
            .setNegativeButton("Continue with Limited Apps") { _, _ ->
                showLimitedFunctionalityWarning()
                loadApps() // Load with whatever apps we can see
            }
            .setCancelable(false)
            .show()
    }
    
    private fun requestBasicPermissions() {
        // Try to request overlay permission as a fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Display Permission")
                .setMessage("CONKOT needs permission to display over other apps. This helps with app discovery.")
                .setPositiveButton("Grant") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        overlayPermissionLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting overlay permission", e)
                        openAppPermissionSettings()
                    }
                }
                .setNegativeButton("Skip") { _, _ ->
                    showLimitedFunctionalityWarning()
                    loadApps()
                }
                .show()
        } else {
            // If overlay permission is already granted or not needed, try to load apps
            loadApps()
        }
    }
    
    private fun openAppPermissionSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            appSettingsLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings", e)
            Toast.makeText(this, "Please enable permissions manually in Settings → Apps → CONKOT", Toast.LENGTH_LONG).show()
            loadApps() // Try to load whatever we can
        }
    }
    
    private fun showLimitedFunctionalityWarning() {
        AlertDialog.Builder(this)
            .setTitle("Limited Functionality")
            .setMessage("""
                Without the required permissions, CONKOT may only show a limited number of apps.
                
                You can enable full permissions later in:
                Settings → Apps → CONKOT → Permissions
            """.trimIndent())
            .setPositiveButton("OK") { _, _ ->
                loadApps()
            }
            .show()
    }
    
    private fun loadApps() {
        try {
            Log.d(TAG, "Loading apps...")
            lifecycleScope.launch {
                try {
                    viewModel.refreshApps()
                    Log.d(TAG, "Apps refresh initiated")
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing apps", e)
                    showErrorDialog("App Loading Failed", "Unable to load apps: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting app load", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        Log.d(TAG, "Permission result: requestCode=$requestCode, results=${grantResults.contentToString()}")
        
        when (requestCode) {
            REQUEST_QUERY_ALL_PACKAGES -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "QUERY_ALL_PACKAGES permission granted via runtime request")
                    Toast.makeText(this, "Permission granted! Loading apps...", Toast.LENGTH_SHORT).show()
                    loadApps()
                } else {
                    Log.d(TAG, "QUERY_ALL_PACKAGES permission denied via runtime request")
                    // Show the settings dialog as fallback
                    showQueryAllPackagesDialog()
                }
            }
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
            Log.d(TAG, "onResume - checking if we need to reload apps")
            // Only reload if we don't have apps yet
            lifecycleScope.launch {
                try {
                    // Check if we have apps loaded
                    viewModel.filteredApps.value.let { apps ->
                        if (apps.isEmpty()) {
                            Log.d(TAG, "No apps loaded, triggering refresh")
                            viewModel.refreshApps()
                        } else {
                            Log.d(TAG, "Apps already loaded (${apps.size} apps)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onResume refresh check", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
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
} 