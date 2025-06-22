package com.speedrawer.conkot.ui.activities

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.speedrawer.conkot.R

class PermissionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PermissionActivity"
        private const val REQUEST_QUERY_ALL_PACKAGES = 1001
        private const val REQUEST_MANAGE_ALL_FILES = 1002
        private const val REQUEST_SYSTEM_ALERT_WINDOW = 1003
    }
    
    private val manageAllFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Manage All Files result: ${result.resultCode}")
        checkAndRequestNextPermission()
    }
    
    private val systemOverlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "System Overlay result: ${result.resultCode}")
        checkAndRequestNextPermission()
    }
    
    private val specialAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Special Access result: ${result.resultCode}")
        checkAndRequestNextPermission()
    }
    
    private var currentStep = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "PermissionActivity created for Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        
        showInitialDialog()
    }
    
    private fun showInitialDialog() {
        val androidVersion = when (Build.VERSION.SDK_INT) {
            35 -> "15"
            34 -> "14"
            33 -> "13"
            32 -> "12L"
            31 -> "12"
            30 -> "11"
            else -> Build.VERSION.RELEASE
        }
        
        AlertDialog.Builder(this)
            .setTitle("Setup Required for Android $androidVersion")
            .setMessage("""
                CONKOT needs special permissions to discover your apps.
                
                On Android $androidVersion, we need to enable:
                
                ✓ Query All Packages - To see all your apps
                ✓ Display Over Other Apps - For overlay features
                ✓ All Files Access - For app data access
                
                This is a one-time setup that takes about 30 seconds.
            """.trimIndent())
            .setPositiveButton("Start Setup") { _, _ ->
                startPermissionProcess()
            }
            .setNegativeButton("Manual Setup") { _, _ ->
                showManualInstructions()
            }
            .setNeutralButton("Skip") { _, _ ->
                showSkipWarning()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startPermissionProcess() {
        currentStep = 0
        checkAndRequestNextPermission()
    }
    
    private fun checkAndRequestNextPermission() {
        when (currentStep) {
            0 -> {
                // Step 1: QUERY_ALL_PACKAGES permission
                if (!hasQueryAllPackagesPermission()) {
                    requestQueryAllPackagesPermission()
                } else {
                    Log.d(TAG, "QUERY_ALL_PACKAGES already granted")
                    currentStep++
                    checkAndRequestNextPermission()
                }
            }
            1 -> {
                // Step 2: MANAGE_EXTERNAL_STORAGE (All Files Access)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    requestManageAllFilesPermission()
                } else {
                    Log.d(TAG, "All Files Access not needed or already granted")
                    currentStep++
                    checkAndRequestNextPermission()
                }
            }
            2 -> {
                // Step 3: System Alert Window
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    requestSystemAlertWindowPermission()
                } else {
                    Log.d(TAG, "System Alert Window not needed or already granted")
                    currentStep++
                    checkAndRequestNextPermission()
                }
            }
            else -> {
                // All permissions processed
                showCompletionDialog()
            }
        }
    }
    
    private fun hasQueryAllPackagesPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+, check if we can query all packages
                val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
                
                // Also check if the permission is granted in manifest
                val permissionGranted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.QUERY_ALL_PACKAGES
                ) == PackageManager.PERMISSION_GRANTED
                
                Log.d(TAG, "Query All Packages - AppOps mode: $mode, Permission granted: $permissionGranted")
                
                // For Android 15, be more lenient in checking
                if (Build.VERSION.SDK_INT >= 35) {
                    return permissionGranted || mode == AppOpsManager.MODE_ALLOWED
                }
                
                return permissionGranted && mode == AppOpsManager.MODE_ALLOWED
            } else {
                // For older Android versions, just check the permission
                return ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.QUERY_ALL_PACKAGES
                ) == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking QUERY_ALL_PACKAGES permission", e)
            false
        }
    }
    
    private fun requestQueryAllPackagesPermission() {
        try {
            Log.d(TAG, "Requesting QUERY_ALL_PACKAGES permission")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+, show dialog and direct to settings
                AlertDialog.Builder(this)
                    .setTitle("Enable App Discovery")
                    .setMessage("""
                        To show all your installed apps, please:
                        
                        1. Tap "Open Settings" below
                        2. Find "Special app access" or "Advanced"
                        3. Look for "Display over other apps" or "Query all packages"
                        4. Find CONKOT and enable it
                        5. Press back to return here
                        
                        This allows CONKOT to see all your apps.
                    """.trimIndent())
                    .setPositiveButton("Open Settings") { _, _ ->
                        openSpecialAppAccessSettings()
                    }
                    .setNegativeButton("Try Alternative") { _, _ ->
                        openAppDetailsSettings()
                    }
                    .setNeutralButton("Skip") { _, _ ->
                        currentStep++
                        checkAndRequestNextPermission()
                    }
                    .show()
            } else {
                // For older versions, try runtime permission request
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.QUERY_ALL_PACKAGES),
                    REQUEST_QUERY_ALL_PACKAGES
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting QUERY_ALL_PACKAGES permission", e)
            currentStep++
            checkAndRequestNextPermission()
        }
    }
    
    private fun requestManageAllFilesPermission() {
        try {
            Log.d(TAG, "Requesting MANAGE_EXTERNAL_STORAGE permission")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AlertDialog.Builder(this)
                    .setTitle("Enable All Files Access")
                    .setMessage("""
                        For better app data access:
                        
                        1. Tap "Open Settings" below
                        2. Enable "All files access" for CONKOT
                        3. Press back to continue
                        
                        This helps CONKOT access app information more efficiently.
                    """.trimIndent())
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            manageAllFilesLauncher.launch(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening all files access settings", e)
                            currentStep++
                            checkAndRequestNextPermission()
                        }
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        currentStep++
                        checkAndRequestNextPermission()
                    }
                    .show()
            } else {
                currentStep++
                checkAndRequestNextPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting MANAGE_EXTERNAL_STORAGE permission", e)
            currentStep++
            checkAndRequestNextPermission()
        }
    }
    
    private fun requestSystemAlertWindowPermission() {
        try {
            Log.d(TAG, "Requesting SYSTEM_ALERT_WINDOW permission")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AlertDialog.Builder(this)
                    .setTitle("Enable Display Over Other Apps")
                    .setMessage("""
                        For overlay features:
                        
                        1. Tap "Open Settings" below
                        2. Enable "Display over other apps" for CONKOT
                        3. Press back to finish setup
                        
                        This allows CONKOT to show floating elements.
                    """.trimIndent())
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            systemOverlayLauncher.launch(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening overlay permission settings", e)
                            currentStep++
                            checkAndRequestNextPermission()
                        }
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        currentStep++
                        checkAndRequestNextPermission()
                    }
                    .show()
            } else {
                currentStep++
                checkAndRequestNextPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting SYSTEM_ALERT_WINDOW permission", e)
            currentStep++
            checkAndRequestNextPermission()
        }
    }
    
    private fun openSpecialAppAccessSettings() {
        try {
            // Try multiple paths to get to special app access
            val intents = listOf(
                Intent("android.settings.SPECIAL_APP_ACCESS_SETTINGS"),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                },
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
            
            for (intent in intents) {
                if (intent.resolveActivity(packageManager) != null) {
                    specialAccessLauncher.launch(intent)
                    return
                }
            }
            
            // Fallback to app details
            openAppDetailsSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening special app access settings", e)
            openAppDetailsSettings()
        }
    }
    
    private fun openAppDetailsSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            specialAccessLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app details settings", e)
            currentStep++
            checkAndRequestNextPermission()
        }
    }
    
    private fun showCompletionDialog() {
        val hasQueryAllPackages = hasQueryAllPackagesPermission()
        val hasAllFilesAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        val hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        
        val message = buildString {
            appendLine("Permission Setup Complete!")
            appendLine()
            appendLine("Status:")
            appendLine("${if (hasQueryAllPackages) "✓" else "✗"} Query All Packages")
            appendLine("${if (hasAllFilesAccess) "✓" else "✗"} All Files Access")
            appendLine("${if (hasOverlayPermission) "✓" else "✗"} Display Over Other Apps")
            appendLine()
            if (!hasQueryAllPackages) {
                appendLine("⚠️ Without Query All Packages permission, you may see limited apps.")
                appendLine("You can enable this later in Settings → Apps → CONKOT → Permissions")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Setup Complete")
            .setMessage(message)
            .setPositiveButton("Open CONKOT") { _, _ ->
                finishAndOpenMainApp()
            }
            .setNegativeButton("Retry Setup") { _, _ ->
                showManualInstructions()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showSkipWarning() {
        AlertDialog.Builder(this)
            .setTitle("Limited Functionality Warning")
            .setMessage("""
                Without the required permissions, CONKOT will have limited functionality:
                
                • May only show a few apps instead of all installed apps
                • Some features may not work properly
                • Performance may be reduced
                
                You can enable permissions later in:
                Settings → Apps → CONKOT → Permissions
            """.trimIndent())
            .setPositiveButton("Continue Anyway") { _, _ ->
                finishAndOpenMainApp()
            }
            .setNegativeButton("Grant Permissions") { _, _ ->
                startPermissionProcess()
            }
            .show()
    }
    
    private fun showManualInstructions() {
        val instructions = when {
            Build.VERSION.SDK_INT >= 35 -> {
                """
                Manual Setup for Android 15:
                
                1. Go to Settings → Apps → CONKOT
                2. Tap "Permissions" or "App permissions"
                3. Enable "Query all packages" if available
                4. Go back, tap "Special app access"
                5. Find "Display over other apps" → Enable for CONKOT
                6. Find "All files access" → Enable for CONKOT
                7. Return to CONKOT
                
                Alternative path:
                Settings → Privacy → Special app access → Display over other apps → CONKOT → Enable
                
                If you can't find these options, try:
                Settings → Security → Special app access
                """
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                """
                Manual Setup for Android ${Build.VERSION.RELEASE}:
                
                1. Go to Settings → Apps → CONKOT
                2. Tap "Permissions"
                3. Enable all available permissions
                4. Look for "Special app access" or "Advanced"
                5. Enable "Display over other apps"
                6. Enable "All files access" if available
                """
            }
            else -> {
                """
                Manual Setup:
                
                1. Go to Settings → Apps → CONKOT
                2. Tap "Permissions"
                3. Enable all requested permissions
                4. Look for "Draw over other apps" and enable it
                """
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Manual Permission Setup")
            .setMessage(instructions.trimIndent())
            .setPositiveButton("I've Done This") { _, _ ->
                finishAndOpenMainApp()
            }
            .setNegativeButton("Guided Setup") { _, _ ->
                startPermissionProcess()
            }
            .show()
    }
    
    private fun finishAndOpenMainApp() {
        Toast.makeText(this, "Opening CONKOT...", Toast.LENGTH_SHORT).show()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        Log.d(TAG, "Permission result: requestCode=$requestCode")
        
        when (requestCode) {
            REQUEST_QUERY_ALL_PACKAGES -> {
                currentStep++
                checkAndRequestNextPermission()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // When returning from settings, continue with the permission process
        if (currentStep > 0) {
            android.os.Handler(mainLooper).postDelayed({
                currentStep++
                checkAndRequestNextPermission()
            }, 500)
        }
    }
} 