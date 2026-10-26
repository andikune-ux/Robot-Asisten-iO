package dev.andikune.robotasisten.io.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigatePermissions: () -> Unit,
    onNavigateDiagnostic: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigatePrivacy: () -> Unit,
    onNavigateLicense: () -> Unit,
    onNavigateDebug: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = AppModule.provideSettingsRepository(context)
    val cacheManager = AppModule.provideCacheManager(context)
    val scope = rememberCoroutineScope()

    val overlaySize by settingsRepo.overlaySizeDp.collectAsState()
    val overlayOpacity by settingsRepo.overlayOpacity.collectAsState()
    val confirmBeforeFill by settingsRepo.confirmBeforeFill.collectAsState()
    val autoAnalyze by settingsRepo.autoAnalyze.collectAsState()
    val silentMode by settingsRepo.silentMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ Pengaturan") },
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
            // Overlay Settings
            Text(
                text = "Tampilan Bubble Robot",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ukuran Bubble: ${overlaySize}dp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = overlaySize.toFloat(),
                        onValueChange = { settingsRepo.setOverlaySize(it.toInt()) },
                        valueRange = 40f..80f,
                        steps = 7
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Transparansi / Opasitas: ${(overlayOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = overlayOpacity,
                        onValueChange = { settingsRepo.setOverlayOpacity(it) },
                        valueRange = 0.3f..1.0f
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Behavioral Toggles
            Text(
                text = "Perilaku Asisten",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Konfirmasi Sebelum Isi", fontWeight = FontWeight.Bold)
                            Text("Tampilkan tinjauan jawaban sebelum mengisi kolom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = confirmBeforeFill,
                            onCheckedChange = { settingsRepo.setConfirmBeforeFill(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Analisa Otomatis Layar", fontWeight = FontWeight.Bold)
                            Text("Pindai saat berpindah soal secara langsung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoAnalyze,
                            onCheckedChange = { settingsRepo.setAutoAnalyze(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode Senyap", fontWeight = FontWeight.Bold)
                            Text("Matikan efek suara beep dan getaran", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = silentMode,
                            onCheckedChange = { settingsRepo.setSilentMode(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Storage & Cache
            Text(
                text = "Penyimpanan & Cache",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        cacheManager.clear()
                        ToastHelper.showShort(context, "✅ Cache berhasil dibersihkan")
                    }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Bersihkan Cache Jawaban")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Links
            Text(
                text = "Informasi & Diagnostik",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsRow(title = "Daftar Izin & Aksesibilitas", icon = Icons.Default.VerifiedUser, onClick = onNavigatePermissions)
                    HorizontalDivider()
                    SettingsRow(title = "Diagnostik Sistem & HW", icon = Icons.Default.Speed, onClick = onNavigateDiagnostic)
                    HorizontalDivider()
                    SettingsRow(title = "Tentang Aplikasi R A . i O", icon = Icons.Default.Info, onClick = onNavigateAbout)
                    HorizontalDivider()
                    SettingsRow(title = "Kebijakan Privasi & Etika", icon = Icons.Default.Policy, onClick = onNavigatePrivacy)
                    HorizontalDivider()
                    SettingsRow(title = "Debug Screen & Log", icon = Icons.Default.BugReport, onClick = onNavigateDebug)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
