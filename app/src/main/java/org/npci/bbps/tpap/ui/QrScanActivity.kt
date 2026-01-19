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
import org.npci.bbps.tpap.model.QrPaymentPayload
import org.npci.bbps.tpap.util.QrIntentContract
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
    
    private fun handleQrCodeScanned(qrData: QrPaymentPayload) {
        Log.d("QrScanActivity", "QR code scanned successfully")

        // Launch TPAP PaymentScreen directly with bill details + encrypted fields from QR payload.
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            putExtra(QrIntentContract.EXTRA_FROM_QR, true)
            putExtra(QrIntentContract.EXTRA_BILLER_ID, qrData.billerId)
            putExtra(QrIntentContract.EXTRA_BILLER_NAME, qrData.billerName)
            putExtra(QrIntentContract.EXTRA_CONSUMER_NUMBER, qrData.consumerNumber)
            putExtra(QrIntentContract.EXTRA_AMOUNT, qrData.amount)
            putExtra(QrIntentContract.EXTRA_DUE_DATE, qrData.dueDate)

            putExtra(QrIntentContract.EXTRA_ENCRYPTED_PAYLOAD, qrData.encryptedPayload)
            putExtra(QrIntentContract.EXTRA_WRAPPED_DEK, qrData.wrappedDek)
            putExtra(QrIntentContract.EXTRA_IV, qrData.iv)
            putExtra(QrIntentContract.EXTRA_SENDER_PUBLIC_KEY, qrData.senderPublicKey)
            if (qrData.expiry != null) {
                putExtra(QrIntentContract.EXTRA_EXPIRY, qrData.expiry)
            }
        }

        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("QrScanActivity", "Failed to open PaymentScreen after QR scan", e)
            Toast.makeText(this, "Failed to open payment screen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}

@Composable
fun QrScanScreen(
    onQrCodeScanned: (QrPaymentPayload) -> Unit,
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
    onQrCodeFound: (QrPaymentPayload) -> Unit
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
                                        // 1) Preferred: parse as QR Payment payload (JSON)
                                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                                        val payload = json.decodeFromString<QrPaymentPayload>(rawValue)
                                        val normalized = normalizeQrPayload(payload)
                                        if (normalized != null) {
                                            onQrCodeFound(normalized)
                                            return@addOnSuccessListener
                                        }
                                    } catch (_: Exception) {
                                        // ignore and try other formats
                                    }

                                    try {
                                        // 2) Backward compatible: EncryptedStatementResponse (JSON)
                                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                                        val encryptedResponse = json.decodeFromString<EncryptedStatementResponse>(rawValue)
                                        onQrCodeFound(
                                            QrPaymentPayload(
                                                encryptedPayload = encryptedResponse.encryptedPayload,
                                                wrappedDek = encryptedResponse.wrappedDek,
                                                iv = encryptedResponse.iv,
                                                senderPublicKey = encryptedResponse.senderPublicKey,
                                                expiry = encryptedResponse.expiry
                                            )
                                        )
                                        return@addOnSuccessListener // Stop processing after finding valid QR
                                    } catch (e: Exception) {
                                        try {
                                            // 3) Backward compatible: QrCodeData (JSON)
                                            val json = Json { ignoreUnknownKeys = true; isLenient = true }
                                            val qrData = json.decodeFromString<QrCodeData>(rawValue)
                                            onQrCodeFound(
                                                QrPaymentPayload(
                                                    encryptedPayload = qrData.encryptedPayload,
                                                    wrappedDek = qrData.wrappedDek,
                                                    iv = qrData.iv,
                                                    senderPublicKey = qrData.senderPublicKey,
                                                    expiry = qrData.expiry
                                                )
                                            )
                                            return@addOnSuccessListener // Stop processing after finding valid QR
                                        } catch (e2: Exception) {
                                            // 4) Plain-text key/value payload
                                            val fromText = parsePlainTextQrPayload(rawValue)
                                            val normalized = normalizeQrPayload(fromText)
                                            if (normalized != null) {
                                                onQrCodeFound(normalized)
                                                return@addOnSuccessListener
                                            }

                                            Log.e("QrScanScreen", "Failed to parse QR code payload", e2)
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

private fun normalizeQrPayload(payload: QrPaymentPayload): QrPaymentPayload? {
    val encryptedPayload = payload.encryptedPayload?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val wrappedDek = payload.wrappedDek?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val iv = payload.iv?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val senderPublicKey = payload.senderPublicKey?.trim().takeUnless { it.isNullOrEmpty() } ?: return null

    return payload.copy(
        encryptedPayload = encryptedPayload,
        wrappedDek = wrappedDek,
        iv = iv,
        senderPublicKey = senderPublicKey,
        billerId = payload.billerId?.trim(),
        billerName = payload.billerName?.trim(),
        consumerNumber = payload.consumerNumber?.trim(),
        amount = payload.amount?.trim(),
        dueDate = payload.dueDate?.trim()
    )
}

private fun parsePlainTextQrPayload(rawValue: String): QrPaymentPayload {
    // Supports formats like:
    // billerId=BESCOM|consumerNumber=CON123|amount=101|dueDate=15-Jan-2026|encryptedPayload=...|wrappedDek=...|iv=...|senderPublicKey=...
    // or newline/semicolon separated and either ":" or "=" as separators.
    val separators = charArrayOf('\n', '|', ';', '&', ',')
    val tokens = rawValue
        .trim()
        .split(*separators)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val map = linkedMapOf<String, String>()
    for (token in tokens) {
        val pair = when {
            token.contains('=') -> token.split('=', limit = 2)
            token.contains(':') -> token.split(':', limit = 2)
            else -> null
        } ?: continue

        val key = pair[0].trim().lowercase()
        val value = pair.getOrNull(1)?.trim().orEmpty()
        if (key.isNotEmpty() && value.isNotEmpty()) {
            map[key] = value
        }
    }

    fun v(vararg keys: String): String? =
        keys.asSequence().mapNotNull { map[it.lowercase()] }.firstOrNull()

    fun rx(pattern: String): String? =
        Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            .find(rawValue)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    // Also support space-delimited formats like:
    // "billerID BESCOM consumerNumber CON123 amount 101 dueDate 15-Jan-2026 ..."
    val billerId = v("billerid", "biller_id", "biller")
        ?: rx("""\b(?:biller\s*id|billerid)\b\s*[:=]?\s*([A-Za-z0-9_-]+)""")

    val billerName = v("billername", "biller_name")
        ?: rx("""\b(?:biller\s*name|billername)\b\s*[:=]?\s*([^|\n;&,]+)""")

    val consumerNumber = v("consumernumber", "consumer_number", "consumer", "account", "accountnumber")
        ?: rx("""\b(?:consumer\s*number|consumernumber|account\s*number|accountnumber)\b\s*[:=]?\s*([A-Za-z0-9_-]+)""")

    val amount = v("amount", "amt")
        ?: rx("""\b(?:amount|amt)\b\s*[:=]?\s*(₹?\s*[0-9]+(?:\.[0-9]{1,2})?)""")

    val dueDate = v("duedate", "due_date", "due")
        ?: rx("""\b(?:due\s*date|duedate)\b\s*[:=]?\s*([0-9]{1,2}[-/ ][A-Za-z]{3,9}[-/ ][0-9]{2,4}|[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4})""")

    return QrPaymentPayload(
        billerId = billerId,
        billerName = billerName,
        consumerNumber = consumerNumber,
        amount = amount,
        dueDate = dueDate,
        encryptedPayload = v("encryptedpayload", "encrypted_payload", "payload"),
        wrappedDek = v("wrappeddek", "wrapped_dek", "dek"),
        iv = v("iv"),
        senderPublicKey = v("senderpublickey", "sender_public_key", "senderkey", "publickey"),
        expiry = v("expiry")?.toLongOrNull()
    )
}
