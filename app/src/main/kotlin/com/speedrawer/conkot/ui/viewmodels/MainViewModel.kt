package com.speedrawer.conkot.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.speedrawer.conkot.App
import com.speedrawer.conkot.data.models.AppInfo
import com.speedrawer.conkot.data.repository.AppRepository
import com.speedrawer.conkot.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as App
    private val repository: AppRepository = app.repository
    val preferencesManager: PreferencesManager = app.preferencesManager
    
    // Loading state
    val isLoading: LiveData<Boolean> = repository.isLoading
    
    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    // All apps
    val allApps: Flow<List<AppInfo>> = repository.allApps
    
    // Filtered apps based on search
    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _searchQuery,
        allApps
    ) { query, apps ->
        if (query.isEmpty()) {
            apps.take(50) // Limit for performance
        } else {
            apps.filter { it.matchesQuery(query) }
                .sortedWith(
                    compareByDescending<AppInfo> { it.isFavorite }
                        .thenByDescending { it.calculateSearchScore(query) }
                        .thenByDescending { it.launchCount }
                        .thenBy { it.displayName.lowercase() }
                )
                .take(50)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Favorite apps
    val favoriteApps: Flow<List<AppInfo>> = repository.favoriteApps
    
    // Most used apps
    val mostUsedApps: Flow<List<AppInfo>> = app.database.appDao().getMostUsedApps()
    
    // Settings
    val iconSize: StateFlow<Float> = preferencesManager.iconSizeFlow
    val showMostUsed: Boolean get() = preferencesManager.showMostUsed
    val showKeyboard: Boolean get() = preferencesManager.showKeyboard
    val vibrationEnabled: Boolean get() = preferencesManager.vibrationEnabled
    val animationsEnabled: Boolean get() = preferencesManager.animationsEnabled
    val fuzzySearchEnabled: Boolean get() = preferencesManager.fuzzySearchEnabled
    
    // Search history
    val searchHistory: Set<String> get() = preferencesManager.searchHistory
    
    init {
        // Load apps on initialization
        refreshApps()
    }
    
    /**
     * Search for apps
     */
    fun search(query: String) {
        _searchQuery.value = query.trim()
        
        // Add to search history if not empty
        if (query.isNotBlank() && query.length >= 2) {
            preferencesManager.addToSearchHistory(query)
        }
    }
    
    /**
     * Clear search
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    /**
     * Launch an app
     */
    fun launchApp(app: AppInfo) {
        viewModelScope.launch {
            val success = repository.launchApp(app)
            
            if (success && vibrationEnabled) {
                repository.vibrate(50)
            }
        }
    }
    
    /**
     * Toggle favorite status
     */
    fun toggleFavorite(app: AppInfo) {
        viewModelScope.launch {
            repository.toggleFavorite(app)
            
            if (vibrationEnabled) {
                repository.vibrate(100)
            }
        }
    }
    
    /**
     * Refresh apps from system
     */
    fun refreshApps() {
        viewModelScope.launch {
            repository.refreshInstalledApps()
        }
    }
    
    /**
     * Get app icon
     */
    fun getAppIcon(app: AppInfo) = repository.getAppIcon(app)
    
    /**
     * Provide haptic feedback
     */
    fun vibrate(duration: Long = 50) {
        if (vibrationEnabled) {
            repository.vibrate(duration)
        }
    }
    
    /**
     * Clear search history
     */
    fun clearSearchHistory() {
        preferencesManager.clearSearchHistory()
    }
    
    /**
     * Get display apps based on current state
     */
    fun getDisplayApps(): Flow<List<AppInfo>> {
        return filteredApps
    }
    
    /**
     * Handle app long press (show options)
     */
    fun onAppLongPress(app: AppInfo) {
        if (vibrationEnabled) {
            repository.vibrate(100)
        }
    }
    
    /**
     * Handle search from history
     */
    fun searchFromHistory(query: String) {
        search(query)
    }
} 