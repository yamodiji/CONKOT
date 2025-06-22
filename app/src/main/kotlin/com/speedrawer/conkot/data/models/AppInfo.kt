package com.speedrawer.conkot.data.models

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore

@Entity(tableName = "apps")
data class AppInfo(
    @PrimaryKey
    val packageName: String,
    
    val appName: String,
    val systemAppName: String? = null,
    val versionName: String? = null,
    val versionCode: Long = 0,
    val isSystemApp: Boolean = false,
    val installTimeMillis: Long = 0,
    val updateTimeMillis: Long = 0,
    val category: String = "Unknown",
    val isEnabled: Boolean = true,
    
    // Performance tracking
    var launchCount: Int = 0,
    var lastLaunchTime: Long = 0,
    var isFavorite: Boolean = false,
    var searchScore: Double = 0.0
) {
    
    @Ignore
    var icon: Drawable? = null
    
    @Ignore
    var isHidden: Boolean = false
    
    // Computed properties
    val displayName: String
        get() = if (appName.isNotEmpty()) appName else (systemAppName ?: packageName)
    
    val isLauncher: Boolean
        get() = packageName.contains("launcher", ignoreCase = true) || 
                category.contains("launcher", ignoreCase = true)
    
    val shouldHide: Boolean
        get() = isSystemApp && (
            packageName.startsWith("com.android.") ||
            packageName.startsWith("com.google.android.") ||
            packageName.contains("packageinstaller") ||
            packageName.contains("wallpaper")
        )
    
    // Factory method from Android PackageInfo
    companion object {
        fun fromPackageInfo(
            packageInfo: PackageInfo,
            applicationInfo: ApplicationInfo,
            appName: String
        ): AppInfo {
            return AppInfo(
                packageName = packageInfo.packageName,
                appName = appName,
                systemAppName = applicationInfo.loadLabel(null)?.toString(),
                versionName = packageInfo.versionName,
                versionCode = packageInfo.longVersionCode,
                isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                installTimeMillis = packageInfo.firstInstallTime,
                updateTimeMillis = packageInfo.lastUpdateTime,
                category = getCategoryName(applicationInfo.category),
                isEnabled = applicationInfo.enabled
            )
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
    
    // Search matching
    fun matchesQuery(query: String): Boolean {
        if (query.isEmpty()) return true
        
        val lowerQuery = query.lowercase()
        val lowerAppName = appName.lowercase()
        val lowerPackageName = packageName.lowercase()
        val lowerSystemName = systemAppName?.lowercase() ?: ""
        
        return lowerAppName.contains(lowerQuery) ||
               lowerPackageName.contains(lowerQuery) ||
               lowerSystemName.contains(lowerQuery)
    }
    
    // Fuzzy search scoring
    fun calculateSearchScore(query: String): Double {
        if (query.isEmpty()) return 1.0
        
        val lowerQuery = query.lowercase()
        val lowerAppName = appName.lowercase()
        val lowerPackageName = packageName.lowercase()
        
        return when {
            lowerAppName.startsWith(lowerQuery) -> 1.0
            lowerAppName.contains(lowerQuery) -> 0.8
            lowerPackageName.contains(lowerQuery) -> 0.6
            systemAppName?.lowercase()?.contains(lowerQuery) == true -> 0.4
            else -> 0.0
        }
    }
    
    // For debugging
    override fun toString(): String {
        return "AppInfo(name=$appName, package=$packageName, favorite=$isFavorite, launches=$launchCount)"
    }
} 