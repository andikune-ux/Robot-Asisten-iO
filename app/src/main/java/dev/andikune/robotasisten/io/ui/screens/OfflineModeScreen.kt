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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import dev.andikune.robotasisten.io.data.ai.OfflineModelSpec
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
    var failedModelIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf<OfflineModelSpec?>(null) }
    val freeStorage = DeviceHelper.getDeviceInfo(context)["Available Storage"] ?: "Tidak diketahui"

    fun isSelected(model: OfflineModelSpec): Boolean =
        selectedModel.equals(model.id, true) || selectedModel.equals(model.name, true) ||
            (model.id == dev.andikune.robotasisten.io.data.ai.ModelDownloader.MODEL_ID && selectedModel.equals("Qwen 2.5 0.5B", true))

    fun beginDownload(model: OfflineModelSpec) {
        if (downloadingModelId != null) return
        downloadingModelId = model.id
        failedModelIds = failedModelIds - model.id
        scope.launch {
            try {
                downloader.startDownload(model) { success ->
                    if (success) {
                        settingsRepo.setSelectedOfflineModel(model.id)
                        failedModelIds = failedModelIds - model.id
                        ToastHelper.showShort(context, "Model berhasil diunduh, diverifikasi, dan diaktifkan")
                    } else {
                        failedModelIds = failedModelIds + model.id
                        ToastHelper.showShort(context, "Unduhan gagal. Tekan Coba Lagi untuk melanjutkan data yang tersimpan.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failedModelIds = failedModelIds + model.id
                ToastHelper.showShort(context, "Unduhan gagal: ${error.message ?: "coba lagi"}")
            } finally {
                downloadingModelId = null
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("AI Offline (Model di HP)") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
        })
    }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Sisa ruang penyimpanan: $freeStorage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pilih dari model ringan ke model yang lebih mampu. Unduhan diverifikasi dengan SHA-256. Jika koneksi terputus, data parsial disimpan dan Coba Lagi melanjutkan lewat HTTP Range bila server mendukungnya. Model berjalan di perangkat setelah dipilih.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(downloader.availableModels, key = { it.id }) { model ->
                    val installed = downloader.isModelDownloaded(model)
                    val active = isSelected(model) && installed
                    val progress = progressMap[model.id]
                    val partialBytes = downloader.partialDownloadedBytes(model)
                    val hasFiles = downloader.hasModelFiles(model)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Ukuran: ${model.sizeText} · MediaPipe task · Apache-2.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (active) Text("AKTIF", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(model.capability, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(model.recommendedRamText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (progress != null) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("Mengunduh/memverifikasi ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else if (!installed && partialBytes > 0L) {
                                Spacer(Modifier.height(6.dp))
                                Text("Data tersimpan: ${formatMiB(partialBytes)} dari ${model.sizeText}; coba lagi akan melanjutkan unduhan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (model.id in failedModelIds) {
                                Spacer(Modifier.height(6.dp))
                                Text("Unduhan gagal. File parsial yang belum lengkap dipertahankan agar bisa dilanjutkan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                if (hasFiles && downloadingModelId != model.id) {
                                    IconButton(onClick = { pendingDelete = model }, enabled = true) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus file model ${model.name}", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                when {
                                    downloadingModelId == model.id -> Button(onClick = {}, enabled = false, shape = RoundedCornerShape(8.dp)) { Text("Sedang memproses…") }
                                    !installed -> Button(onClick = { beginDownload(model) }, shape = RoundedCornerShape(8.dp)) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (model.id in failedModelIds || partialBytes > 0L) "Coba Lagi / Lanjutkan" else "Unduh Model")
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

    pendingDelete?.let { model ->
        val deletingActiveModel = isSelected(model) && downloader.isModelDownloaded(model)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus model offline?") },
            text = {
                Text(if (deletingActiveModel)
                    "${model.name} sedang dipilih. File, status verifikasi, dan unduhan parsial akan dihapus; pilihan model offline dikosongkan dan model dalam memori ditutup. Anda perlu mengunduh model lagi untuk memakai mode Offline."
                else "Hapus model ${model.name}, status verifikasi, dan data unduhan parsial dari penyimpanan privat aplikasi?")
            },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = null
                    scope.launch {
                        if (deletingActiveModel) {
                            settingsRepo.setSelectedOfflineModel("")
                            AppModule.provideOfflineAi(context).releaseCachedEngine()
                        }
                        val deleted = downloader.deleteModelFiles(model)
                        ToastHelper.showShort(context, if (deleted) "File model dihapus" else "Gagal menghapus sebagian file model")
                    }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } }
        )
    }
}

private fun formatMiB(bytes: Long): String = "%.1f MB".format(bytes / 1_000_000.0)
