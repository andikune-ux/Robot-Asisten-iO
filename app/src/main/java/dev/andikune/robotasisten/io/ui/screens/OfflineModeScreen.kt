package dev.andikune.robotasisten.io.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.ui.theme.SuccessGreen
import dev.andikune.robotasisten.io.util.DeviceHelper
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineModeScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val downloader = AppModule.provideModelDownloader(context)
    val settingsRepo = AppModule.provideSettingsRepository(context)
    val selectedModel by settingsRepo.selectedOfflineModel.collectAsState()
    val progressMap by downloader.downloadProgress.collectAsState()
    val scope = rememberCoroutineScope()
    var downloadingModelId by remember { mutableStateOf<String?>(null) }
    var downloadFailed by remember { mutableStateOf(false) }
    val freeStorage = DeviceHelper.getDeviceInfo(context)["Available Storage"] ?: "Tidak diketahui"

    Scaffold(topBar = {
        TopAppBar(title = { Text("AI Offline (Model di HP)") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
        })
    }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Sisa ruang penyimpanan: $freeStorage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Unduh model MediaPipe resmi (sekitar 547 MB). Model baru dapat diaktifkan setelah ukuran dan SHA-256 terverifikasi. Pengunduhan memerlukan internet; inferensi berjalan di perangkat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(downloader.availableModels, key = { it.id }) { model ->
                    val installed = downloader.isModelDownloaded(model)
                    val active = selectedModel == model.id && installed
                    val progress = progressMap[model.id]
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Ukuran: ${model.sizeText} · MediaPipe Tasks GenAI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (active) Text("AKTIF", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.Bold)
                            }
                            if (progress != null) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text(if (progress >= 1f) "Memverifikasi model…" else "Mengunduh… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (downloadFailed) {
                                Spacer(Modifier.height(6.dp))
                                Text("Unduhan atau verifikasi gagal. Periksa koneksi dan coba lagi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                when {
                                    downloadingModelId == model.id -> Button(onClick = {}, enabled = false, shape = RoundedCornerShape(8.dp)) { Text("Sedang memproses…") }
                                    !installed -> Button(
                                        onClick = {
                                            downloadingModelId = model.id
                                            downloadFailed = false
                                            scope.launch {
                                                try {
                                                    downloader.startDownload(model) { success ->
                                                        downloadFailed = !success
                                                        if (success) ToastHelper.showShort(context, "Model berhasil diunduh dan diverifikasi")
                                                    }
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (error: Exception) {
                                                    downloadFailed = true
                                                    ToastHelper.showShort(context, "Unduhan gagal: ${error.message ?: "coba lagi"}")
                                                } finally {
                                                    downloadingModelId = null
                                                }
                                            }
                                        }, shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (downloadFailed) "Coba Lagi" else "Unduh Model")
                                    }
                                    active -> Button(onClick = {}, enabled = false, shape = RoundedCornerShape(8.dp)) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Sedang Digunakan")
                                    }
                                    else -> OutlinedButton(
                                        onClick = {
                                            settingsRepo.setSelectedOfflineModel(model.id)
                                            ToastHelper.showShort(context, "Model ${model.name} diaktifkan")
                                        }, shape = RoundedCornerShape(8.dp)
                                    ) { Text("Aktifkan Model") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
