package org.npci.bbps.tpap.ui

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// --- Global Colors ---
//val BharatBlue = Color(0xFF1976D2)
//val BgGray = Color(0xFFF1F3F6)
val RewardGreenBg = Color(0xFFE8F5E9)
val RewardGreenText = Color(0xFF2E7D32)
val DarkText = Color(0xFF212121)

class CallableUI : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security: Prevent screenshots of sensitive financial data
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val bankName = intent.getStringExtra("BANK_NAME") ?: "HDFC Bank Credit Card"
        val lastFour = intent.getStringExtra("LAST_FOUR") ?: "9017"

        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                Surface(modifier = Modifier.fillMaxSize(), color = BgGray) {
                    NavHost(navController = navController, startDestination = "bill_summary") {

                        // Screen 1: Bill Summary (Entry Point)
                        composable("bill_summary") {
                            CallableUIScreen(
                                bankName = bankName,
                                lastFour = lastFour,
                                onBack = { finish() },
                                onViewStatementClick = { navController.navigate("view_statement") }
                            )
                        }

                        // Screen 2: View Statement Options
                        composable("view_statement") {
                            ViewStatementScreen(
                                onBack = { navController.popBackStack() },
                                onOptionClick = { month ->
                                    navController.navigate("statement_details/$month")
                                }
                            )
                        }

                        // Screen 3: Statement Details (Transactions)
                        composable("statement_details/{month}") { backStackEntry ->
                            val month = backStackEntry.arguments?.getString("month") ?: "Current"
                            StatementDetailsScreen(
                                bankName = bankName,
                                lastFour = lastFour,
                                month = month,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}


// SCREEN 1: BILL SUMMARY (HOME)

@Composable
fun CallableUIScreen(
    bankName: String,
    lastFour: String,
    onBack: () -> Unit,
    onViewStatementClick: () -> Unit
) {
    var selectedAmount by remember { mutableStateOf(199) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BharatBlue)
                    .padding(top = 16.dp, bottom = 40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Text("Pay Credit Card Bill", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { /* Help */ }) {
                        Icon(Icons.Default.HelpOutline, null, tint = Color.White)
                    }
                }
            }
        },
        bottomBar = {
            Button(
                onClick = { /* Process Payment */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BharatBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("PAY YOUR BILL", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .offset(y = (-24).dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("BANK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(bankName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("XXXX $lastFour", color = Color.Gray, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    BillRow("Customer Name", "JOHN DOE")
                    BillRow("Bill Date", "08-Jan-2026")
                    BillRow("Minimum Amount Due", "₹ 100.00")
                    BillRow("Current Outstanding Amount", "₹ 199.00")

                    Spacer(Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("₹ $selectedAmount", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Due Date: 26-Jan-2026",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmountChip("MINIMUM DUE", 100, selectedAmount == 100, Modifier.weight(1f)) { selectedAmount = 100 }
                        AmountChip("TOTAL AMOUNT", 199, selectedAmount == 199, Modifier.weight(1f)) { selectedAmount = 199 }
                    }

                    Spacer(Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = onViewStatementClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, BharatBlue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BharatBlue)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("VIEW MY STATEMENT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Note: $bankName will consider today's date as payment date.",
                        fontSize = 12.sp,
                        color = Color(0xFFE65100)
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}


// SCREEN 2: VIEW STATEMENT OPTIONS


@Composable
fun ViewStatementScreen(onBack: () -> Unit, onOptionClick: (String) -> Unit) {
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BharatBlue)
                    .padding(top = 16.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        text = "View Statement",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("HELP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }
        },
        containerColor = BgGray
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            StatementOptionItem("Current Month", "View statement for Jan '26") { onOptionClick("Jan '26") }
            Spacer(Modifier.height(16.dp))
            StatementOptionItem("Last Month", "View statement for Dec '25") { onOptionClick("Dec '25") }
            Spacer(Modifier.height(16.dp))
            StatementOptionItem("Past Month", "View Past Month Statements") { onOptionClick("Past") }
            Spacer(Modifier.height(16.dp))
            StatementOptionItem("Annual", "View Annual Statements") { onOptionClick("Annual") }
            Spacer(Modifier.height(16.dp))
            StatementOptionItem("Custom Statement", "Customize Statements") { onOptionClick("Custom") }
        }
    }
}

@Composable
fun StatementOptionItem(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkText)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, fontSize = 14.sp, color = Color.Gray)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}


// SCREEN 3: STATEMENT DETAILS (TRANSACTIONS)


@Composable
fun StatementDetailsScreen(
    bankName: String,
    lastFour: String,
    month: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Mock Data
    val transactions = remember {
        listOf(
            Transaction("BLUEDART665D6", "12134567788", "- ₹900", "10 $month", 47),
            Transaction("FLIPKART65D6", "126345567", "- ₹500", "03 $month", 25),
            Transaction("AMAZONPAYIN", "12612345667", "- ₹1,200", "24 Prev", 70),
            Transaction("AMAZON665D6", "12456789902", "- ₹4,239", "21 Prev", 0)
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BharatBlue)
                    .padding(top = 16.dp, bottom = 40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        text = "Last Statement",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        },
        containerColor = BgGray
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .offset(y = (-30).dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 1. Credit Card Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Visual Card Placeholder
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF263238)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "VISA",
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(bankName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
                        Text(
                            "XXXX XXXX XXXX $lastFour",
                            color = BharatBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Sub-header (Select Transactions / Dispute)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Transactions", fontSize = 14.sp, color = Color.Gray)
                Text(
                    "Raise a Dispute >",
                    fontSize = 14.sp,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Opening Dispute Flow...", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Transactions List (with Empty State)
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions found for this period", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(transactions) { txn ->
                        TransactionItem(txn)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(txn: Transaction) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle specific transaction click */ }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left Side: Name and Ref ID
                Column(modifier = Modifier.weight(1f)) {
                    Text(txn.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
                    Spacer(Modifier.height(4.dp))
                    Text("REF ID: ${txn.refId}", fontSize = 11.sp, color = Color.Gray)
                }

                // Right Side: Amount and Date
                Column(horizontalAlignment = Alignment.End) {
                    Text(txn.amount, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
                    Spacer(Modifier.height(4.dp))
                    Text(txn.date, fontSize = 11.sp, color = Color.Gray)
                }
            }

            // Reward Points Badge
            if (txn.points > 0) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = RewardGreenBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = RewardGreenText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${txn.points}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RewardGreenText
                        )
                    }
                }
            }
        }
    }
}


// DATA MODELS & HELPERS


data class Transaction(
    val name: String,
    val refId: String,
    val amount: String,
    val date: String,
    val points: Int
)

@Composable
fun BillRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
    }
}

@Composable
fun AmountChip(label: String, amount: Int, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val borderColor = if (isSelected) BharatBlue else Color.LightGray.copy(alpha = 0.5f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Column(
        modifier = modifier
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) BharatBlue else Color.Gray
        )
        Text(
            "₹$amount",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isSelected) BharatBlue else DarkText
        )
    }
}