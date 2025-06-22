package com.speedrawer.conkot.utils

object AppConstants {
    
    // Performance constants
    const val MAX_SEARCH_RESULTS = 50
    const val MAX_SEARCH_HISTORY = 20
    const val MAX_MOST_USED_APPS = 10
    const val DEBOUNCE_DELAY_MS = 100L
    const val ANIMATION_DURATION_MS = 150L
    
    // Icon sizes
    const val SMALL_ICON_SIZE = 32f
    const val MEDIUM_ICON_SIZE = 48f
    const val LARGE_ICON_SIZE = 64f
    const val EXTRA_LARGE_ICON_SIZE = 80f
    
    // Spacing
    const val ITEM_PADDING = 8f
    const val CARD_CORNER_RADIUS = 12f
    const val CARD_ELEVATION = 4f
    
    // Search configuration
    const val SEARCH_THRESHOLD = 0.6
    const val MIN_SEARCH_LENGTH = 1
    
    // Vibration durations
    const val VIBRATION_SHORT = 50L
    const val VIBRATION_MEDIUM = 100L
    const val VIBRATION_LONG = 200L
    
    // App categories
    val HIDDEN_PACKAGES = setOf(
        "com.android.launcher",
        "com.google.android.launcher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oneplus.launcher",
        "com.android.settings",
        "com.android.packageinstaller"
    )
    
    // Database
    const val DATABASE_NAME = "app_database"
    const val DATABASE_VERSION = 1
    
    // Cleanup
    const val CLEANUP_DAYS = 30
    const val MAX_ICON_CACHE_SIZE = 100
} 