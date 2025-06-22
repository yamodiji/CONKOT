package com.speedrawer.conkot.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.speedrawer.conkot.R

class PermissionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PermissionActivity"
        private const val REQUEST_MANAGE_ALL_FILES = 100
        private const val REQUEST_SYSTEM_ALERT_WINDOW = 101
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "PermissionActivity created")
        
        showPermissionDialog()
    }
    
    private fun showPermissionDialog() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Enable App Discovery")
                .setMessage("""
                    To show your installed apps, CONKOT needs special permissions.
                    
                    On Android ${Build.VERSION.RELEASE}, you need to:
                    
                    1. Enable "Display over other apps"
                    2. Grant "All files access" (if available)
                    3. Allow "Query all packages"
                    
                    This allows CONKOT to discover and display your installed applications.
                """.trimIndent())
                .setPositiveButton("Enable Permissions") { _, _ ->
                    requestPermissions()
                }
                .setNegativeButton("Skip") { _, _ ->
                    showSkipWarning()
                }
                .setNeutralButton("Manual Setup") { _, _ ->
                    showManualInstructions()
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing permission dialog", e)
            finish()
        }
    }
    
    private fun requestPermissions() {
        try {
            Log.d(TAG, "Requesting permissions for Android ${Build.VERSION.SDK_INT}")
            
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Android 11+ (including Android 15)
                    requestAndroid11Permissions()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // Android 6-10
                    requestSystemAlertWindowPermission()
                }
                else -> {
                    // Android 5 and below
                    showSuccessAndFinish()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permissions", e)
            showErrorAndFinish()
        }
    }
    
    private fun requestAndroid11Permissions() {
        try {
            // First, try to request "All files access" permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    Log.d(TAG, "Requesting All Files Access permission")
                    startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES)
                } else {
                    // Fallback to system alert window permission
                    requestSystemAlertWindowPermission()
                }
            } else {
                requestSystemAlertWindowPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Android 11+ permissions", e)
            requestSystemAlertWindowPermission()
        }
    }
    
    private fun requestSystemAlertWindowPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "Requesting System Alert Window permission")
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, REQUEST_SYSTEM_ALERT_WINDOW)
                } else {
                    Log.d(TAG, "System Alert Window permission already granted")
                    showSuccessAndFinish()
                }
            } else {
                showSuccessAndFinish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting System Alert Window permission", e)
            showManualInstructions()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d(TAG, "Permission result: requestCode=$requestCode, resultCode=$resultCode")
        
        when (requestCode) {
            REQUEST_MANAGE_ALL_FILES -> {
                // Check if permission was granted, then request overlay permission
                requestSystemAlertWindowPermission()
            }
            REQUEST_SYSTEM_ALERT_WINDOW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    showSuccessAndFinish()
                } else {
                    showManualInstructions()
                }
            }
        }
    }
    
    private fun showSkipWarning() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Limited Functionality")
                .setMessage("""
                    Without these permissions, CONKOT may not be able to discover all your installed apps.
                    
                    You can enable permissions later in:
                    Settings → Apps → CONKOT → Permissions
                """.trimIndent())
                .setPositiveButton("Continue Anyway") { _, _ ->
                    showSuccessAndFinish()
                }
                .setNegativeButton("Grant Permissions") { _, _ ->
                    requestPermissions()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing skip warning", e)
            finish()
        }
    }
    
    private fun showManualInstructions() {
        try {
            val instructions = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    """
                    For Android ${Build.VERSION.RELEASE}:
                    
                    1. Go to Settings → Apps → CONKOT
                    2. Tap "Permissions" or "App permissions"
                    3. Enable "Display over other apps"
                    4. Enable "All files access" (if available)
                    5. Look for "Special app access" and enable all relevant permissions
                    
                    Alternative path:
                    Settings → Privacy → Special app access → Display over other apps → CONKOT → Enable
                    """.trimIndent()
                }
                else -> {
                    """
                    For your Android version:
                    
                    1. Go to Settings → Apps → CONKOT
                    2. Tap "Permissions"
                    3. Enable all requested permissions
                    4. Look for "Draw over other apps" and enable it
                    """.trimIndent()
                }
            }
            
            AlertDialog.Builder(this)
                .setTitle("Manual Permission Setup")
                .setMessage(instructions)
                .setPositiveButton("Open Settings") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Done") { _, _ ->
                    showSuccessAndFinish()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing manual instructions", e)
            finish()
        }
    }
    
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings", e)
            Toast.makeText(this, "Please manually go to Settings → Apps → CONKOT", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun showSuccessAndFinish() {
        try {
            Log.d(TAG, "Permissions setup completed")
            Toast.makeText(this, "Permissions configured. Restarting app...", Toast.LENGTH_SHORT).show()
            
            // Start MainActivity and finish this activity
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing success", e)
            finish()
        }
    }
    
    private fun showErrorAndFinish() {
        try {
            Toast.makeText(this, "Permission setup failed. Please try manually.", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error message", e)
            finish()
        }
    }
} 