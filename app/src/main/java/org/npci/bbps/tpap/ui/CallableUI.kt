package org.npci.bbps.tpap.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CallableUI : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val bankName = intent.getStringExtra("BANK_NAME") ?: "HDFC Bank Credit Card"
        val lastFour = intent.getStringExtra("LAST_FOUR") ?: "9017"

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgGray) {
                    CallableUIScreen(bankName, lastFour) { finish() }
                }
            }
        }
    }
}

@Composable
fun CallableUIScreen(bankName: String, lastFour: String, onBack: () -> Unit) {
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
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
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
                .offset(y = (-24).dp) // Overlap the blue header
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Bill Details Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("BANK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(bankName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("XXXX $lastFour", color = Color.Gray, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bill Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Hide", color = BharatBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    BillRow("Customer Name", "JOHN DOE")
                    BillRow("Bill Date", "08-Jan-2026")
                    BillRow("Minimum Amount Due", "₹ 100.00")
                    BillRow("Current Outstanding Amount", "₹ 199.00")

                    Spacer(Modifier.height(24.dp))

                    // Amount Display
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp)
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
                        modifier = Modifier.background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    // Selection Chips
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmountChip("MINIMUM DUE", 100, selectedAmount == 100, Modifier.weight(1f)) { selectedAmount = 100 }
                        AmountChip("TOTAL AMOUNT", 199, selectedAmount == 199, Modifier.weight(1f)) { selectedAmount = 199 }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Warning Note
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Note: $bankName will consider today's date as payment date. It may take upto 30 minutes to reflect in account.",
                        fontSize = 12.sp,
                        color = Color(0xFFE65100),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Footer text
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Text("Bharat Connect", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text("By proceeding further, you allow the app to store details and fetch bills.", fontSize = 11.sp, color = Color.Gray, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun BillRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) BharatBlue else Color.Gray)
        Text("₹$amount", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (isSelected) BharatBlue else Color.Black)
    }
}