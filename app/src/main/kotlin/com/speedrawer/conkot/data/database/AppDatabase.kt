package com.speedrawer.conkot.data.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.speedrawer.conkot.data.models.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [AppInfo::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun appDao(): AppDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback())
                .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                
                // Create indexes for better performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_apps_favorite ON apps(isFavorite)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_apps_launch_count ON apps(launchCount)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_apps_name ON apps(appName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_apps_enabled ON apps(isEnabled)")
            }
            
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                
                // Optimize database settings
                db.execSQL("PRAGMA journal_mode=WAL")
                db.execSQL("PRAGMA synchronous=NORMAL")
                db.execSQL("PRAGMA cache_size=10000")
                db.execSQL("PRAGMA temp_store=MEMORY")
            }
        }
    }
} 