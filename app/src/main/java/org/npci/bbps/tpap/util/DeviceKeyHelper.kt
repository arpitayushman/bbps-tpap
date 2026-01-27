package org.npci.bbps.tpap.util

import android.content.pm.PackageManager
import android.util.Log

/**
 * Helper to get device public key (DEPRECATED)
 * 
 * NOTE: This helper is no longer needed for the main flow.
 * The Secure Runtime Bundle now handles all key generation and device registration automatically.
 * This class is kept for backward compatibility only.
 * 
 * For new integrations, simply:
 * 1. Pass encrypted data to StatementWebViewActivity
 * 2. The Secure Runtime Bundle will automatically handle key generation and device registration
 */
object DeviceKeyHelper {

    // Keep these for backward compatibility checks (optional)
    private const val CALLABLE_APP_PACKAGE = "org.npci.bbps.callableui"

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
     * Check if callable app is installed (for backward compatibility)
     * Note: This is no longer required since we use KeystoreManager directly
     * @return Always returns true since we don't need the callable app anymore
     */
    fun isCallableAppInstalled(packageManager: PackageManager): Boolean {
        // No longer needed - we use KeystoreManager directly
        // Keep for backward compatibility
        return true
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
     * Get the device public key from Secure Runtime Bundle (stored in SharedPreferences)
     * The Secure Runtime Bundle generates and manages its own key pair
     * 
     * IMPORTANT: On first run, the Secure Runtime Bundle may not have initialized yet.
     * In that case, this will return null and the caller should initialize the bundle first.
     * 
     * @return Base64 encoded public key in SPKI format, or null if unavailable
     */
    fun getPublicKey(context: android.content.Context? = null): String? {
        if (context == null) {
            Log.e("DeviceKeyHelper", "Context is required to get public key from Secure Runtime Bundle")
            return null
        }
        
        return try {
            // Get public key from SharedPreferences (stored by Secure Runtime Bundle)
            val prefs = context.getSharedPreferences("bbps_secure_runtime", android.content.Context.MODE_PRIVATE)
            val publicKey = prefs.getString("device_public_key_spki", null)
            
            if (publicKey != null) {
                Log.d("DeviceKeyHelper", "✓ Successfully retrieved public key from Secure Runtime Bundle (length: ${publicKey.length})")
                publicKey
            } else {
                Log.w("DeviceKeyHelper", "Public key not found in SharedPreferences. Secure Runtime Bundle has not initialized yet.")
                Log.w("DeviceKeyHelper", "The bundle will initialize when StatementWebViewActivity loads, but device registration may fail on first run.")
                Log.w("DeviceKeyHelper", "Consider initializing the bundle early or handling registration after bundle initialization.")
                null
            }
        } catch (e: Exception) {
            Log.e("DeviceKeyHelper", "Error getting public key from Secure Runtime Bundle", e)
            null
        }
    }
}
