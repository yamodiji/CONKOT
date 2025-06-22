package com.speedrawer.conkot

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.speedrawer.conkot.data.repository.AppRepository
import com.speedrawer.conkot.data.database.AppDatabase
import com.speedrawer.conkot.data.preferences.PreferencesManager

class App : Application() {
    
    companion object {
        private const val TAG = "SpeedDrawerApp"
        @Volatile
        private var instance: App? = null
        
        fun getInstance(): App {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
        
        fun getContext(): Context {
            return getInstance().applicationContext
        }
    }
    
    // Lazy initialization for better performance with error handling
    val database by lazy { 
        try {
            AppDatabase.getDatabase(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database", e)
            throw e
        }
    }
    
    val repository by lazy { 
        try {
            AppRepository(this, database.appDao())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
            throw e
        }
    }
    
    val preferencesManager by lazy { 
        try {
            PreferencesManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize preferences manager", e)
            throw e
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate started")
        
        try {
            instance = this
            
            // Initialize background components safely
            initializeApp()
            
            Log.d(TAG, "Application onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in Application onCreate", e)
            // Don't throw here as it would crash the entire app
            // Instead, let individual components handle their own errors
        }
    }
    
    private fun initializeApp() {
        try {
            Log.d(TAG, "Starting background initialization")
            
            // Pre-load app data in background with error handling
            Thread {
                try {
                    Log.d(TAG, "Background thread started for app data loading")
                    kotlinx.coroutines.runBlocking {
                        repository.refreshInstalledApps()
                    }
                    Log.d(TAG, "App data loading completed successfully")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception during app loading - missing permissions?", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during background app loading", e)
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting background initialization", e)
        }
    }
} 