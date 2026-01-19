package org.npci.bbps.tpap.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.serialization.json.Json
import org.npci.bbps.tpap.model.QrCodeData
import org.npci.bbps.tpap.model.EncryptedStatementResponse
import java.util.concurrent.Executors

class QrScanActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, start camera
        } else {
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check camera permission
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        setContent {
            QrScanScreen(
                onQrCodeScanned = { qrData ->
                    handleQrCodeScanned(qrData)
                },
                onBack = {
                    finish()
                }
            )
        }
    }
    
    private fun handleQrCodeScanned(qrData: QrCodeData) {
        Log.d("QrScanActivity", "QR code scanned successfully")
        Log.d("QrScanActivity", "Encrypted payload length: ${qrData.encryptedPayload.length}")
        
        // Launch callable app with encrypted data.
        // IMPORTANT: QR flow is bill statement only (never payment history).
        val intent = Intent().apply {
            setClassName(
                "org.npci.bbps.callableui",
                "org.npci.bbps.callableui.entry.StatementRenderActivity"
            )
            putExtra("encryptedPayload", qrData.encryptedPayload)
            putExtra("wrappedDek", qrData.wrappedDek)
            putExtra("iv", qrData.iv)
            putExtra("senderPublicKey", qrData.senderPublicKey)
            putExtra("payloadType", "BILL_STATEMENT")
        }
        
        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("QrScanActivity", "Failed to launch callable app", e)
            Toast.makeText(this, "Failed to open callable app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun QrScanScreen(
    onQrCodeScanned: (QrCodeData) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(true) }
    
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    // Optimize ML Kit barcode scanner for faster detection
    val barcodeScanner = remember { 
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE
                )
                .enableAllPotentialBarcodes() // Enable faster detection
                .build()
        )
    }
    
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var frameCount by remember { mutableStateOf(0L) }
    var lastProcessTime by remember { mutableStateOf(0L) }
    
    // Scanning box dimensions (as fraction of screen)
    val scanBoxWidth = 0.7f
    val scanBoxHeight = 0.4f
    
    LaunchedEffect(previewView) {
        if (previewView == null) return@LaunchedEffect
        
        val cameraProvider = cameraProviderFuture.get()
        
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView!!.surfaceProvider)
        }
        
        // Optimized: Higher resolution for better QR code detection
        // Higher resolution helps ML Kit detect complex/large QR codes faster
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetResolution(android.util.Size(1280, 720)) // Higher resolution for better QR detection
            .build()
            .also {
                it.setAnalyzer(executor) { imageProxy ->
                    if (isScanning) {
                        val currentTime = System.currentTimeMillis()
                        // Process every 2nd frame (reduced skipping for faster detection)
                        frameCount++
                        if (frameCount % 2 == 0L && (currentTime - lastProcessTime) > 50) {
                            // Reduced throttling from 100ms to 50ms for faster scanning
                            lastProcessTime = currentTime
                            processImageProxy(
                                imageProxy, 
                                barcodeScanner,
                                scanBoxWidth,
                                scanBoxHeight
                            ) { qrData ->
                                isScanning = false
                                onQrCodeScanned(qrData)
                            }
                        }
                    }
                    imageProxy.close()
                }
            }
        
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e("QrScanScreen", "Camera binding failed", e)
            errorMessage = "Failed to start camera: ${e.message}"
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                // Ignore
            }
            barcodeScanner.close()
            executor.shutdown()
        }
    }
    
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Camera preview
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also {
                            previewView = it
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Scanning box overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Dark overlay with transparent center
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val boxWidth = width * scanBoxWidth
                        val boxHeight = height * scanBoxHeight
                        val left = (width - boxWidth) / 2
                        val top = (height - boxHeight) / 2
                        val right = left + boxWidth
                        val bottom = top + boxHeight
                        
                        // Draw dark overlay by drawing 4 rectangles around the scanning box
                        val overlayColor = Color.Black.copy(alpha = 0.6f)
                        
                        // Top rectangle
                        drawRect(
                            color = overlayColor,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(width, top)
                        )
                        // Bottom rectangle
                        drawRect(
                            color = overlayColor,
                            topLeft = Offset(0f, bottom),
                            size = androidx.compose.ui.geometry.Size(width, height - bottom)
                        )
                        // Left rectangle
                        drawRect(
                            color = overlayColor,
                            topLeft = Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(left, boxHeight)
                        )
                        // Right rectangle
                        drawRect(
                            color = overlayColor,
                            topLeft = Offset(right, top),
                            size = androidx.compose.ui.geometry.Size(width - right, boxHeight)
                        )
                        
                        // Draw corner indicators
                        val cornerLength = 40.dp.toPx()
                        val strokeWidth = 4.dp.toPx()
                        val cornerColor = Color.White
                        
                        // Top-left corner
                        drawLine(
                            color = cornerColor,
                            start = Offset(left, top),
                            end = Offset(left + cornerLength, top),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = cornerColor,
                            start = Offset(left, top),
                            end = Offset(left, top + cornerLength),
                            strokeWidth = strokeWidth
                        )
                        
                        // Top-right corner
                        drawLine(
                            color = cornerColor,
                            start = Offset(right, top),
                            end = Offset(right - cornerLength, top),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = cornerColor,
                            start = Offset(right, top),
                            end = Offset(right, top + cornerLength),
                            strokeWidth = strokeWidth
                        )
                        
                        // Bottom-left corner
                        drawLine(
                            color = cornerColor,
                            start = Offset(left, bottom),
                            end = Offset(left + cornerLength, bottom),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = cornerColor,
                            start = Offset(left, bottom),
                            end = Offset(left, bottom - cornerLength),
                            strokeWidth = strokeWidth
                        )
                        
                        // Bottom-right corner
                        drawLine(
                            color = cornerColor,
                            start = Offset(right, bottom),
                            end = Offset(right - cornerLength, bottom),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = cornerColor,
                            start = Offset(right, bottom),
                            end = Offset(right, bottom - cornerLength),
                            strokeWidth = strokeWidth
                        )
                    }
                }
                
                // Overlay with instructions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top bar with back button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                        ) {
                            Text("Back")
                        }
                    }
                    
                    // Bottom instructions
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Scan QR Code",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Position the QR code within the frame",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // Error message
                errorMessage?.let { error ->
                    AlertDialog(
                        onDismissRequest = { errorMessage = null },
                        title = { Text("Error") },
                        text = { Text(error) },
                        confirmButton = {
                            TextButton(onClick = { errorMessage = null }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun processImageProxy(
    imageProxy: androidx.camera.core.ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    scanBoxWidth: Float,
    scanBoxHeight: Float,
    onQrCodeFound: (QrCodeData) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        try {
            // Validate image format - ML Kit requires YUV_420_888 format
            val imageFormat = mediaImage.format
            if (imageFormat != android.graphics.ImageFormat.YUV_420_888) {
                Log.d("QrScanScreen", "Skipping frame with format: $imageFormat (expected YUV_420_888)")
                return
            }
            
            // Check if image planes are valid and have data
            val planes = mediaImage.planes
            if (planes == null || planes.isEmpty() || planes[0] == null) {
                Log.d("QrScanScreen", "Image has invalid planes")
                return
            }
            
            // Check if the first plane has a valid buffer (this is what ML Kit needs)
            val firstPlane = planes[0]
            if (firstPlane.buffer == null) {
                Log.d("QrScanScreen", "First plane has null buffer")
                return
            }
            
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            
            // Calculate scanning box bounds in image coordinates
            val imageWidth = mediaImage.width.toFloat()
            val imageHeight = mediaImage.height.toFloat()
            val boxLeft = (imageWidth * (1f - scanBoxWidth) / 2f)
            val boxTop = (imageHeight * (1f - scanBoxHeight) / 2f)
            val boxRight = boxLeft + (imageWidth * scanBoxWidth)
            val boxBottom = boxTop + (imageHeight * scanBoxHeight)
            
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        // Check if barcode is within scanning box
                        val boundingBox = barcode.boundingBox
                        if (boundingBox != null) {
                            val barcodeCenterX = boundingBox.left + (boundingBox.width() / 2f)
                            val barcodeCenterY = boundingBox.top + (boundingBox.height() / 2f)
                            
                            // Only process if barcode center is within scanning box
                            if (barcodeCenterX < boxLeft || barcodeCenterX > boxRight ||
                                barcodeCenterY < boxTop || barcodeCenterY > boxBottom) {
                                continue // Skip barcodes outside the scanning box
                            }
                        }
                        
                        when (barcode.valueType) {
                            Barcode.TYPE_TEXT, Barcode.TYPE_URL -> {
                                val rawValue = barcode.rawValue
                                if (rawValue != null) {
                                    try {
                                        // Try parsing as EncryptedStatementResponse first (from backend)
                                        val encryptedResponse = Json.decodeFromString<EncryptedStatementResponse>(rawValue)
                                        val qrData = QrCodeData(
                                            encryptedPayload = encryptedResponse.encryptedPayload,
                                            wrappedDek = encryptedResponse.wrappedDek,
                                            iv = encryptedResponse.iv,
                                            senderPublicKey = encryptedResponse.senderPublicKey,
                                            expiry = encryptedResponse.expiry
                                        )
                                        onQrCodeFound(qrData)
                                        return@addOnSuccessListener // Stop processing after finding valid QR
                                    } catch (e: Exception) {
                                        try {
                                            // Fallback: try parsing as QrCodeData directly
                                            val qrData = Json.decodeFromString<QrCodeData>(rawValue)
                                            onQrCodeFound(qrData)
                                            return@addOnSuccessListener // Stop processing after finding valid QR
                                        } catch (e2: Exception) {
                                            Log.e("QrScanScreen", "Failed to parse QR code data", e2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    // Suppress NullPointerException errors as they're often due to image format issues
                    // that we're already handling with validation above
                    if (e.message?.contains("NullPointerException", ignoreCase = true) != true) {
                        Log.e("QrScanScreen", "Barcode scanning failed", e)
                    }
                }
        } catch (e: Exception) {
            // Suppress common image processing errors
            if (e.message?.contains("NullPointerException", ignoreCase = true) != true) {
                Log.e("QrScanScreen", "Error processing image", e)
            }
        }
    }
}
