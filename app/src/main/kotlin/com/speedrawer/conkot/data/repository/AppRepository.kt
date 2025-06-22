package com.speedrawer.conkot.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.speedrawer.conkot.data.database.AppDao
import com.speedrawer.conkot.data.models.AppInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class AppRepository(
    private val context: Context,
    private val appDao: AppDao
) {
    private val packageManager = context.packageManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Cache for app icons to reduce memory usage
    private val iconCache = ConcurrentHashMap<String, android.graphics.drawable.Drawable>()
    
    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    // Filtered apps based on search
    private val _filteredApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val filteredApps: StateFlow<List<AppInfo>> = _filteredApps
    
    // All apps from database
    val allApps: Flow<List<AppInfo>> = appDao.getAllApps()
    
    // Favorite apps
    val favoriteApps: Flow<List<AppInfo>> = appDao.getFavoriteApps()
    
    // Most used apps
    val mostUsedApps: Flow<List<AppInfo>> = appDao.getMostUsedApps(10)
    
    init {
        // Combine search query with all apps to create filtered results
        scope.launch {
            combine(
                _searchQuery,
                allApps
            ) { query, apps ->
                filterApps(apps, query)
            }.collect { filtered ->
                _filteredApps.value = filtered
            }
        }
    }
    
    /**
     * Refresh installed apps from system
     */
    suspend fun refreshInstalledApps() {
        withContext(Dispatchers.IO) {
            _isLoading.postValue(true)
            
            try {
                val installedApps = getInstalledApps()
                appDao.insertApps(installedApps)
                
                // Clean up old apps that are no longer installed
                cleanupUninstalledApps(installedApps.map { it.packageName }.toSet())
                
            } catch (e: Exception) {
                // Handle errors gracefully
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Get installed apps from system
     */
    private fun getInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        
        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            
            for (resolveInfo in resolveInfos) {
                try {
                    val packageName = resolveInfo.activityInfo.packageName
                    
                    // Skip if it's our own app or should be hidden
                    if (packageName == context.packageName || shouldHideApp(packageName)) {
                        continue
                    }
                    
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    val applicationInfo = packageInfo.applicationInfo
                    val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                    
                    val appInfo = AppInfo.fromPackageInfo(packageInfo, applicationInfo, appName)
                    
                    // Load existing data if available
                    val existingApp = appDao.getApp(packageName)
                    if (existingApp != null) {
                        appInfo.launchCount = existingApp.launchCount
                        appInfo.lastLaunchTime = existingApp.lastLaunchTime
                        appInfo.isFavorite = existingApp.isFavorite
                    }
                    
                    apps.add(appInfo)
                    
                } catch (e: Exception) {
                    // Skip apps that cause errors
                    continue
                }
            }
        } catch (e: Exception) {
            // Handle system-level errors
        }
        
        return apps
    }
    
    /**
     * Check if app should be hidden
     */
    private fun shouldHideApp(packageName: String): Boolean {
        val hidePackages = setOf(
            "com.android.launcher",
            "com.google.android.launcher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oneplus.launcher",
            "com.android.settings",
            "com.android.packageinstaller"
        )
        
        return hidePackages.any { packageName.contains(it) }
    }
    
    /**
     * Clean up apps that are no longer installed
     */
    private suspend fun cleanupUninstalledApps(installedPackages: Set<String>) {
        withContext(Dispatchers.IO) {
            try {
                val allDbApps = appDao.getAllApps().first()
                val uninstalledApps = allDbApps.filter { it.packageName !in installedPackages }
                
                for (app in uninstalledApps) {
                    appDao.deleteApp(app.packageName)
                }
                
            } catch (e: Exception) {
                // Handle errors gracefully
            }
        }
    }
    
    /**
     * Search apps with query
     */
    fun search(query: String) {
        _searchQuery.value = query.trim()
    }
    
    /**
     * Clear search
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    /**
     * Filter apps based on search query
     */
    private fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isEmpty()) {
            return apps.take(50) // Limit for performance
        }
        
        return apps.asSequence()
            .filter { it.matchesQuery(query) }
            .map { app ->
                app.apply { searchScore = calculateSearchScore(query) }
            }
            .sortedWith(compareByDescending<AppInfo> { it.isFavorite }
                .thenByDescending { it.searchScore }
                .thenByDescending { it.launchCount }
                .thenBy { it.displayName.lowercase() })
            .take(50) // Limit results for performance
            .toList()
    }
    
    /**
     * Launch an app
     */
    suspend fun launchApp(app: AppInfo): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                context.startActivity(intent)
                
                // Update launch count in background
                scope.launch {
                    appDao.incrementLaunchCount(app.packageName, System.currentTimeMillis())
                }
                
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Toggle favorite status
     */
    suspend fun toggleFavorite(app: AppInfo) {
        withContext(Dispatchers.IO) {
            try {
                appDao.updateFavoriteStatus(app.packageName, !app.isFavorite)
            } catch (e: Exception) {
                // Handle errors gracefully
            }
        }
    }
    
    /**
     * Get app icon with caching
     */
    fun getAppIcon(app: AppInfo): android.graphics.drawable.Drawable? {
        return try {
            iconCache[app.packageName] ?: run {
                val icon = packageManager.getApplicationIcon(app.packageName)
                iconCache[app.packageName] = icon
                icon
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Provide haptic feedback
     */
    fun vibrate(duration: Long = 50) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = ContextCompat.getSystemService(context, VibratorManager::class.java)
                vibratorManager?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }
    
    /**
     * Get app info details
     */
    suspend fun getAppDetails(packageName: String): AppInfo? {
        return withContext(Dispatchers.IO) {
            try {
                appDao.getApp(packageName)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Cleanup old data
     */
    suspend fun cleanup() {
        withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) // 30 days
                appDao.cleanupOldApps(cutoffTime)
                
                // Clear icon cache if it gets too large
                if (iconCache.size > 100) {
                    iconCache.clear()
                }
            } catch (e: Exception) {
                // Handle errors gracefully
            }
        }
    }
    
    /**
     * Get statistics
     */
    suspend fun getStats(): Map<String, Int> {
        return withContext(Dispatchers.IO) {
            try {
                mapOf(
                    "total_apps" to appDao.getAppCount(),
                    "favorite_apps" to appDao.getFavoriteCount()
                )
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
} 