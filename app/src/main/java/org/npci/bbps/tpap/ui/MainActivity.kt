package org.npci.bbps.tpap.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.npci.bbps.tpap.model.EncryptStatementRequest
import org.npci.bbps.tpap.network.BackendApi

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
    
    val onViewMoreDetailsClick: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val response = withContext(Dispatchers.IO) {
                    BackendApi.encryptStatement(
                        baseUrl = "http://10.0.2.2:8111", // Emulator: use 10.0.2.2 for localhost
                        request = EncryptStatementRequest(
                            statementId = "STMT123",
                            consumerId = "C12345",
                            deviceId = deviceId
                        )
                    )
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
                Log.e("TPAP", "Error encrypting statement", e)
                errorMessage = "Failed to load statement: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "BBPS TPAP App",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "View your encrypted statement details",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onViewMoreDetailsClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(24.dp).height(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "View More Details",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Device Info",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Device ID: $deviceId",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Consumer ID: C12345",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Statement ID: STMT123",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
