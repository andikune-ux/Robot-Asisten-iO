package dev.andikune.robotasisten.io.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.service.OverlayService
import dev.andikune.robotasisten.io.service.MyAccessibilityService
import dev.andikune.robotasisten.io.ui.theme.CyanAccent
import dev.andikune.robotasisten.io.ui.theme.ErrorRed
import dev.andikune.robotasisten.io.ui.theme.SuccessGreen
import dev.andikune.robotasisten.io.ui.theme.TechBluePrimary
import dev.andikune.robotasisten.io.util.ClipboardHelper
import dev.andikune.robotasisten.io.util.PermissionHelper
import dev.andikune.robotasisten.io.util.ToastHelper

@Composable
fun HomeScreen(
    onNavigateSettings: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateLibrary: () -> Unit,
    onNavigateOffline: () -> Unit,
    onNavigateCloud: () -> Unit,
    onNavigatePermissions: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = AppModule.provideSettingsRepository(context)
    val ruleRepo = AppModule.provideRuleRepository(context)
    val isRobotRunning by OverlayService.isRunning.collectAsState()
    val savedMode by settingsRepo.activeMode.collectAsState()
    val rules by ruleRepo.allRules.collectAsState(initial = emptyList())

    var selectedMode by remember(savedMode) {
        mutableStateOf(savedMode.lowercase())
    }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var startPending by remember { mutableStateOf(false) }

    LaunchedEffect(isRobotRunning, startPending) {
        if (isRobotRunning) {
            startPending = false
            ToastHelper.showShort(context, "Overlay RELX.iO siap digunakan")
        } else if (startPending) {
            delay(8_000)
            if (!OverlayService.isRunning.value) startPending = false
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { /* Already on Home */ },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateHistory,
                    icon = { Icon(Icons.Default.History, contentDescription = "Riwayat") },
                    label = { Text("Riwayat") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateSettings,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                    label = { Text("Pengaturan") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top App Bar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_art),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onNavigatePermissions,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Izin Layanan",
                        tint = if (PermissionHelper.hasOverlayPermission(context)) SuccessGreen else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hero Status Banner (Glassmorphism effect)
            HeroStatusCard(
                isRunning = isRobotRunning,
                currentMode = selectedMode,
                onCheckPermissions = onNavigatePermissions
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Pilih Mode Operasi Asisten",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Kartu Mode Library
            PremiumModeCard(
                title = stringResource(R.string.mode_library),
                subtitle = "Jawab instan tanpa AI & internet. 100% gratis.",
                badgeText = "${rules.size} Aturan Aktif",
                badgeColor = SuccessGreen,
                icon = Icons.Default.Book,
                isSelected = selectedMode.equals("library", ignoreCase = true),
                onClick = {
                    selectedMode = "library"
                    settingsRepo.setActiveMode("LIBRARY")
                },
                onConfigureClick = onNavigateLibrary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Kartu Mode AI Offline
            PremiumModeCard(
                title = stringResource(R.string.mode_offline),
                subtitle = "Qwen2.5 0.5B di perangkat. Unduh model sekitar 547 MB untuk inferensi lokal.",
                badgeText = "On-Device MediaPipe",
                badgeColor = Color(0xFF8B5CF6),
                icon = Icons.Default.Memory,
                isSelected = selectedMode.equals("offline", ignoreCase = true) || selectedMode.equals("ai_offline", ignoreCase = true),
                onClick = {
                    selectedMode = "offline"
                    settingsRepo.setActiveMode("AI_OFFLINE")
                },
                onConfigureClick = onNavigateOffline
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Kartu Mode AI Cloud Open Key
            PremiumModeCard(
                title = stringResource(R.string.mode_cloud),
                subtitle = "API key untuk provider OpenAI-compatible, misalnya OpenAI, Groq, DeepSeek, dan OpenRouter.",
                badgeText = "Multi-Provider AI",
                badgeColor = CyanAccent,
                icon = Icons.Default.Key,
                isSelected = selectedMode.equals("cloud", ignoreCase = true) || selectedMode.equals("ai_open_key", ignoreCase = true),
                onClick = {
                    selectedMode = "cloud"
                    settingsRepo.setActiveMode("AI_OPEN_KEY")
                },
                onConfigureClick = onNavigateCloud
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedMode !in setOf("library", "offline", "cloud")) {
                Text(
                    text = "Pilih salah satu mode di atas untuk mengaktifkan tombol mulai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // TOMBOL UTAMA BESAR 64dp
            val buttonColor by animateColorAsState(
                targetValue = if (isRobotRunning) ErrorRed else MaterialTheme.colorScheme.primary,
                animationSpec = tween(300),
                label = "btnColor"
            )

            Button(
                onClick = {
                    if (isRobotRunning) {
                        val stopIntent = Intent(context, OverlayService::class.java)
                        val accepted = context.stopService(stopIntent)
                        ToastHelper.showShort(context, if (accepted) "Permintaan menghentikan overlay dikirim" else "Overlay tidak sedang berjalan")
                    } else {
                        if (selectedMode !in setOf("library", "offline", "cloud")) {
                            ToastHelper.showShort(context, "Pilih mode operasi terlebih dahulu")
                        } else if (!PermissionHelper.hasOverlayPermission(context) ||
                            !PermissionHelper.isAccessibilityServiceEnabled(context, MyAccessibilityService::class.java)
                        ) {
                            showPermissionDialog = true
                        } else {
                            startPending = true
                            startRobotOverlay(context, selectedMode)
                        }
                    }
                },
                enabled = isRobotRunning || (!startPending && selectedMode in setOf("library", "offline", "cloud")),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(
                        elevation = if (isRobotRunning) 8.dp else 6.dp,
                        shape = RoundedCornerShape(18.dp)
                    ),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isRobotRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isRobotRunning) "⏸ HENTIKAN ROBOT MENGAMBANG" else "🚀 MULAI ROBOT SEKARANG",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Shortcut Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        ClipboardHelper.copyToClipboard(
                            context,
                            "Soal Uji",
                            "Apa ibu kota negara Indonesia?\nA. Jakarta\nB. Nusantara\nC. Surabaya\nD. Bandung"
                        )
                        ToastHelper.showShort(context, "📋 Contoh soal disalin! Buka robot untuk jawab.")
                    },
                    label = { Text("📋 Contoh Soal", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = onNavigatePermissions,
                    label = { Text("🛡️ Cek Izin", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = onNavigateHistory,
                    label = { Text("📜 Riwayat", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Izin Overlay dan Aksesibilitas diperlukan")
            },
            text = {
                Text("Untuk menampilkan bubble dan membaca soal layar, aktifkan izin 'Tampil di atas aplikasi lain' serta Layanan Aksesibilitas RELX.iO. Kedua izin dapat ditinjau dan diaktifkan dari Checklist Izin.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        onNavigatePermissions()
                    }
                ) {
                    Text("Buka Pengaturan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermissionDialog = false }) {
                    Text("Nanti Saja")
                }
            }
        )
    }
}

private fun startRobotOverlay(context: Context, mode: String) {
    val serviceMode = when (mode.lowercase()) {
        "library" -> "LIBRARY"
        "offline", "ai_offline" -> "AI_OFFLINE"
        "cloud", "ai_open_key" -> "AI_OPEN_KEY"
        else -> {
            ToastHelper.showShort(context, "Pilih mode yang valid sebelum memulai")
            return
        }
    }
    val startIntent = Intent(context, OverlayService::class.java).apply {
        putExtra("mode", serviceMode)
    }
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(startIntent)
        else context.startService(startIntent)
    }.onSuccess {
        ToastHelper.showShort(context, "Meminta RELX.iO memulai overlay…")
    }.onFailure { error ->
        ToastHelper.showShort(context, "Overlay gagal dimulai: ${error.message ?: "periksa izin"}")
    }
}

@Composable
private fun HeroStatusCard(
    isRunning: Boolean,
    currentMode: String,
    onCheckPermissions: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isRunning) TechBluePrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) SuccessGreen else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "STATUS: AKTIF MENGAMBANG" else "STATUS: STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Mode Aktif: ${currentMode.replace("_", " ").uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onCheckPermissions() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isRunning) "🤖 Berjalan" else "Siap Pakai",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PremiumModeCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    onConfigureClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon avatar with themed background
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Selection checkmark
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Terpilih" else "Tidak dipilih",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Quick configure chip
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onConfigureClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Kelola & Uji Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        Surface {
            HomeScreen(
                onNavigateSettings = {},
                onNavigateHistory = {},
                onNavigateLibrary = {},
                onNavigateOffline = {},
                onNavigateCloud = {},
                onNavigatePermissions = {}
            )
        }
    }
}
