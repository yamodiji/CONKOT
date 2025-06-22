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
import android.util.Log
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
    
    companion object {
        private const val TAG = "AppRepository"
    }
    
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
    val allApps: Flow<List<AppInfo>> = appDao.getAllApps().catch { e ->
        Log.e(TAG, "Error getting all apps from database", e)
        emit(emptyList())
    }
    
    // Favorite apps
    val favoriteApps: Flow<List<AppInfo>> = appDao.getFavoriteApps().catch { e ->
        Log.e(TAG, "Error getting favorite apps from database", e)
        emit(emptyList())
    }
    
    // Most used apps
    val mostUsedApps: Flow<List<AppInfo>> = appDao.getMostUsedApps(10).catch { e ->
        Log.e(TAG, "Error getting most used apps from database", e)
        emit(emptyList())
    }
    
    init {
        // Combine search query with all apps to create filtered results
        scope.launch {
            try {
                combine(
                    _searchQuery,
                    allApps
                ) { query, apps ->
                    filterApps(apps, query)
                }.catch { e ->
                    Log.e(TAG, "Error in apps filtering flow", e)
                    emit(emptyList())
                }.collect { filtered ->
                    _filteredApps.value = filtered
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up apps filtering", e)
            }
        }
    }
    
    /**
     * Refresh installed apps from system
     */
    suspend fun refreshInstalledApps() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting app refresh")
            _isLoading.postValue(true)
            
            try {
                // Check if we have permission to query apps
                if (!hasQueryAllPackagesPermission()) {
                    Log.w(TAG, "No QUERY_ALL_PACKAGES permission, cannot refresh apps")
                    _isLoading.postValue(false)
                    return@withContext
                }
                
                val installedApps = getInstalledApps()
                Log.d(TAG, "Found ${installedApps.size} installed apps")
                
                if (installedApps.isNotEmpty()) {
                    appDao.insertApps(installedApps)
                    Log.d(TAG, "Inserted apps into database")
                    
                    // Clean up old apps that are no longer installed
                    cleanupUninstalledApps(installedApps.map { it.packageName }.toSet())
                    Log.d(TAG, "Cleaned up uninstalled apps")
                } else {
                    Log.w(TAG, "No apps found during refresh")
                }
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during app refresh - missing permissions", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error during app refresh", e)
            } finally {
                _isLoading.postValue(false)
                Log.d(TAG, "App refresh completed")
            }
        }
    }
    
    /**
     * Check if app has QUERY_ALL_PACKAGES permission
     */
    private fun hasQueryAllPackagesPermission(): Boolean {
        return try {
            // Try to get a small sample of installed apps
            val testIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            packageManager.queryIntentActivities(testIntent, 0)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "QUERY_ALL_PACKAGES permission not granted", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }
    
    /**
     * Get installed apps from system
     */
    private fun getInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        
        try {
            Log.d(TAG, "Querying installed apps")
            
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            Log.d(TAG, "Found ${resolveInfos.size} launcher activities")
            
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
                    apps.add(appInfo)
                    
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Package not found: ${resolveInfo.activityInfo.packageName}")
                    continue
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing app: ${resolveInfo.activityInfo.packageName}", e)
                    continue
                }
            }
            
            Log.d(TAG, "Successfully processed ${apps.size} apps")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting installed apps", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps", e)
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
            "com.android.packageinstaller",
            "com.speedrawer.conkot"  // Hide our own app
        )
        
        return hidePackages.any { packageName.contains(it, ignoreCase = true) }
    }
    
    /**
     * Clean up apps that are no longer installed
     */
    private suspend fun cleanupUninstalledApps(installedPackages: Set<String>) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Cleaning up uninstalled apps")
                val allDbApps = appDao.getAllApps().first()
                val uninstalledApps = allDbApps.filter { it.packageName !in installedPackages }
                
                Log.d(TAG, "Found ${uninstalledApps.size} uninstalled apps to remove")
                
                for (app in uninstalledApps) {
                    appDao.deleteApp(app.packageName)
                }
                
                Log.d(TAG, "Cleanup completed")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
    }
    
    /**
     * Search apps with query
     */
    fun search(query: String) {
        try {
            _searchQuery.value = query.trim()
            Log.d(TAG, "Search query updated: '$query'")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating search query", e)
        }
    }
    
    /**
     * Clear search
     */
    fun clearSearch() {
        try {
            _searchQuery.value = ""
            Log.d(TAG, "Search cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing search", e)
        }
    }
    
    /**
     * Filter apps based on search query
     */
    private fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        return try {
            if (query.isEmpty()) {
                apps.take(50) // Limit for performance
            } else {
                apps.asSequence()
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
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering apps", e)
            emptyList()
        }
    }
    
    /**
     * Launch an app
     */
    suspend fun launchApp(app: AppInfo): Boolean {
        return try {
            Log.d(TAG, "Launching app: ${app.displayName}")
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                
                // Update launch count in background
                scope.launch {
                    try {
                        appDao.incrementLaunchCount(app.packageName, System.currentTimeMillis())
                        Log.d(TAG, "Updated launch count for ${app.displayName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating launch count", e)
                    }
                }
                
                true
            } else {
                Log.w(TAG, "No launch intent found for ${app.displayName}")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception launching app: ${app.displayName}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${app.displayName}", e)
            false
        }
    }
    
    /**
     * Toggle favorite status
     */
    suspend fun toggleFavorite(app: AppInfo) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Toggling favorite for ${app.displayName}")
                appDao.updateFavoriteStatus(app.packageName, !app.isFavorite)
                Log.d(TAG, "Favorite status updated for ${app.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite status", e)
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
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found for icon: ${app.packageName}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app icon: ${app.displayName}", e)
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
            Log.d(TAG, "Vibration not available or error occurred", e)
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
                Log.e(TAG, "Error getting app details for $packageName", e)
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
                Log.d(TAG, "Starting cleanup")
                val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) // 30 days
                appDao.cleanupOldApps(cutoffTime)
                
                // Clear icon cache if it gets too large
                if (iconCache.size > 100) {
                    iconCache.clear()
                    Log.d(TAG, "Cleared icon cache")
                }
                
                Log.d(TAG, "Cleanup completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
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
                Log.e(TAG, "Error getting stats", e)
                emptyMap()
            }
        }
    }
} 