package com.speedrawer.conkot.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.speedrawer.conkot.data.models.AppInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    
    @Query("SELECT * FROM apps WHERE isEnabled = 1 ORDER BY isFavorite DESC, launchCount DESC, appName ASC")
    fun getAllApps(): Flow<List<AppInfo>>
    
    @Query("SELECT * FROM apps WHERE isEnabled = 1 ORDER BY isFavorite DESC, launchCount DESC, appName ASC")
    fun getAllAppsLiveData(): LiveData<List<AppInfo>>
    
    @Query("SELECT * FROM apps WHERE isFavorite = 1 ORDER BY launchCount DESC, appName ASC")
    fun getFavoriteApps(): Flow<List<AppInfo>>
    
    @Query("SELECT * FROM apps WHERE launchCount > 0 ORDER BY launchCount DESC, lastLaunchTime DESC LIMIT :limit")
    fun getMostUsedApps(limit: Int = 10): Flow<List<AppInfo>>
    
    @Query("SELECT * FROM apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getApp(packageName: String): AppInfo?
    
    @Query("SELECT * FROM apps WHERE (appName LIKE '%' || :query || '%' OR packageName LIKE '%' || :query || '%' OR systemAppName LIKE '%' || :query || '%') AND isEnabled = 1 ORDER BY isFavorite DESC, launchCount DESC, appName ASC")
    fun searchApps(query: String): Flow<List<AppInfo>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppInfo)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<AppInfo>)
    
    @Update
    suspend fun updateApp(app: AppInfo)
    
    @Delete
    suspend fun deleteApp(app: AppInfo)
    
    @Query("DELETE FROM apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)
    
    @Query("DELETE FROM apps")
    suspend fun deleteAllApps()
    
    @Query("UPDATE apps SET isFavorite = :isFavorite WHERE packageName = :packageName")
    suspend fun updateFavoriteStatus(packageName: String, isFavorite: Boolean)
    
    @Query("UPDATE apps SET launchCount = launchCount + 1, lastLaunchTime = :launchTime WHERE packageName = :packageName")
    suspend fun incrementLaunchCount(packageName: String, launchTime: Long)
    
    @Query("SELECT COUNT(*) FROM apps")
    suspend fun getAppCount(): Int
    
    @Query("SELECT COUNT(*) FROM apps WHERE isFavorite = 1")
    suspend fun getFavoriteCount(): Int
    
    // Cleanup old or unused data
    @Query("DELETE FROM apps WHERE lastLaunchTime < :cutoffTime AND launchCount = 0 AND isFavorite = 0")
    suspend fun cleanupOldApps(cutoffTime: Long)
} 