package org.npci.bbps.tpap.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import org.json.JSONObject

/**
 * WebView Activity that uses the BBPS bundle to decrypt and render statements
 * Replaces the callable app integration
 * 
 * SECURITY MODEL: The Secure Runtime Bundle has exclusive access to the device's private key.
 * The TPAP app NEVER sees the private key - the Secure Runtime Bundle generates and manages
 * its own key pair entirely within the isolated JavaScript context.
 * All ECDH operations and decryption happen within the Secure Runtime Bundle's isolated context.
 */
class StatementWebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots / screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Create WebView
        webView = WebView(this)
        setContentView(webView)

        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }

        // Add JavaScript interface for communication
        webView.addJavascriptInterface(AndroidInterface(), "Android")

        // Set WebViewClient with console logging
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Add console logging
                webView.evaluateJavascript(
                    """
                    console.log = function(message) {
                        Android.log('JS: ' + message);
                    };
                    console.error = function(message) {
                        Android.log('JS ERROR: ' + message);
                    };
                    """.trimIndent(),
                    null
                )
                // Wait a bit for console setup, then initialize
                webView.postDelayed({
                    initializeAndProcess()
                }, 200)
            }
        }

        // Load the bundle HTML
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    private fun initializeAndProcess() {
        try {
            Log.d("StatementWebView", "Starting initialization...")
            
            // SECURITY: The TPAP app NEVER touches the private key.
            // The Secure Runtime Bundle generates and manages its own key pair
            // entirely within the isolated JavaScript context.

            // Get encrypted data from intent
            val encryptedPayload = intent.getStringExtra("encryptedPayload")
            val wrappedDek = intent.getStringExtra("wrappedDek")
            val iv = intent.getStringExtra("iv")
            val senderPublicKey = intent.getStringExtra("senderPublicKey")
            val payloadType = intent.getStringExtra("payloadType") ?: "BILL_STATEMENT"

            Log.d("StatementWebView", "Encrypted data retrieved - payload: ${encryptedPayload?.take(20)}..., wrappedDek: ${wrappedDek?.take(20)}...")

            if (encryptedPayload == null || wrappedDek == null || iv == null || senderPublicKey == null) {
                Log.e("StatementWebView", "Missing required parameters")
                webView.evaluateJavascript(
                    """
                    if (window.BBPSBundle && window.BBPSBundle.showError) {
                        window.BBPSBundle.showError('Missing required decryption parameters');
                    } else {
                        alert('Missing required decryption parameters');
                    }
                    """.trimIndent(),
                    null
                )
                return
            }

            // Prepare JSON strings with proper escaping
            // SECURITY: The Secure Runtime Bundle will use its own private key
            // (generated and stored in IndexedDB within the isolated JavaScript context)
            // The TPAP app never sees the private key.
            val paramsJson = JSONObject().apply {
                put("encryptedPayload", encryptedPayload)
                put("wrappedDek", wrappedDek)
                put("iv", iv)
                put("senderPublicKey", senderPublicKey)
                put("payloadType", payloadType)
            }.toString()

            // Initialize and process in one async call
            // The Secure Runtime Bundle will:
            // 1. Check if it has a stored key pair in IndexedDB
            // 2. If not, generate a new key pair
            // 3. Use the private key to perform ECDH and decrypt
            val initScript = """
                (async function() {
                    try {
                        Android.log('Checking if BBPSBundle is loaded...');
                        if (typeof window.BBPSBundle === 'undefined') {
                            throw new Error('BBPSBundle not loaded. Check assets.');
                        }
                        
                        Android.log('Initializing Secure Runtime (generating/retrieving device key pair)...');
                        // Ensure device key pair exists (generates if needed, retrieves from IndexedDB if exists)
                        // This will also trigger automatic device registration
                        await window.BBPSBundle.ensureDeviceKeyPair();
                        Android.log('Secure Runtime initialized with device key pair');
                        
                        // Wait a moment to ensure registration completes
                        Android.log('Ensuring device registration is complete...');
                        await new Promise(resolve => setTimeout(resolve, 500));
                        
                        Android.log('Processing encrypted statement in Secure Runtime...');
                        const params = $paramsJson;
                        await window.BBPSBundle.processEncryptedStatement(params);
                        Android.log('Processing complete');
                    } catch (error) {
                        Android.log('ERROR: ' + error.message);
                        Android.log('Stack: ' + (error.stack || 'No stack'));
                        if (window.BBPSBundle && window.BBPSBundle.showError) {
                            window.BBPSBundle.showError('Error: ' + error.message);
                        } else if (window.uiController) {
                            window.uiController.showError('Error: ' + error.message);
                        } else {
                            alert('Error: ' + error.message);
                        }
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(initScript, null)

        } catch (e: Exception) {
            Log.e("StatementWebView", "Error initializing bundle", e)
            e.printStackTrace()
            webView.evaluateJavascript(
                """
                if (window.BBPSBundle && window.uiController) {
                    window.uiController.showError('Failed to initialize: ${e.message?.replace("'", "\\'")}');
                } else {
                    alert('Failed to initialize: ${e.message?.replace("'", "\\'")}');
                }
                """.trimIndent(),
                null
            )
        }
    }

    /**
     * JavaScript interface for communication between WebView and Android
     */
    inner class AndroidInterface {
        @JavascriptInterface
        fun close() {
            finish()
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d("WebView", message)
        }

        @JavascriptInterface
        fun registerDevice(baseUrl: String, consumerId: String, deviceId: String, publicKeyBase64: String): Boolean {
            // Register device with backend - called automatically by Secure Runtime Bundle
            return try {
                org.npci.bbps.tpap.network.BackendApi.registerDevice(
                    baseUrl = baseUrl,
                    request = org.npci.bbps.tpap.model.DeviceRegistrationRequest(
                        consumerId = consumerId,
                        deviceId = deviceId,
                        devicePublicKey = publicKeyBase64
                    )
                )
                Log.d("StatementWebView", "Device registered successfully via Secure Runtime Bundle")
                true
            } catch (e: Exception) {
                Log.e("StatementWebView", "Failed to register device via Secure Runtime Bundle", e)
                false
            }
        }

        @JavascriptInterface
        fun getDeviceId(): String {
            // Get device ID for registration
            return android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: ""
        }

        @JavascriptInterface
        fun getBackendConfig(): String {
            // Return backend configuration as JSON
            val config = org.json.JSONObject().apply {
                put("baseUrl", org.npci.bbps.tpap.config.AppConfig.baseUrl)
                put("consumerId", org.npci.bbps.tpap.config.AppConfig.consumerId)
            }
            return config.toString()
        }

        @JavascriptInterface
        fun storePublicKey(publicKeyBase64: String) {
            // Store public key in SharedPreferences (for backward compatibility)
            val prefs = getSharedPreferences("bbps_secure_runtime", MODE_PRIVATE)
            prefs.edit().putString("device_public_key_spki", publicKeyBase64).apply()
            Log.d("StatementWebView", "Stored device public key in SharedPreferences (length: ${publicKeyBase64.length})")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
