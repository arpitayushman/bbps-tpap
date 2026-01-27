package org.npci.bbps.tpap.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
// import androidx.compose.material.ripple.rememberRipple // Removed deprecated import
import androidx.compose.material3.ripple // Added new M3 ripple import
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.npci.bbps.tpap.R
import org.npci.bbps.tpap.config.AppConfig
import org.npci.bbps.tpap.model.DeviceRegistrationRequest
import org.npci.bbps.tpap.model.EncryptedStatementResponse
import org.npci.bbps.tpap.model.EncryptStatementRequest
import org.npci.bbps.tpap.model.QrPaymentPayload
import org.npci.bbps.tpap.network.BackendApi
import org.npci.bbps.tpap.util.DeviceKeyHelper
import org.npci.bbps.tpap.util.QrIntentContract

// --- Colors ---
val BharatBlue = Color(0xFF1976D2) // Primary blue matching Bharat Connect
val BharatLightBlue = Color(0xFF2196F3) // Lighter blue accent
val BgGray = Color(0xFFF1F3F6)

// --- Data Models ---
data class BankOption(
    val name: String,
    val icon: ImageVector,
    val isPopular: Boolean = false
)

class MainActivity : ComponentActivity() {
    private val latestQrPayload = mutableStateOf<QrPaymentPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestQrPayload.value = extractQrPaymentPayload(intent)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = BharatBlue,
                    background = BgGray
                )
            ) {
                AppNavigation(qrPayload = latestQrPayload.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        latestQrPayload.value = extractQrPaymentPayload(intent)
    }
}

// --- Navigation ---
@Composable
fun AppNavigation(qrPayload: QrPaymentPayload? = null) {
    val navController = rememberNavController()

    // If app is opened from QR scan, jump directly to PaymentScreen once.
    LaunchedEffect(qrPayload) {
        if (qrPayload != null) {
            val displayName = (qrPayload.billerName ?: qrPayload.billerId ?: "Biller")
            val encodedName = displayName.replace(" ", "_")
            val consumer = qrPayload.consumerNumber?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
            navController.navigate("payment_screen/$encodedName/$consumer") {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(navController)
        }
        composable("pay_bill/{type}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "Bill"
            BillPaymentScreen(navController, type)
        }
        composable("biller_details/{billerName}") { backStackEntry ->
            val name = backStackEntry.arguments?.getString("billerName")?.replace("_", " ") ?: "Biller"
            BillerDetailsScreen(navController, name)
        }

        // --- NEW ROUTE FOR CREDIT CARD DETAILS ---
        composable("credit_card_details/{bankName}") { backStackEntry ->
            val name = backStackEntry.arguments?.getString("bankName")?.replace("_", " ") ?: "HDFC Bank"
            CreditCardDetailsScreen(navController, name)
        }

        composable("payment_screen/{billerName}/{consumerNumber}") { backStackEntry ->
            val billerName = backStackEntry.arguments?.getString("billerName")?.replace("_", " ") ?: "Biller"
            val consumerNumber = backStackEntry.arguments?.getString("consumerNumber") ?: ""
            val matchedQrPayload = qrPayload?.takeIf { payload ->
                val payloadConsumer = payload.consumerNumber?.trim().orEmpty()
                payloadConsumer.isBlank() || payloadConsumer == consumerNumber
            }
            PaymentScreen(navController, billerName, consumerNumber, qrPayload = matchedQrPayload)
        }
    }
}

private fun extractQrPaymentPayload(intent: Intent?): QrPaymentPayload? {
    if (intent == null) return null
    val fromQr = intent.getBooleanExtra(QrIntentContract.EXTRA_FROM_QR, false)
    if (!fromQr) return null

    val expiry = if (intent.hasExtra(QrIntentContract.EXTRA_EXPIRY)) {
        intent.getLongExtra(QrIntentContract.EXTRA_EXPIRY, 0L)
    } else {
        null
    }

    return QrPaymentPayload(
        billerId = intent.getStringExtra(QrIntentContract.EXTRA_BILLER_ID),
        billerName = intent.getStringExtra(QrIntentContract.EXTRA_BILLER_NAME),
        consumerNumber = intent.getStringExtra(QrIntentContract.EXTRA_CONSUMER_NUMBER),
        amount = intent.getStringExtra(QrIntentContract.EXTRA_AMOUNT),
        dueDate = intent.getStringExtra(QrIntentContract.EXTRA_DUE_DATE),
        encryptedPayload = intent.getStringExtra(QrIntentContract.EXTRA_ENCRYPTED_PAYLOAD),
        wrappedDek = intent.getStringExtra(QrIntentContract.EXTRA_WRAPPED_DEK),
        iv = intent.getStringExtra(QrIntentContract.EXTRA_IV),
        senderPublicKey = intent.getStringExtra(QrIntentContract.EXTRA_SENDER_PUBLIC_KEY),
        expiry = expiry
    )
}

// --- Screens ---
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = { CloneTopBar(onQrScanClick = {
            val intent = Intent(context, QrScanActivity::class.java)
            context.startActivity(intent)
        }) },
        bottomBar = { CloneBottomNav(onQrScanClick = {
            val intent = Intent(context, QrScanActivity::class.java)
            context.startActivity(intent)
        }) },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Money Transfers
            Text("Money Transfers", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            MoneyTransferGrid()

            HorizontalDivider(thickness = 8.dp, color = BgGray)

            // 2. Offer Slider
            OfferSlider()

            HorizontalDivider(thickness = 8.dp, color = BgGray)

            // 3. Recharge & Pay Bills
            Text("Recharge & Pay Bills", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            RechargeGrid(navController)

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillPaymentScreen(navController: NavController, type: String) {
    when (type) {
        "Electricity" -> ElectricityScreen(navController)
        "Credit Card" -> CreditCardScreen(navController)
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Pay $type", color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BharatBlue)
                    )
                }
            ) { p ->
                Box(modifier = Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Design for $type coming soon...", color = Color.Gray)
                }
            }
        }
    }
}

// =========================================
// CREDIT CARD SCREEN
// =========================================

@Composable
fun CreditCardScreen(navController: NavController) {
    // State for search and UI
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Simulated Data Source
    val allBanks = remember {
        listOf(
            BankOption("HDFC Bank Credit Card", Icons.Default.AccountBalance, true),
            BankOption("Axis Bank Credit Card", Icons.Default.AccountBalance, true),
            BankOption("SBI Credit Card", Icons.Default.AccountBalance, true),
            BankOption("ICICI Bank Credit Card", Icons.Default.AccountBalance, true),
            BankOption("Kotak Mahindra Bank", Icons.Default.AccountBalance, true),
            BankOption("RBL Bank Credit Card", Icons.Default.AccountBalance, true),
            BankOption("American Express", Icons.Default.CreditCard),
            BankOption("AU Small Finance Bank", Icons.Default.AccountBalance),
            BankOption("Bank of Baroda", Icons.Default.AccountBalance),
            BankOption("Canara Bank", Icons.Default.AccountBalance),
            BankOption("Citi Bank", Icons.Default.AccountBalance),
            BankOption("Dhanlaxmi Bank", Icons.Default.AccountBalance),
            BankOption("Federal Bank", Icons.Default.AccountBalance),
            BankOption("IDFC FIRST Bank", Icons.Default.AccountBalance),
            BankOption("IndusInd Bank", Icons.Default.AccountBalance),
            BankOption("Punjab National Bank", Icons.Default.AccountBalance),
            BankOption("Standard Chartered", Icons.Default.AccountBalance),
            BankOption("Union Bank of India", Icons.Default.AccountBalance),
            BankOption("Yes Bank", Icons.Default.AccountBalance)
        )
    }

    // Filter Logic
    val filteredBanks = remember(searchQuery, allBanks) {
        if (searchQuery.isBlank()) allBanks
        else allBanks.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val popularBanks = remember(filteredBanks) {
        filteredBanks.filter { it.isPopular }.take(6) // Limit to top 6 for grid
    }

    // Simulate Network Loading
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800) // 800ms fake load
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BharatBlue)
    ) {
        // --- Header with Functional Search ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Top Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Pay Credit Card Bill",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )

                // Try to use resource, fallback to text if needed
                Image(
                    painter = painterResource(id = R.drawable.bharat_connect_text),
                    contentDescription = "Bharat Connect",
                    modifier = Modifier.height(18.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )

                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Help",
                    tint = Color.White
                )
            }

            // Functional Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.Black,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(BharatBlue),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search by bank name",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Search",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        // --- Content Area ---
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = BgGray
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BharatBlue)
                }
            } else if (filteredBanks.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No banks found for '$searchQuery'", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Popular Banks Section (Only show if search is empty or matches found)
                    if (popularBanks.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Popular banks",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    // Dynamic Grid Logic
                                    val rows = popularBanks.chunked(3)
                                    rows.forEachIndexed { index, rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // Fill gaps if row has less than 3 items
                                            for (i in 0 until 3) {
                                                if (i < rowItems.size) {
                                                    val bank = rowItems[i]
                                                    PopularBankItem(
                                                        name = bank.name,
                                                        icon = bank.icon,
                                                        onClick = {
                                                            val encoded = bank.name.replace(" ", "_")
                                                            // Navigate to Credit Card Details
                                                            navController.navigate("credit_card_details/$encoded")
                                                        }
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.width(85.dp)) // Placeholder
                                                }
                                            }
                                        }
                                        if (index < rows.size - 1) Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    // 2. All Banks Section
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "All banks",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)

                                filteredBanks.forEach { bank ->
                                    AllBankListItem(
                                        name = bank.name,
                                        onClick = {
                                            val encoded = bank.name.replace(" ", "_")
                                            // Navigate to Credit Card Details
                                            navController.navigate("credit_card_details/$encoded")
                                        }
                                    )
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = Color.LightGray.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// CREDIT CARD DETAILS SCREEN (NEW)


@Composable
fun CreditCardDetailsScreen(navController: NavController, bankName: String) {
    // State for inputs
    var lastFourDigits by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("123456789") }
    var isConsentGiven by remember { mutableStateOf(true) }
    val context = LocalContext.current
    Scaffold(
        topBar = {
            // --- Custom Blue Header ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BharatBlue)
                    .padding(16.dp)
            ) {
                // Top Row: Back & Help
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("HELP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bank Info Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // White logo box
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bankName.take(4).uppercase(),
                            color = BharatBlue,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = bankName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Bill Payments",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        },
        bottomBar = {
            Button(
                onClick = {
                    // Trigger the new Activity
                    val intent = Intent(context, CallableUI::class.java).apply {
                        // Pass the bank name and last 4 digits to the new screen
                        putExtra("BANK_NAME", bankName)
                        putExtra("LAST_FOUR", lastFourDigits)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BharatBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CONFIRM", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            }
        },
        containerColor = BgGray
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // --- Input Card ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // 1. Last 4 Digits Input
                    Text("Last 4 digits of Credit Card", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // The unique "Dots + Text" input field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Grey Dots (Visual only)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            repeat(3) { group ->
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    repeat(4) {
                                        Box(Modifier.size(6.dp).background(Color.LightGray, CircleShape))
                                    }
                                }
                                if (group < 2) Spacer(modifier = Modifier.width(8.dp))
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.height(24.dp).padding(horizontal = 12.dp),
                            color = Color.LightGray
                        )

                        // Actual Input
                        BasicTextField(
                            value = lastFourDigits,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) lastFourDigits = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                letterSpacing = 3.sp,
                                textAlign = TextAlign.Center
                            ),
                            singleLine = true,
                            modifier = Modifier.width(60.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (lastFourDigits.isEmpty()) {
                                        Text("0000", color = Color.LightGray.copy(alpha = 0.5f), fontSize = 18.sp, letterSpacing = 3.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. Mobile Number
                    Text("Mobile Number (Linked to credit card)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { mobileNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                            focusedBorderColor = BharatBlue
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.PermContactCalendar, null, tint = Color(0xFF9C27B0))
                        }
                    )
                }
            }

            // --- Consent Card ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = isConsentGiven,
                        onCheckedChange = { isConsentGiven = it },
                        colors = CheckboxDefaults.colors(checkedColor = BharatBlue),
                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Bharat Connect", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "By proceeding further, you allow BharatConnect to  fetch current and future bills, and send you reminders.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --- Helper Components (Credit Card) ---

@Composable
fun PopularBankItem(name: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(85.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = BharatBlue),
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$name logo",
                tint = BharatBlue.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            color = Color.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AllBankListItem(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo Placeholder
        Box(
            modifier = Modifier
                .size(40.dp) // Increased size slightly
                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                fontWeight = FontWeight.Bold,
                color = BharatBlue,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = name,
            fontSize = 14.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        // Chevron for affordance
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.LightGray
        )
    }
}


// --- Components (General) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneTopBar(onQrScanClick: () -> Unit = {}) {
    val context = LocalContext.current
    var showDeviceIdDialog by remember { mutableStateOf(false) }
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    if (showDeviceIdDialog) {
        DeviceIdDialog(deviceId = deviceId, onDismiss = { showDeviceIdDialog = false })
    }

    TopAppBar(
        title = {
            Column {
                Text("BBPS COU", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Add Address ▼", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        },
        actions = {
            IconButton(onClick = onQrScanClick) { Icon(Icons.Default.QrCodeScanner, "Scan", tint = Color.White) }
            IconButton(onClick = {}) { Icon(Icons.Default.Notifications, "Notify", tint = Color.White) }
            IconButton(onClick = { showDeviceIdDialog = true }) {
                Icon(Icons.Default.HelpOutline, "Help", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BharatBlue)
    )
}

@Composable
fun MoneyTransferGrid() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MoneyItem("To Contact", Icons.Default.Person)
        MoneyItem("To Account", Icons.Default.AccountBalance)
        MoneyItem("To Self", Icons.Default.SwapHoriz)
        MoneyItem("Split Bill", Icons.Default.CallSplit)
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MoneyItem("Check Bal", Icons.Default.HelpOutline)
        MoneyItem("Wallet", Icons.Default.AccountBalanceWallet)
        MoneyItem("Reminders", Icons.Default.CalendarToday)
        MoneyItem("Req. Money", Icons.Default.RequestQuote)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
fun MoneyItem(label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(45.dp).clip(CircleShape).background(BharatBlue)
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}

@Composable
fun RechargeGrid(navController: NavController) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RechargeItem("Mobile", Icons.Default.Smartphone, navController)
        RechargeItem("Electricity", Icons.Default.Lightbulb, navController)
        RechargeItem("Credit Card", Icons.Default.CreditCard, navController)
        RechargeItem("Broadband", Icons.Default.Router, navController)
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(Modifier.fillMaxWidth().padding(start = 12.dp)) {
        Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
            RechargeItem("Insurance", Icons.Default.Favorite, navController)
        }
        Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
            RechargeItem("Water", Icons.Default.WaterDrop, navController)
        }
    }
}

@Composable
fun RechargeItem(label: String, icon: ImageVector, navController: NavController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { navController.navigate("pay_bill/$label") }
    ) {
        Icon(icon, null, tint = BharatLightBlue, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun OfferSlider() {
    val pagerState = rememberPagerState(pageCount = { 5 })
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp
        ) {
            Card(
                modifier = Modifier.height(120.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Offer ${it + 1}", color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 20.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            repeat(5) {
                val color = if (pagerState.currentPage == it) BharatBlue else Color.LightGray
                Box(Modifier.padding(2.dp).size(6.dp).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
fun CloneBottomNav(onQrScanClick: () -> Unit = {}) {
    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Home", fontSize = 10.sp) },
            selected = true,
            onClick = {},
            colors = NavigationBarItemDefaults.colors(selectedIconColor = BharatBlue, indicatorColor = Color.White)
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.LocalOffer, null) },
            label = { Text("Stores", fontSize = 10.sp) },
            selected = false,
            onClick = {},
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Gray)
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.QrCodeScanner, null) },
            label = { Text("Scan", fontSize = 10.sp) },
            selected = false,
            onClick = onQrScanClick,
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Gray)
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, null) },
            label = { Text("Account", fontSize = 10.sp) },
            selected = false,
            onClick = {},
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Gray)
        )
        NavigationBarItem(
            icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
            label = { Text("History", fontSize = 10.sp) },
            selected = false,
            onClick = {},
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Gray)
        )
    }
}

// =========================================
// ELECTRICITY SCREEN
// =========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityScreen(navController: NavController) {
    val context = LocalContext.current
    var showDeviceIdDialog by remember { mutableStateOf(false) }
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    if (showDeviceIdDialog) {
        DeviceIdDialog(deviceId = deviceId, onDismiss = { showDeviceIdDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Electricity Provider", color = Color.White, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeviceIdDialog = true }) {
                        Icon(Icons.Default.HelpOutline, "Help", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BharatBlue)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchBar()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text("Recent Accounts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    BillerItem(
                        name = "BESCOM - Bangalore Electricity",
                        subtext = "CON123456789",
                        icon = Icons.Default.FlashOn,
                        onClick = {
                            navController.navigate("biller_details/BESCOM")
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
                    Text("Billers in Karnataka", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(getKarnatakaBillers()) { billerName ->
                    BillerItem(
                        name = billerName,
                        subtext = null,
                        icon = Icons.Default.Business,
                        onClick = {
                            val encoded = billerName.replace(" ", "_")
                            navController.navigate("biller_details/$encoded")
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun SearchBar() {
    var searchText by remember { mutableStateOf("") }
    OutlinedTextField(
        value = searchText,
        onValueChange = { searchText = it },
        placeholder = { Text("Search by biller") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            unfocusedBorderColor = Color.LightGray,
            focusedBorderColor = BharatBlue
        )
    )
}

@Composable
fun BillerItem(name: String, subtext: String?, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.dp, Color.LightGray, CircleShape)
        ) {
            Icon(icon, null, tint = BharatBlue, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, color = Color.Black)
            if (subtext != null) {
                Text(subtext, fontSize = 13.sp, color = Color.Gray)
            }
        }

        if (subtext != null) {
            Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
        }
    }
}

fun getKarnatakaBillers(): List<String> {
    return listOf(
        "BESCOM - Bangalore Electricity",
        "HESCOM - Hubli Electricity",
        "MESCOM - Mangalore Electricity",
        "GESCOM - Gulbarga Electricity"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillerDetailsScreen(navController: NavController, billerName: String) {
    val context = LocalContext.current
    var showDeviceIdDialog by remember { mutableStateOf(false) }
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    if (showDeviceIdDialog) {
        DeviceIdDialog(deviceId = deviceId, onDismiss = { showDeviceIdDialog = false })
    }

    // Prefill consumer number (no manual entry required)
    // In a real app this would come from the selected account / profile.
    var consumerNumber by remember { mutableStateOf("CON123456789") }
    val isButtonEnabled = consumerNumber.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = billerName,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.bharat_connect_text),
                            contentDescription = "Bharat BillPay",
                            modifier = Modifier.height(16.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { showDeviceIdDialog = true }) {
                            Icon(Icons.Default.HelpOutline, "Help", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BharatBlue)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val encodedName = billerName.replace(" ", "_")
                    navController.navigate("payment_screen/$encodedName/$consumerNumber")
                },
                enabled = isButtonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BharatBlue,
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CONFIRM", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Consumer Number", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = consumerNumber,
                onValueChange = { consumerNumber = it },
                placeholder = { Text("Please enter your Consumer Number") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BharatBlue,
                    unfocusedBorderColor = Color.LightGray,
                    cursorColor = BharatBlue
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.Top) {
                Image(
                    painter = painterResource(id = R.drawable.bharat_connect_logo),
                    contentDescription = "Bharat BillPay Logo",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "By proceeding further, you allow BBPS COU to fetch your current and future bills and remind you.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    navController: NavController,
    billerName: String,
    consumerNumber: String,
    qrPayload: QrPaymentPayload? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeviceIdDialog by remember { mutableStateOf(false) }

    // Get device ID
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    if (showDeviceIdDialog) {
        DeviceIdDialog(deviceId = deviceId, onDismiss = { showDeviceIdDialog = false })
    }

    fun launchCallableUi(response: EncryptedStatementResponse, payloadType: String) {
        val intent = Intent().apply {
            setClassName(
                "org.npci.bbps.callableui",
                "org.npci.bbps.callableui.entry.StatementRenderActivity"
            )
            putExtra("encryptedPayload", response.encryptedPayload)
            putExtra("wrappedDek", response.wrappedDek)
            putExtra("iv", response.iv)
            putExtra("senderPublicKey", response.senderPublicKey)
            putExtra("payloadType", payloadType)
        }
        Log.d("TPAP", "Launching callable app with encrypted data (payloadType=$payloadType)...")
        context.startActivity(intent)
    }

    fun startEncryptedFlow(
        payloadType: String,
        encryptFn: (String, EncryptStatementRequest) -> org.npci.bbps.tpap.model.EncryptedStatementResponse
    ) {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val baseUrl = AppConfig.baseUrl
                val consumerId = AppConfig.consumerId

                Log.d("TPAP", "Checking if callable app is installed...")
                val isCallableAppInstalled = withContext(Dispatchers.IO) {
                    DeviceKeyHelper.isCallableAppInstalled(context.packageManager)
                }

                Log.d("TPAP", "Attempting to retrieve public key from callable app...")
                val publicKey = withContext(Dispatchers.IO) {
                    DeviceKeyHelper.getPublicKey(context)
                }

                if (publicKey == null) {
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
                    if (!isCallableAppInstalled) {
                        Log.w("TPAP", "Package check failed but ContentProvider works - app is installed!")
                    } else {
                        Log.d("TPAP", "Callable app verified and public key retrieved")
                    }
                }

                Log.d("TPAP", "Successfully retrieved public key from callable app (length: ${publicKey.length})")

                var response = try {
                    withContext(Dispatchers.IO) {
                        encryptFn(
                            baseUrl,
                            EncryptStatementRequest(
                                statementId = AppConfig.statementId,
                                consumerId = consumerId,
                                deviceId = deviceId
                            )
                        )
                    }
                } catch (e: Exception) {
                    val errorMessage = e.message ?: ""
                    val isDeviceNotRegistered = errorMessage.contains("No ACTIVE device key found", ignoreCase = true) ||
                            errorMessage.contains("IllegalStateException", ignoreCase = true) ||
                            errorMessage.contains("device key", ignoreCase = true) ||
                            (errorMessage.contains("500") && errorMessage.contains("encrypt")) ||
                            errorMessage.contains("Failed to call", ignoreCase = true)

                    Log.d("TPAP", "Encryption error: $errorMessage")
                    Log.d("TPAP", "Is device not registered? $isDeviceNotRegistered")

                    if (isDeviceNotRegistered || errorMessage.contains("500")) {
                        Log.d("TPAP", "Device not registered. Registering now...")

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

                        withContext(Dispatchers.IO) {
                            encryptFn(
                                baseUrl,
                                EncryptStatementRequest(
                                    statementId = AppConfig.statementId,
                                    consumerId = consumerId,
                                    deviceId = deviceId
                                )
                            )
                        }
                    } else {
                        throw e
                    }
                }

                Log.d("TPAP", "Received encrypted response from backend")
                Log.d("TPAP", "Encrypted payload length: ${response.encryptedPayload.length}")
                Log.d("TPAP", "Wrapped DEK length: ${response.wrappedDek.length}")
                Log.d("TPAP", "IV: ${response.iv}")
                Log.d("TPAP", "Sender public key length: ${response.senderPublicKey.length}")

                launchCallableUi(response, payloadType)

            } catch (e: Exception) {
                Log.e("TPAP", "Error in secure payload flow", e)
                errorMessage = "Failed to load secure data: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val qrStatementResponse: EncryptedStatementResponse? = remember(qrPayload) {
        val encryptedPayload = qrPayload?.encryptedPayload?.trim().takeUnless { it.isNullOrEmpty() } ?: return@remember null
        val wrappedDek = qrPayload?.wrappedDek?.trim().takeUnless { it.isNullOrEmpty() } ?: return@remember null
        val iv = qrPayload?.iv?.trim().takeUnless { it.isNullOrEmpty() } ?: return@remember null
        val senderPublicKey = qrPayload?.senderPublicKey?.trim().takeUnless { it.isNullOrEmpty() } ?: return@remember null
        EncryptedStatementResponse(
            encryptedPayload = encryptedPayload,
            wrappedDek = wrappedDek,
            iv = iv,
            senderPublicKey = senderPublicKey,
            expiry = qrPayload?.expiry ?: 0L
        )
    }

    // Bill statement and payment history are separate encrypted APIs/screens
    val onBillStatementClick: () -> Unit = onBillStatementClick@{
        if (qrPayload != null) {
            if (qrStatementResponse == null) {
                errorMessage = "QR payload is missing encrypted statement fields."
                return@onBillStatementClick
            }
            try {
                launchCallableUi(qrStatementResponse, "BILL_STATEMENT")
            } catch (e: Exception) {
                Log.e("TPAP", "Failed to launch callable app from QR payload", e)
                errorMessage = "Failed to open bill statement: ${e.message}"
            }
        } else {
            startEncryptedFlow(
                payloadType = "BILL_STATEMENT",
                encryptFn = BackendApi::encryptBillStatement
            )
        }
    }

    val onPaymentHistoryClick: () -> Unit = {
        startEncryptedFlow(
            payloadType = "PAYMENT_HISTORY",
            encryptFn = BackendApi::encryptPaymentHistory
        )
    }

    // TPAP QR Scan Logic (from original TPAP MainActivity)
    val onScanQrClick: () -> Unit = {
        val intent = Intent(context, QrScanActivity::class.java)
        context.startActivity(intent)
    }

    val effectiveBillerName = qrPayload?.billerName?.takeIf { it.isNotBlank() }
        ?: qrPayload?.billerId?.takeIf { it.isNotBlank() }
        ?: billerName
    val effectiveConsumerNumber = qrPayload?.consumerNumber?.takeIf { it.isNotBlank() } ?: consumerNumber

    val amountText = qrPayload?.amount?.takeIf { it.isNotBlank() }?.let { raw ->
        val trimmed = raw.trim()
        if (trimmed.startsWith("₹")) trimmed else "₹ $trimmed"
    } ?: "₹ 101"
    val dueDateText = qrPayload?.dueDate?.takeIf { it.isNotBlank() }?.let { "Due Date: ${it.trim()}" }
        ?: "Due Date: 15-Jan-2026"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Pay Electricity Bill", color = Color.White, fontSize = 16.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.bharat_connect_text),
                            contentDescription = "Bharat Connect",
                            modifier = Modifier
                                .height(20.dp)
                                .width(60.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showDeviceIdDialog = true }) {
                            Icon(Icons.Default.HelpOutline, "Help", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BharatBlue)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Error Message (if any)
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp
                        )
                    }
                }

                // PAY BILL Button (does nothing - just UI)
                Button(
                    onClick = { /* Does nothing - just UI */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(55.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BharatBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("PAY BILL", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1. BILLER INFO CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE8EAF6), CircleShape)
                    ) {
                        Icon(Icons.Default.FlashOn, null, tint = BharatBlue)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(effectiveBillerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(effectiveConsumerNumber, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Bill Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // 2. MAIN DETAILS CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(amountText, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(dueDateText, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // SHOW MY STATEMENT BUTTON (triggers TPAP logic)
                    Button(
                        onClick = onBillStatementClick,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BharatBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            "Show bill statement",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // PAYMENT HISTORY BUTTON (launches callable UI with payment-only payload)
                    OutlinedButton(
                        onClick = onPaymentHistoryClick,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BharatBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Show payment history",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SCAN QR BUTTON (hidden when launched via QR scan)
                    if (qrPayload == null) {
                        OutlinedButton(
                            onClick = onScanQrClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BharatBlue
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Scan QR for Statement",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // QUICK PAY CHIPS
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceBetween
//                    ) {
//                        QuickAmountChip("₹201")
//                        QuickAmountChip("₹301")
//                        QuickAmountChip("₹401")
//                        QuickAmountChip("₹501")
//                    }
                }
            }
        }
    }
}

@Composable
fun QuickAmountChip(amount: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .border(1.dp, BharatBlue, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(amount, color = BharatBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun DeviceIdDialog(deviceId: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device ID") },
        text = {
            Text(
                text = "ANDROID_ID:\n${deviceId ?: "Unavailable"}",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}