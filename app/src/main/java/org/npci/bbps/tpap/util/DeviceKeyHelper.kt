package org.npci.bbps.tpap.util

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper to get device public key from callable app via ContentProvider
 * This allows TPAP app to register the device using the callable app's public key
 */
object DeviceKeyHelper {

    private const val AUTHORITY = "org.npci.bbps.callableui.provider"
    private const val CALLABLE_APP_PACKAGE = "org.npci.bbps.callableui"
    private const val PUBLIC_KEY_PATH = "/publicKey"
    private val CONTENT_URI = Uri.parse("content://$AUTHORITY$PUBLIC_KEY_PATH")

    /**
     * Debug helper: List all installed packages containing "callable" or "bbps"
     */
    fun listInstalledPackages(packageManager: PackageManager): List<String> {
        val packages = mutableListOf<String>()
        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            Log.d("DeviceKeyHelper", "Total installed packages: ${installedPackages.size}")
            
            for (packageInfo in installedPackages) {
                val packageName = packageInfo.packageName
                val appName = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: "Unknown"
                
                // Log all packages with "callable" or "bbps" in name or package
                if (packageName.contains("callable", ignoreCase = true) || 
                    packageName.contains("bbps", ignoreCase = true) ||
                    appName.contains("callable", ignoreCase = true) ||
                    appName.contains("bbps", ignoreCase = true)) {
                    packages.add(packageName)
                    Log.d("DeviceKeyHelper", "Found matching package: $packageName (app name: $appName)")
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceKeyHelper", "Error listing packages", e)
        }
        return packages
    }

    /**
     * Check if callable app is installed
     */
    fun isCallableAppInstalled(packageManager: PackageManager): Boolean {
        // First, list all matching packages for debugging
        val matchingPackages = listInstalledPackages(packageManager)
        Log.d("DeviceKeyHelper", "=== DEBUG: Checking for callable app ===")
        Log.d("DeviceKeyHelper", "Expected package: $CALLABLE_APP_PACKAGE")
        Log.d("DeviceKeyHelper", "Found packages containing 'callable' or 'bbps': $matchingPackages")
        
        // Check if exact package is in the list
        if (matchingPackages.contains(CALLABLE_APP_PACKAGE)) {
            Log.d("DeviceKeyHelper", "✓ Exact package found in installed packages list!")
        } else {
            Log.w("DeviceKeyHelper", "✗ Exact package NOT in installed packages list")
        }
        
        return try {
            // Try multiple methods to check if app is installed
            // Method 1: getPackageInfo with MATCH_UNINSTALLED_PACKAGES flag
            Log.d("DeviceKeyHelper", "Attempting getPackageInfo for: $CALLABLE_APP_PACKAGE")
            val packageInfo = packageManager.getPackageInfo(
                CALLABLE_APP_PACKAGE,
                PackageManager.GET_ACTIVITIES or PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
            
            // Also verify the app is actually installed (not just uninstalled)
            val applicationInfo = packageInfo.applicationInfo
            val isInstalled = applicationInfo?.enabled ?: false
            Log.d("DeviceKeyHelper", "✓ Callable app found: package=$CALLABLE_APP_PACKAGE, enabled=$isInstalled")
            
            isInstalled
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("DeviceKeyHelper", "✗ Callable app not found: $CALLABLE_APP_PACKAGE")
            Log.e("DeviceKeyHelper", "Expected package: $CALLABLE_APP_PACKAGE")
            Log.e("DeviceKeyHelper", "Found packages: $matchingPackages")
            if (matchingPackages.isEmpty()) {
                Log.e("DeviceKeyHelper", "No packages found. Please install the callable UI app.")
            } else {
                Log.e("DeviceKeyHelper", "Found similar packages but not the exact one. Check package name.")
                Log.e("DeviceKeyHelper", "Maybe the app is installed with a different package name?")
            }
            false
        } catch (e: Exception) {
            Log.e("DeviceKeyHelper", "Error checking if callable app is installed", e)
            // Fallback: try to query ContentProvider directly
            false
        }
    }

    /**
     * Get the device public key from the callable app
     * Tries ContentProvider first (works even if app not running), then BroadcastReceiver as fallback
     * @return Base64 encoded public key, or null if unavailable
     */
    fun getPublicKey(context: Context): String? {
        // Try ContentProvider first (more reliable, works even if app not running)
        val contentProviderKey = getPublicKeyFromContentProvider(context.contentResolver)
        if (contentProviderKey != null) {
            return contentProviderKey
        }
        
        // Fallback to BroadcastReceiver
        Log.d("DeviceKeyHelper", "ContentProvider failed, trying BroadcastReceiver...")
        return try {
            var publicKey: String? = null
            val lock = Object()
            var received = false
            
            // Register receiver for response
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == "org.npci.bbps.callableui.PUBLIC_KEY_RESPONSE") {
                        val receivedKey = intent.getStringExtra("publicKey")
                        if (!receivedKey.isNullOrBlank()) {
                            publicKey = receivedKey
                            Log.d("DeviceKeyHelper", "✓ Received public key via BroadcastReceiver (length: ${publicKey?.length ?: 0})")
                        }
                        synchronized(lock) {
                            received = true
                            lock.notifyAll()
                        }
                    }
                }
            }
            
            val filter = IntentFilter("org.npci.bbps.callableui.PUBLIC_KEY_RESPONSE")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            
            try {
                // Send request broadcast
                val requestIntent = Intent("org.npci.bbps.tpap.REQUEST_PUBLIC_KEY").apply {
                    setPackage("org.npci.bbps.callableui")
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(requestIntent, null)
                Log.d("DeviceKeyHelper", "Sent broadcast request to callable app")
                
                // Wait for response (max 3 seconds)
                synchronized(lock) {
                    if (!received) {
                        lock.wait(3000)
                    }
                }
            } finally {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Ignore if already unregistered
                }
            }
            
            publicKey
        } catch (e: Exception) {
            Log.e("DeviceKeyHelper", "Error getting public key via BroadcastReceiver", e)
            null
        }
    }

    /**
     * Get the device public key from the callable app's ContentProvider (fallback method)
     * @return Base64 encoded public key, or null if unavailable
     */
    private fun getPublicKeyFromContentProvider(contentResolver: ContentResolver): String? {
        return try {
            Log.d("DeviceKeyHelper", "Querying ContentProvider: $CONTENT_URI")
            Log.d("DeviceKeyHelper", "Authority: $AUTHORITY, Path: $PUBLIC_KEY_PATH")
            
            // Try querying the ContentProvider
            val cursor: Cursor? = try {
                contentResolver.query(
                    CONTENT_URI,
                    arrayOf("publicKey"),
                    null,
                    null,
                    null
                )
            } catch (e: IllegalArgumentException) {
                // This usually means the ContentProvider authority is not found
                Log.e("DeviceKeyHelper", "IllegalArgumentException: ContentProvider authority not found", e)
                Log.e("DeviceKeyHelper", "URI: $CONTENT_URI")
                Log.e("DeviceKeyHelper", "This usually means:")
                Log.e("DeviceKeyHelper", "1. The callable app is not installed")
                Log.e("DeviceKeyHelper", "2. The ContentProvider is not registered in the manifest")
                Log.e("DeviceKeyHelper", "3. The app needs to be restarted/reinstalled")
                return null
            } catch (e: SecurityException) {
                Log.e("DeviceKeyHelper", "SecurityException: Permission denied", e)
                return null
            }

            if (cursor == null) {
                Log.e("DeviceKeyHelper", "ContentProvider query returned null cursor")
                Log.e("DeviceKeyHelper", "The ContentProvider might not be accessible")
                return null
            }

            cursor.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex("publicKey")
                    if (columnIndex >= 0) {
                        val publicKey = it.getString(columnIndex)
                        if (publicKey.isNullOrBlank()) {
                            Log.e("DeviceKeyHelper", "Public key is null or blank")
                            return null
                        }
                        Log.d("DeviceKeyHelper", "✓ Successfully retrieved public key (length: ${publicKey.length})")
                        return publicKey
                    } else {
                        Log.e("DeviceKeyHelper", "Column 'publicKey' not found in cursor")
                        Log.e("DeviceKeyHelper", "Available columns: ${it.columnNames.joinToString()}")
                    }
                } else {
                    Log.e("DeviceKeyHelper", "Cursor is empty - no rows returned")
                }
            }
            
            Log.w("DeviceKeyHelper", "No public key found in cursor")
            null
        } catch (e: Exception) {
            Log.e("DeviceKeyHelper", "Unexpected error getting public key", e)
            e.printStackTrace()
            null
        }
    }
}
