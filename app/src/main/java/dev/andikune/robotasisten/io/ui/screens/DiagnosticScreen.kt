package dev.andikune.robotasisten.io.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.service.OverlayService
import dev.andikune.robotasisten.io.util.DeviceHelper
import dev.andikune.robotasisten.io.util.NetworkHelper
import dev.andikune.robotasisten.io.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val historyRepo = AppModule.provideHistoryRepository(context)
    val stats by historyRepo.statsFlow.collectAsState(initial = null)
    val isRobotRunning by OverlayService.isRunning.collectAsState()

    val deviceInfo = DeviceHelper.getDeviceInfo(context)
    val isOnline = NetworkHelper.isConnected(context)
    val hasOverlay = PermissionHelper.hasOverlayPermission(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Diagnostik Sistem") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Informasi Perangkat Keras & Status Layanan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    deviceInfo.forEach { (k, v) ->
                        DiagnosticRow(k, v)
                    }
                    DiagnosticRow("Koneksi Internet", if (isOnline) "Terhubung (Online) 🌐" else "Offline 📴")
                    DiagnosticRow("Izin Overlay", if (hasOverlay) "Diizinkan ✅" else "Belum Diizinkan ❌")
                    DiagnosticRow("Status Robot Overlay", if (isRobotRunning) "Aktif Mengambang 🚀" else "Idle / Berhenti ⏸")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Statistik Penggunaan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DiagnosticRow("Total Soal Diproses", "${stats?.totalQuestions ?: 0}")
                    DiagnosticRow("Dijawab via Library", "${stats?.libraryCount ?: 0}")
                    DiagnosticRow("Dijawab via AI Offline", "${stats?.offlineCount ?: 0}")
                    DiagnosticRow("Dijawab via Cloud Open Key", "${stats?.cloudCount ?: 0}")
                    DiagnosticRow("Penghematan Cache (Cache Hits)", "${stats?.cacheHits ?: 0}")
                    DiagnosticRow("Penggunaan Kuota Cloud Hari Ini", "${stats?.dailyQuotaUsed ?: 0} panggilan")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
