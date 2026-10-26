package dev.andikune.robotasisten.io.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.util.HapticHelper
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.SoundHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onNavigateBack: () -> Unit
) {
    var logs by remember { mutableStateOf(LogHelper.getLogs()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛠️ Debug Screen & Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        LogHelper.clearLogs()
                        logs = emptyList()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Bersihkan Log")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        SoundHelper.playSuccess()
                        LogHelper.i("DebugScreen", "Test sound & haptic executed")
                        logs = LogHelper.getLogs()
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    Text("Tes Suara", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        logs = LogHelper.getLogs()
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh Log (${logs.size})", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Log Aktivitas Internal (In-Memory Buffer):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxSize()
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "Log kosong. Tekan refresh setelah melakukan aktivitas.",
                        color = Color.LightGray,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs.reversed()) { logEntry ->
                            Text(
                                text = logEntry,
                                color = if (logEntry.contains("ERROR")) Color(0xFFEF4444) else if (logEntry.contains("WARN")) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
