package dev.andikune.robotasisten.io.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.service.OverlayService
import dev.andikune.robotasisten.io.ui.components.ModeCard
import dev.andikune.robotasisten.io.ui.components.StatusIndicator
import dev.andikune.robotasisten.io.ui.components.StatusType
import dev.andikune.robotasisten.io.ui.theme.SuccessGreen
import dev.andikune.robotasisten.io.util.PermissionHelper
import dev.andikune.robotasisten.io.util.ToastHelper

@Composable
fun HomeScreen(
    onNavigateToLibrary: () -> Unit,
    onNavigateToOffline: () -> Unit,
    onNavigateToCloud: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = AppModule.provideSettingsRepository(context)
    val historyRepo = AppModule.provideHistoryRepository(context)
    val ruleRepo = AppModule.provideRuleRepository(context)
    val keyStore = AppModule.provideApiKeyStore(context)
    val modelDownloader = AppModule.provideModelDownloader(context)

    val activeMode by settingsRepo.activeMode.collectAsState()
    val isRobotRunning by OverlayService.isRunning.collectAsState()
    val stats by historyRepo.statsFlow.collectAsState(initial = null)
    val rules by ruleRepo.allRules.collectAsState(initial = emptyList())

    // Readiness evaluation
    val isLibraryReady = rules.isNotEmpty()
    val isOfflineReady = modelDownloader.availableModels.any { modelDownloader.isModelDownloaded(it) }
    val isCloudReady = keyStore.hasApiKey(settingsRepo.selectedProvider.value)

    val isCurrentModeReady = when (activeMode) {
        "LIBRARY" -> isLibraryReady
        "AI_OFFLINE" -> isOfflineReady
        "AI_OPEN_KEY" -> isCloudReady
        else -> false
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(painterResource(R.drawable.ic_robot), contentDescription = "Beranda", modifier = Modifier.size(22.dp)) },
                    label = { Text("Beranda") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToHistory,
                    icon = { Icon(Icons.Default.History, contentDescription = "Riwayat") },
                    label = { Text("Riwayat") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                    label = { Text("Pengaturan") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToHelp,
                    icon = { Icon(Icons.Default.HelpOutline, contentDescription = "Bantuan") },
                    label = { Text("Bantuan") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_robot),
                        contentDescription = "Logo Robot",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "R A . i O",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Asisten belajar dengan overlay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onNavigateToPermissions) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Izin Layanan",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status Card
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mode Aktif: $activeMode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        StatusIndicator(
                            text = if (isRobotRunning) "Mengambang" else "Standby",
                            status = if (isRobotRunning) StatusType.SUCCESS else StatusType.IDLE
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Kuota AI hari ini: ${stats?.dailyQuotaUsed ?: 0} / 500",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Pilih Mode Operasi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Mode Library
            ModeCard(
                title = "Mode Library",
                description = "Jawab tanpa AI, tanpa internet. Cepat & gratis.",
                icon = Icons.Default.Book,
                isSelected = activeMode == "LIBRARY",
                isReady = isLibraryReady,
                statusBadgeText = if (isLibraryReady) "${rules.size} aturan aktif" else "Belum ada aturan",
                onClick = {
                    settingsRepo.setActiveMode("LIBRARY")
                    onNavigateToLibrary()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Mode AI Offline
            ModeCard(
                title = "Mode AI Offline",
                description = "AI di HP. Unduh model sekali, pakai selamanya.",
                icon = Icons.Default.Memory,
                isSelected = activeMode == "AI_OFFLINE",
                isReady = isOfflineReady,
                statusBadgeText = if (isOfflineReady) "Model terunduh & siap" else "Perlu unduh model",
                onClick = {
                    settingsRepo.setActiveMode("AI_OFFLINE")
                    onNavigateToOffline()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Mode AI Open Key
            ModeCard(
                title = "Mode AI Open Key",
                description = "Pakai API key sendiri. Pilih provider & model.",
                icon = Icons.Default.Key,
                isSelected = activeMode == "AI_OPEN_KEY",
                isReady = isCloudReady,
                statusBadgeText = if (isCloudReady) "Kunci tersimpan (${settingsRepo.selectedProvider.value})" else "Perlu input API Key",
                onClick = {
                    settingsRepo.setActiveMode("AI_OPEN_KEY")
                    onNavigateToCloud()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // TOMBOL BESAR "MULAI ROBOT"
            Button(
                onClick = {
                    if (isRobotRunning) {
                        val stopIntent = Intent(context, OverlayService::class.java)
                        context.stopService(stopIntent)
                        ToastHelper.showShort(context, "⏸ Robot dihentikan")
                    } else {
                        if (!PermissionHelper.hasOverlayPermission(context)) {
                            ToastHelper.showLong(context, "Buka izin overlay terlebih dahulu")
                            PermissionHelper.openOverlaySettings(context)
                            return@Button
                        }
                        val startIntent = Intent(context, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(startIntent)
                        } else {
                            context.startService(startIntent)
                        }
                        ToastHelper.showShort(context, "🚀 Robot mengambang diaktifkan!")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("start_robot_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRobotRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                enabled = isRobotRunning || isCurrentModeReady
            ) {
                Text(
                    text = if (isRobotRunning) "⏸ HENTIKAN ROBOT" else "🚀 MULAI ROBOT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            if (!isCurrentModeReady && !isRobotRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Siapkan mode aktif di atas agar tombol dapat dijalankan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
