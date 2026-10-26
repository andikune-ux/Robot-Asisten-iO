package dev.andikune.robotasisten.io.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.data.ai.AiProviderConfig
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudModeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val keyStore = remember(context) { runCatching { AppModule.provideApiKeyStore(context) }.getOrNull() }
    val settingsRepo = AppModule.provideSettingsRepository(context)
    val aiRepo = remember(context) { runCatching { AppModule.provideAiRepository(context) }.getOrNull() }

    val selectedProvider by settingsRepo.selectedProvider.collectAsState()
    val selectedModel by settingsRepo.selectedModel.collectAsState()
    val savedCustomBaseUrl by settingsRepo.customBaseUrl.collectAsState()

    var apiKeyInput by remember(selectedProvider, keyStore) { mutableStateOf(keyStore?.getApiKey(selectedProvider) ?: "") }
    var customBaseUrlInput by remember { mutableStateOf(savedCustomBaseUrl) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val resolvedProvider = runCatching { AiProviderConfig.getProvider(selectedProvider) }.getOrNull()
    val providerItem = resolvedProvider ?: AiProviderConfig.getProvider("OpenAI")
    val hasKey = keyStore?.hasApiKey(selectedProvider) == true
    val providerSupported = resolvedProvider != null && providerItem.supportsOpenAiChatCompletions
    val customBaseUrlValid = if (providerItem.isCustom) {
        val parsed = runCatching { Uri.parse(customBaseUrlInput.trim()) }.getOrNull()
        customBaseUrlInput.trim().startsWith("https://", ignoreCase = true) && !parsed?.host.isNullOrBlank()
    } else true
    val configurationReady = hasKey && providerSupported && selectedModel.isNotBlank() && customBaseUrlValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("☁️ Mode AI Open Key") },
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
                text = "Gunakan API Key Anda sendiri dari penyedia AI pilihan Anda. Kunci disimpan terenkripsi di hardware Keystore perangkat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (keyStore == null) {
                Text("Penyimpanan terenkripsi tidak tersedia di perangkat ini. API key tidak akan disimpan.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (aiRepo == null) {
                Text("Layanan AI tidak dapat diinisialisasi karena penyimpanan aman tidak tersedia.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (providerItem.isCustom) {
                OutlinedTextField(
                    value = customBaseUrlInput,
                    onValueChange = {
                        customBaseUrlInput = it
                        settingsRepo.setCustomBaseUrl(it)
                    },
                    label = { Text("Base URL HTTPS (Custom Provider)") },
                    placeholder = { Text("https://server-anda.example/v1/") },
                    supportingText = { Text("Endpoint OpenAI-compatible. API key hanya dikirim lewat HTTPS.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 1: Provider selection
            Text(
                text = "Langkah 1 — Pilih Provider",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = providerDropdownExpanded,
                onExpandedChange = { providerDropdownExpanded = !providerDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = resolvedProvider?.name ?: "Pilih provider yang didukung",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider AI") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = providerDropdownExpanded,
                    onDismissRequest = { providerDropdownExpanded = false }
                ) {
                    AiProviderConfig.supportedProviders.filter { it.supportsOpenAiChatCompletions }.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = {
                                settingsRepo.setSelectedProvider(item.id)
                                settingsRepo.setSelectedModel(item.defaultModel)
                                apiKeyInput = keyStore?.getApiKey(item.id) ?: ""
                                providerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!providerSupported) {
                Text(
                    if (resolvedProvider == null) "Provider tersimpan tidak dikenal. Pilih salah satu provider yang tersedia."
                    else "Provider ini belum didukung karena protokolnya tidak kompatibel. Pilih provider lain.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Step 2: API Key input
            Text(
                text = "Langkah 2 — Input & Validasi API Key",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key (${providerItem.name})") },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Sembunyikan" else "Tampilkan"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasKey) {
                    OutlinedButton(
                        onClick = {
                            keyStore?.removeApiKey(selectedProvider)
                            apiKeyInput = ""
                            ToastHelper.showShort(context, "API Key dihapus")
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus Kunci", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            isValidating = true
                            scope.launch {
                                try {
                                    val repository = aiRepo ?: error("Layanan AI tidak tersedia")
                                    val result = repository.validateCloudKey(selectedProvider, apiKeyInput)
                                    if (result.isSuccess) {
                                        keyStore?.saveApiKey(selectedProvider, apiKeyInput)
                                            ?: error("Penyimpanan API key aman tidak tersedia")
                                        ToastHelper.showShort(context, "API Key valid dan tersimpan")
                                    } else {
                                        ToastHelper.showLong(context, result.exceptionOrNull()?.message ?: "Validasi gagal")
                                    }
                                } catch (error: Exception) {
                                    ToastHelper.showLong(context, "Gagal memvalidasi/menyimpan key: ${error.message ?: "coba lagi"}")
                                } finally {
                                    isValidating = false
                                }
                            }
                        }
                    },
                    enabled = keyStore != null && aiRepo != null && providerSupported && customBaseUrlValid && apiKeyInput.isNotBlank() && !isValidating,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Validasi & Simpan")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 3: Model selection
            Text(
                text = "Langkah 3 — Pilih Model AI",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (providerItem.isCustom) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { settingsRepo.setSelectedModel(it) },
                    label = { Text("Model ID") },
                    placeholder = { Text("contoh: qwen2.5:7b") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pilihan Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        providerItem.models.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(m.displayName, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${m.description} • Context: ${m.contextLength}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    settingsRepo.setSelectedModel(m.id)
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 4: Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (configurationReady) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ringkasan Konfigurasi AI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Provider: ${resolvedProvider?.name ?: "Tidak dikenal ($selectedProvider)"}")
                    Text("• Model: $selectedModel")
                    Text("• Status: ${if (configurationReady) "Siap digunakan" else "Belum siap — periksa provider, model, endpoint, dan API key"}")
                }
            }
        }
    }
}
