package com.speedrawer.conkot.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.provider.MediaStore
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
                // Try to get apps with whatever permissions we have
                val installedApps = getInstalledAppsWithFallback()
                Log.d(TAG, "Found ${installedApps.size} installed apps")
                
                if (installedApps.isNotEmpty()) {
                    appDao.insertApps(installedApps)
                    Log.d(TAG, "Inserted apps into database")
                    
                    // Clean up old apps that are no longer installed
                    cleanupUninstalledApps(installedApps.map { it.packageName }.toSet())
                    Log.d(TAG, "Cleaned up uninstalled apps")
                } else {
                    Log.w(TAG, "No apps found during refresh - trying alternative methods")
                    // Try alternative methods if no apps found
                    val fallbackApps = getAppsViaAlternativeMethods()
                    if (fallbackApps.isNotEmpty()) {
                        appDao.insertApps(fallbackApps)
                        Log.d(TAG, "Inserted ${fallbackApps.size} apps via fallback methods")
                    } else {
                        Log.d(TAG, "No fallback apps found")
                    }
                }
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during app refresh - trying fallback methods", e)
                try {
                    val fallbackApps = getAppsViaAlternativeMethods()
                    if (fallbackApps.isNotEmpty()) {
                        appDao.insertApps(fallbackApps)
                        Log.d(TAG, "Inserted ${fallbackApps.size} apps via fallback after security exception")
                    } else {
                        Log.d(TAG, "No fallback apps found after security exception")
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback methods also failed", e2)
                }
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
            Log.d(TAG, "Checking QUERY_ALL_PACKAGES permission on Android ${Build.VERSION.RELEASE}")
            
            // Try to get a small sample of installed apps
            val testIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = packageManager.queryIntentActivities(testIntent, 0)
            val hasPermission = resolveInfos.isNotEmpty()
            
            Log.d(TAG, "Permission check result: $hasPermission (found ${resolveInfos.size} apps)")
            
            // Also check if we can get installed applications
            if (!hasPermission) {
                try {
                    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    val alternativePermission = installedApps.isNotEmpty()
                    Log.d(TAG, "Alternative permission check: $alternativePermission (found ${installedApps.size} apps)")
                    return alternativePermission
                } catch (e: Exception) {
                    Log.w(TAG, "Alternative permission check failed", e)
                }
            }
            
            hasPermission
        } catch (e: SecurityException) {
            Log.w(TAG, "QUERY_ALL_PACKAGES permission not granted", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }
    
    /**
     * Get installed apps with fallback methods
     */
    private fun getInstalledAppsWithFallback(): List<AppInfo> {
        return try {
            // Try the primary method first
            val primaryApps = getInstalledApps()
            if (primaryApps.isNotEmpty()) {
                primaryApps
            } else {
                // If primary method fails, try alternatives
                Log.d(TAG, "Primary method returned no apps, trying alternatives")
                getAppsViaAlternativeMethods()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Primary method failed, trying alternatives", e)
            getAppsViaAlternativeMethods()
        }
    }
    
    /**
     * Alternative methods to get apps when permissions are limited
     */
    private fun getAppsViaAlternativeMethods(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        
        try {
            // Method 1: Get launcher apps (usually works even with limited permissions)
            val launcherApps = getLauncherApps()
            apps.addAll(launcherApps)
            Log.d(TAG, "Found ${launcherApps.size} launcher apps")
            
            // Method 2: Try to get apps by category
            val categoryApps = getAppsByCategory()
            apps.addAll(categoryApps)
            Log.d(TAG, "Found ${categoryApps.size} category apps")
            
            // Method 3: Try well-known package names
            val knownApps = getWellKnownApps()
            apps.addAll(knownApps)
            Log.d(TAG, "Found ${knownApps.size} well-known apps")
            
            // Remove duplicates
            val uniqueApps = apps.distinctBy { it.packageName }
            Log.d(TAG, "Total unique apps found via alternatives: ${uniqueApps.size}")
            
            return uniqueApps
        } catch (e: Exception) {
            Log.e(TAG, "Error in alternative app discovery methods", e)
            return emptyList()
        }
    }
    
    private fun getLauncherApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            Log.d(TAG, "Found ${resolveInfos.size} launcher activities")
            
            for (resolveInfo in resolveInfos) {
                try {
                    val packageName = resolveInfo.activityInfo.packageName
                    if (shouldHideApp(packageName)) continue
                    
                    val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    
                    val appInfoItem = AppInfo(
                        packageName = packageName,
                        appName = appName,
                        versionName = "Unknown",
                        versionCode = 0,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        installTimeMillis = System.currentTimeMillis(),
                        updateTimeMillis = System.currentTimeMillis(),
                        category = "Launcher",
                        isEnabled = appInfo.enabled,
                        launchCount = 0,
                        lastLaunchTime = 0,
                        isFavorite = false
                    )
                    
                    apps.add(appInfoItem)
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing launcher app", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting launcher apps", e)
        }
        
        return apps
    }
    
    private fun getAppsByCategory(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        
        val categories = listOf(
            Intent.CATEGORY_APP_BROWSER,
            Intent.CATEGORY_APP_CALCULATOR,
            Intent.CATEGORY_APP_CALENDAR,
            Intent.CATEGORY_APP_CONTACTS,
            Intent.CATEGORY_APP_EMAIL,
            Intent.CATEGORY_APP_GALLERY,
            Intent.CATEGORY_APP_MAPS,
            Intent.CATEGORY_APP_MESSAGING,
            Intent.CATEGORY_APP_MUSIC
        )
        
        categories.forEach { category ->
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(category)
                }
                
                val resolveInfos = packageManager.queryIntentActivities(intent, 0)
                
                resolveInfos.forEach { resolveInfo ->
                    try {
                        val packageName = resolveInfo.activityInfo.packageName
                        if (shouldHideApp(packageName)) return@forEach
                        
                        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        
                        val appInfoItem = AppInfo(
                            packageName = packageName,
                            appName = appName,
                            versionName = "Unknown",
                            versionCode = 0,
                            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            installTimeMillis = System.currentTimeMillis(),
                            updateTimeMillis = System.currentTimeMillis(),
                            category = category.removePrefix("android.intent.category.APP_"),
                            isEnabled = appInfo.enabled,
                            launchCount = 0,
                            lastLaunchTime = 0,
                            isFavorite = false
                        )
                        
                        apps.add(appInfoItem)
                    } catch (e: Exception) {
                        // Ignore individual failures
                    }
                }
            } catch (e: Exception) {
                // Ignore category failures
            }
        }
        
        return apps
    }
    
    private fun getWellKnownApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        
        val wellKnownPackages = listOf(
            "com.android.settings",
            "com.android.calculator2",
            "com.android.contacts",
            "com.android.dialer",
            "com.android.camera2",
            "com.android.gallery3d",
            "com.android.chrome",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.google.android.youtube",
            "com.whatsapp",
            "com.facebook.katana",
            "com.instagram.android",
            "com.twitter.android",
            "com.spotify.music",
            "com.netflix.mediaclient"
        )
        
        wellKnownPackages.forEach { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                
                val appInfoItem = AppInfo(
                    packageName = packageName,
                    appName = appName,
                    versionName = "Unknown",
                    versionCode = 0,
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    installTimeMillis = System.currentTimeMillis(),
                    updateTimeMillis = System.currentTimeMillis(),
                    category = "Popular",
                    isEnabled = appInfo.enabled,
                    launchCount = 0,
                    lastLaunchTime = 0,
                    isFavorite = false
                )
                
                apps.add(appInfoItem)
            } catch (e: Exception) {
                // App not installed, skip
            }
        }
        
        return apps
    }

    /**
     * Get installed apps from system (original method)
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
    
    /**
     * Android 15 Enhanced: Load apps with multiple discovery methods
     * Works around Android 15's restricted permissions for sideloaded apps
     */
    suspend fun loadAppsWithAndroid15Compatibility() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Loading apps with Android 15 compatibility mode")
        
        try {
            _isLoading.postValue(true)
            
            // Clear existing apps
            appDao.clearAllApps()
            
            val discoveredApps = mutableSetOf<AppInfo>()
            
            // Method 1: Standard launcher apps (always works)
            try {
                val launcherApps = getLauncherAppsAndroid15()
                discoveredApps.addAll(launcherApps)
                Log.d(TAG, "Android 15: Found ${launcherApps.size} launcher apps")
            } catch (e: Exception) {
                Log.w(TAG, "Error loading launcher apps", e)
            }
            
            // Method 2: Well-known apps via explicit package checks
            try {
                val wellKnownApps = getWellKnownInstalledApps()
                discoveredApps.addAll(wellKnownApps)
                Log.d(TAG, "Android 15: Found ${wellKnownApps.size} well-known apps")
            } catch (e: Exception) {
                Log.w(TAG, "Error loading well-known apps", e)
            }
            
            // Method 3: Apps discoverable via intent queries
            try {
                val intentApps = getAppsViaIntentQueries()
                discoveredApps.addAll(intentApps)
                Log.d(TAG, "Android 15: Found ${intentApps.size} intent-discoverable apps")
            } catch (e: Exception) {
                Log.w(TAG, "Error loading intent apps", e)
            }
            
            // Method 4: Try QUERY_ALL_PACKAGES if available (might work if user manually enabled)
            if (hasQueryAllPackagesPermission()) {
                try {
                    val allApps = getInstalledApps()
                    discoveredApps.addAll(allApps)
                    Log.d(TAG, "Android 15: QUERY_ALL_PACKAGES available, found ${allApps.size} total apps")
                } catch (e: Exception) {
                    Log.w(TAG, "QUERY_ALL_PACKAGES failed despite permission", e)
                }
            }
            
            // Remove duplicates and filter
            val uniqueApps = discoveredApps
                .distinctBy { it.packageName }
                .filter { !shouldHideApp(it) }
                .sortedBy { it.appName }
            
            Log.d(TAG, "Android 15: Total unique apps after filtering: ${uniqueApps.size}")
            
            if (uniqueApps.isNotEmpty()) {
                appDao.insertApps(uniqueApps)
                Log.d(TAG, "Android 15: Successfully inserted ${uniqueApps.size} apps")
            } else {
                Log.w(TAG, "Android 15: No apps discovered with any method")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in Android 15 compatibility loading", e)
            throw e
        } finally {
            _isLoading.postValue(false)
        }
    }
    
    /**
     * Get launcher apps - works on all Android versions including 15
     */
    private fun getLauncherAppsAndroid15(): List<AppInfo> {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            apps.mapNotNull { resolveInfo ->
                try {
                    createAppInfoFromResolveInfo(resolveInfo)
                } catch (e: Exception) {
                    Log.w(TAG, "Error creating AppInfo for ${resolveInfo.activityInfo?.packageName}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting launcher apps", e)
            emptyList()
        }
    }
    
    /**
     * Get well-known apps that are installed
     */
    private fun getWellKnownInstalledApps(): List<AppInfo> {
        val wellKnownPackages = listOf(
            // Popular social media
            "com.whatsapp", "com.facebook.katana", "com.instagram.android", 
            "com.twitter.android", "com.snapchat.android", "com.pinterest",
            "com.linkedin.android", "com.telegram.messenger", "com.viber.voip",
            "com.skype.raider",
            
            // Google apps
            "com.android.chrome", "com.google.android.apps.maps", "com.google.android.gm",
            "com.google.android.calendar", "com.google.android.contacts", 
            "com.google.android.apps.photos", "com.google.android.music",
            "com.google.android.apps.docs", "com.google.android.youtube",
            
            // Entertainment
            "com.spotify.music", "com.netflix.mediaclient", "com.amazon.mShop.android.shopping",
            "com.amazon.avod.thirdpartyclient", "com.hulu.plus", "com.disney.disneyplus",
            
            // Productivity
            "com.microsoft.office.word", "com.microsoft.office.excel", 
            "com.microsoft.office.powerpoint", "com.adobe.reader", 
            "com.dropbox.android", "com.evernote",
            
            // Transportation
            "com.uber.app", "com.lyft.android", "com.airbnb.android",
            
            // Finance
            "com.paypal.android.p2pmobile", "com.chase.sig.android",
            "com.bankofamerica.digitalwallet",
            
            // System apps
            "com.android.settings", "com.android.calculator2", "com.android.camera2",
            "com.android.gallery3d", "com.android.music", "com.android.vending"
        )
        
        return wellKnownPackages.mapNotNull { packageName ->
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                createAppInfoFromPackageInfo(packageInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                null // App not installed
            } catch (e: Exception) {
                Log.w(TAG, "Error getting info for $packageName", e)
                null
            }
        }
    }
    
    /**
     * Get apps discoverable via intent queries
     */
    private fun getAppsViaIntentQueries(): List<AppInfo> {
        val discoveredApps = mutableSetOf<AppInfo>()
        
        val queryIntents = listOf(
            // Browser apps
            Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("http://example.com")
            },
            Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("https://example.com")
            },
            
            // Camera apps
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(MediaStore.ACTION_VIDEO_CAPTURE),
            
            // Messaging/sharing apps
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" },
            Intent(Intent.ACTION_SEND).apply { type = "image/*" },
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/*" },
            
            // Phone apps
            Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:123") },
            Intent(Intent.ACTION_DIAL),
            
            // Email apps
            Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:") },
            Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("mailto:test@example.com") },
            
            // Maps/navigation apps
            Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("geo:0,0") },
            Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("google.navigation:q=destination") },
            
            // Music/media apps
            Intent(Intent.ACTION_VIEW).apply { type = "audio/*" },
            Intent(Intent.ACTION_VIEW).apply { type = "video/*" },
            Intent("android.intent.action.MUSIC_PLAYER"),
            
            // File/document apps
            Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" },
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" },
            Intent(Intent.ACTION_VIEW).apply { type = "application/pdf" },
            
            // Calculator apps
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CALCULATOR) },
            
            // Settings
            Intent(android.provider.Settings.ACTION_SETTINGS),
            Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),
            
            // Home/launcher apps
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        )
        
        queryIntents.forEach { intent ->
            try {
                val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                apps.forEach { resolveInfo ->
                    try {
                        val appInfo = createAppInfoFromResolveInfo(resolveInfo)
                        if (appInfo != null) {
                            discoveredApps.add(appInfo)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error creating AppInfo from intent query", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying intent: ${intent.action}", e)
            }
        }
        
        return discoveredApps.toList()
    }
    
    /**
     * Create AppInfo from ResolveInfo
     */
    private fun createAppInfoFromResolveInfo(resolveInfo: ResolveInfo): AppInfo? {
        return try {
            val activityInfo = resolveInfo.activityInfo ?: return null
            val packageName = activityInfo.packageName
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            
            createAppInfoFromPackageInfo(packageInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Error creating AppInfo from ResolveInfo", e)
            null
        }
    }
    
    /**
     * Create AppInfo from PackageInfo
     */
    private fun createAppInfoFromPackageInfo(packageInfo: PackageInfo): AppInfo? {
        return try {
            val applicationInfo = packageInfo.applicationInfo
            val packageName = packageInfo.packageName
            
            // Skip if this is our own app
            if (packageName == context.packageName) {
                return null
            }
            
            val appName = packageManager.getApplicationLabel(applicationInfo).toString()
            val versionName = packageInfo.versionName ?: "Unknown"
            val category = getCategoryName(applicationInfo.category)
            
            AppInfo(
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                category = category,
                isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isFavorite = false,
                usageCount = 0,
                lastUsedTime = 0L
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error creating AppInfo from PackageInfo", e)
            null
        }
    }
    
    private fun getCategoryName(category: Int): String {
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> "Game"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Image"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_MAPS -> "Maps"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> "Other"
        }
    }
    
} 