package org.npci.bbps.tpap.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import org.npci.bbps.tpap.config.AppConfig
import org.npci.bbps.tpap.model.DeviceRegistrationRequest
import org.npci.bbps.tpap.model.EncryptStatementRequest
import org.npci.bbps.tpap.network.BackendApi
import org.npci.bbps.tpap.util.DeviceKeyHelper
import org.npci.bbps.tpap.ui.QrScanActivity

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            TPAPScreen()
        }
    }
}

@Composable
fun TPAPScreen() {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope: CoroutineScope = rememberCoroutineScope()
    
    // Get device ID (ANDROID_ID)
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    val onMiniStatementClick: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                // Get backend URL from AppConfig (supports LOCAL, EMULATOR, AWS)
                val baseUrl = AppConfig.baseUrl
                val consumerId = AppConfig.consumerId
                
                // Step 0: Check if callable app is installed
                Log.d("TPAP", "Checking if callable app is installed...")
                val isCallableAppInstalled = withContext(Dispatchers.IO) {
                    DeviceKeyHelper.isCallableAppInstalled(context.packageManager)
                }
                
                // Step 1: Try to get public key from callable app
                // Uses BroadcastReceiver (more reliable) with ContentProvider fallback
                Log.d("TPAP", "Attempting to retrieve public key from callable app...")
                val publicKey = withContext(Dispatchers.IO) {
                    DeviceKeyHelper.getPublicKey(context)
                }
                
                if (publicKey == null) {
                    // Package check failed AND ContentProvider query failed
                    val errorMsg = if (!isCallableAppInstalled) {
                        "Callable app (org.npci.bbps.callableui) is not installed or ContentProvider is not accessible.\n" +
                        "Please:\n" +
                        "1. Uninstall the callable app completely from your device\n" +
                        "2. Rebuild and reinstall the callable app from Android Studio\n" +
                        "3. Make sure both apps are installed on the same device\n" +
                        "4. Try restarting your device after installing"
                    } else {
                        "Failed to get public key from callable app. " +
                        "The ContentProvider may not be registered. " +
                        "Try uninstalling and reinstalling the callable app."
                    }
                    throw IllegalStateException(errorMsg)
                } else {
                    // Success! ContentProvider works
                    if (!isCallableAppInstalled) {
                        Log.w("TPAP", "Package check failed but ContentProvider works - app is installed!")
                    } else {
                        Log.d("TPAP", "Callable app verified and public key retrieved")
                    }
                }
                
                Log.d("TPAP", "Successfully retrieved public key from callable app (length: ${publicKey.length})")
                
                Log.d("TPAP", "Retrieved public key from callable app")
                
                // Step 2: Try to encrypt (this will fail if device is not registered)
                var response = try {
                    withContext(Dispatchers.IO) {
                        BackendApi.encryptStatement(
                            baseUrl = baseUrl,
                            request = EncryptStatementRequest(
                                statementId = AppConfig.statementId,
                                consumerId = consumerId,
                                deviceId = deviceId
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Check if error is due to device not being registered
                    // Backend returns HTTP 500 when device is not registered
                    val errorMessage = e.message ?: ""
                    val isDeviceNotRegistered = errorMessage.contains("No ACTIVE device key found", ignoreCase = true) ||
                                                errorMessage.contains("IllegalStateException", ignoreCase = true) ||
                                                errorMessage.contains("device key", ignoreCase = true) ||
                                                (errorMessage.contains("500") && errorMessage.contains("encrypt")) ||
                                                errorMessage.contains("Failed to encrypt statement: 500")
                    
                    Log.d("TPAP", "Encryption error: $errorMessage")
                    Log.d("TPAP", "Is device not registered? $isDeviceNotRegistered")
                    
                    // If encryption fails with 500, assume device not registered and try to register
                    if (isDeviceNotRegistered || errorMessage.contains("500")) {
                        Log.d("TPAP", "Device not registered. Registering now...")
                        
                        // Step 3: Register device using callable app's public key
                        withContext(Dispatchers.IO) {
                            BackendApi.registerDevice(
                                baseUrl = baseUrl,
                                request = DeviceRegistrationRequest(
                                    consumerId = consumerId,
                                    deviceId = deviceId,
                                    devicePublicKey = publicKey
                                )
                            )
                        }
                        
                        Log.d("TPAP", "Device registered successfully")
                        
                        // Step 4: Retry encryption after registration
                        withContext(Dispatchers.IO) {
                            BackendApi.encryptStatement(
                                baseUrl = baseUrl,
                                request = EncryptStatementRequest(
                                    statementId = "STMT123",
                                    consumerId = consumerId,
                                    deviceId = deviceId
                                )
                            )
                        }
                    } else {
                        throw e
                    }
                }
                
                // Log received data for debugging
                Log.d("TPAP", "Received encrypted response from backend")
                Log.d("TPAP", "Encrypted payload length: ${response.encryptedPayload.length}")
                Log.d("TPAP", "Wrapped DEK length: ${response.wrappedDek.length}")
                Log.d("TPAP", "IV: ${response.iv}")
                Log.d("TPAP", "Sender public key length: ${response.senderPublicKey.length}")
                
                // Launch callable app with encrypted payload
                val intent = Intent().apply {
                    setClassName(
                        "org.npci.bbps.callableui",
                        "org.npci.bbps.callableui.entry.StatementRenderActivity"
                    )
                    putExtra("encryptedPayload", response.encryptedPayload)
                    putExtra("wrappedDek", response.wrappedDek)
                    putExtra("iv", response.iv)
                    putExtra("senderPublicKey", response.senderPublicKey)
                }
                
                Log.d("TPAP", "Launching callable app with encrypted data...")
                context.startActivity(intent)
                
            } catch (e: Exception) {
                Log.e("TPAP", "Error in mini statement flow", e)
                errorMessage = "Failed to load mini statement: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    val onScanQrClick: () -> Unit = {
        val intent = Intent(context, QrScanActivity::class.java)
        context.startActivity(intent)
    }
    
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {
                // Header with gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1976D2),
                                    Color(0xFF1565C0)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "BBPS TPAP",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bill Payment Service",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                
                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Biller Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Logo/Icon
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFFF9800),
                                                Color(0xFFF57C00)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = "Biller Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Biller Info
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "BESCOM",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Bangalore Electricity Supply Company",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Consumer No: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "CON123456789",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Action Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Mini Statement Button
                        Button(
                            onClick = onMiniStatementClick,
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mini Statement",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        // Scan QR Button
                        OutlinedButton(
                            onClick = onScanQrClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 2.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scan QR for Mini Statement",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    // Error Message
                    errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Device Info Card (at bottom)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Device Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            InfoRow("Device ID", deviceId.take(8) + "...")
                            InfoRow("Consumer ID", "C12345")
                            InfoRow("Statement ID", "STMT123")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
