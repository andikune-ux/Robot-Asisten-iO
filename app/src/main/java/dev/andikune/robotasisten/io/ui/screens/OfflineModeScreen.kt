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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.ui.theme.SuccessGreen
import dev.andikune.robotasisten.io.util.DeviceHelper
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineModeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val downloader = AppModule.provideModelDownloader(context)
    val settingsRepo = AppModule.provideSettingsRepository(context)

    val selectedModel by settingsRepo.selectedOfflineModel.collectAsState()
    val progressMap by downloader.downloadProgress.collectAsState()
    val scope = rememberCoroutineScope()

    val deviceInfo = DeviceHelper.getDeviceInfo(context)
    val freeStorage = deviceInfo["Available Storage"] ?: "32.4 GB"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🧠 Mode AI Offline (Model di HP)") },
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sisa Ruang Penyimpanan: $freeStorage",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Unduh model sekali dan gunakan selamanya tanpa koneksi internet. Model dieksekusi secara lokal di chipset ponsel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloader.availableModels, key = { it.id }) { model ->
                    val isDownloaded = downloader.isModelDownloaded(model)
                    val isCurrentActive = selectedModel == model.name
                    val downloadProgress = progressMap[model.id]

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ukuran: ${model.sizeText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isCurrentActive) {
                                    Text(
                                        text = "AKTIF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (downloadProgress != null && downloadProgress < 1.0f) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Mengunduh... ${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (!isDownloaded) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                ToastHelper.showShort(context, "Memulai unduhan model ${model.name}")
                                                downloader.startDownload(model) { success ->
                                                    if (success) {
                                                        ToastHelper.showShort(context, "✅ Model ${model.name} siap digunakan!")
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Unduh Model")
                                    }
                                } else {
                                    if (!isCurrentActive) {
                                        OutlinedButton(
                                            onClick = {
                                                settingsRepo.setSelectedOfflineModel(model.name)
                                                ToastHelper.showShort(context, "Model ${model.name} diaktifkan")
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Aktifkan Model")
                                        }
                                    } else {
                                        Button(
                                            onClick = {},
                                            enabled = false,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Sedang Digunakan")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
