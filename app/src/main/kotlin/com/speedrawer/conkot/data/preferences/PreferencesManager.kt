package com.speedrawer.conkot.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // LiveData for theme changes
    private val _themeMode = MutableLiveData(themeMode)
    val themeModeLD: LiveData<Int> get() = _themeMode
    
    // StateFlow for reactive settings
    private val _iconSize = MutableStateFlow(iconSize)
    val iconSizeFlow: StateFlow<Float> get() = _iconSize
    
    // Theme settings
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) {
            prefs.edit().putInt(KEY_THEME_MODE, value).apply()
            _themeMode.value = value
        }
    
    // UI settings
    var iconSize: Float
        get() = prefs.getFloat(KEY_ICON_SIZE, 48f)
        set(value) {
            prefs.edit().putFloat(KEY_ICON_SIZE, value).apply()
            _iconSize.value = value
        }
    
    var backgroundOpacity: Float
        get() = prefs.getFloat(KEY_BACKGROUND_OPACITY, 0.9f)
        set(value) {
            prefs.edit().putFloat(KEY_BACKGROUND_OPACITY, value).apply()
        }
    
    // Search settings
    var fuzzySearchEnabled: Boolean
        get() = prefs.getBoolean(KEY_FUZZY_SEARCH, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FUZZY_SEARCH, value).apply()
        }
    
    var showMostUsed: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MOST_USED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_MOST_USED, value).apply()
        }
    
    var showKeyboard: Boolean
        get() = prefs.getBoolean(KEY_SHOW_KEYBOARD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_KEYBOARD, value).apply()
        }
    
    var showSearchHistory: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SEARCH_HISTORY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_SEARCH_HISTORY, value).apply()
        }
    
    var clearSearchOnClose: Boolean
        get() = prefs.getBoolean(KEY_CLEAR_SEARCH_ON_CLOSE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CLEAR_SEARCH_ON_CLOSE, value).apply()
        }
    
    // Behavior settings
    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_VIBRATION, value).apply()
        }
    
    var animationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATIONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ANIMATIONS, value).apply()
        }
    
    var autoFocus: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FOCUS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_FOCUS, value).apply()
        }
    
    // Search history
    var searchHistory: Set<String>
        get() = prefs.getStringSet(KEY_SEARCH_HISTORY, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_SEARCH_HISTORY, value).apply()
        }
    
    // Helper methods
    fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        
        val history = searchHistory.toMutableSet()
        history.add(query)
        
        // Keep only last 20 searches
        if (history.size > MAX_SEARCH_HISTORY) {
            searchHistory = history.toList().takeLast(MAX_SEARCH_HISTORY).toSet()
        } else {
            searchHistory = history
        }
    }
    
    fun clearSearchHistory() {
        searchHistory = emptySet()
    }
    
    fun getIconSizeLabel(): String {
        return when {
            iconSize <= 32f -> "Small"
            iconSize <= 48f -> "Medium"
            iconSize <= 64f -> "Large"
            else -> "Extra Large"
        }
    }
    
    fun getBackgroundOpacityLabel(): String {
        return "${(backgroundOpacity * 100).toInt()}%"
    }
    
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        
        // Notify observers
        _themeMode.value = themeMode
        _iconSize.value = iconSize
    }
    
    companion object {
        private const val PREFS_NAME = "conkot_prefs"
        private const val MAX_SEARCH_HISTORY = 20
        
        // Theme constants
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
        
        // Preference keys
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ICON_SIZE = "icon_size"
        private const val KEY_BACKGROUND_OPACITY = "background_opacity"
        private const val KEY_FUZZY_SEARCH = "fuzzy_search"
        private const val KEY_SHOW_MOST_USED = "show_most_used"
        private const val KEY_SHOW_KEYBOARD = "show_keyboard"
        private const val KEY_SHOW_SEARCH_HISTORY = "show_search_history"
        private const val KEY_CLEAR_SEARCH_ON_CLOSE = "clear_search_on_close"
        private const val KEY_VIBRATION = "vibration"
        private const val KEY_ANIMATIONS = "animations"
        private const val KEY_AUTO_FOCUS = "auto_focus"
        private const val KEY_SEARCH_HISTORY = "search_history"
    }
} 