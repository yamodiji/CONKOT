package com.speedrawer.conkot

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.speedrawer.conkot.data.repository.AppRepository
import com.speedrawer.conkot.data.database.AppDatabase
import com.speedrawer.conkot.data.preferences.PreferencesManager

class App : Application() {
    
    // Lazy initialization for better performance
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { AppRepository(this, database.appDao()) }
    val preferencesManager by lazy { PreferencesManager(this) }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize background components
        initializeApp()
    }
    
    private fun initializeApp() {
        // Pre-load app data in background
        Thread {
            try {
                repository.refreshInstalledApps()
            } catch (e: Exception) {
                // Handle initialization errors gracefully
            }
        }.start()
    }
    
    companion object {
        @Volatile
        private var instance: App? = null
        
        fun getInstance(): App {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
        
        fun getContext(): Context {
            return getInstance().applicationContext
        }
    }
} 