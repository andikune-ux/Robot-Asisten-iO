package dev.andikune.robotasisten.io.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.util.SafeProjectBackup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeBackupScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val usesScopedStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val preferences = remember { context.getSharedPreferences("safe_project_backup", 0) }
    var selectedTree by remember {
        mutableStateOf(preferences.getString("tree_uri", null)?.let(Uri::parse))
    }
    var status by remember {
        mutableStateOf(
            if (usesScopedStorage) "Siap mengekspor langsung ke Penyimpanan internal/Relxio/Backup aman."
            else "Pilih Penyimpanan internal pada pemilih folder. Aplikasi akan membuat Relxio/Backup aman di lokasi tersebut."
        )
    }
    var exporting by remember { mutableStateOf(false) }

    fun showSuccess(output: Uri) {
        status = "Backup berhasil dibuat: ${output.lastPathSegment?.substringAfterLast('/') ?: "file .txt"} di Penyimpanan internal/Relxio/Backup aman."
    }

    fun exportDirectly() {
        exporting = true
        runCatching { SafeProjectBackup.export(context) }
            .onSuccess(::showSuccess)
            .onFailure { error ->
                status = "Gagal membuat backup: ${error.message ?: "penyimpanan tidak dapat diakses"}."
            }
        exporting = false
    }

    fun exportToTree(uri: Uri) {
        exporting = true
        runCatching { SafeProjectBackup.export(context, uri) }
            .onSuccess(::showSuccess)
            .onFailure { error ->
                status = "Gagal membuat backup: ${error.message ?: "penyimpanan tidak dapat diakses"}. Pilih ulang folder dan izinkan akses."
            }
        exporting = false
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                val readWriteFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, readWriteFlags)
                preferences.edit().putString("tree_uri", uri.toString()).apply()
                selectedTree = uri
                exportToTree(uri)
            }.onFailure { error ->
                selectedTree = uri
                status = "Folder dipilih, tetapi izinnya tidak dapat disimpan: ${error.message ?: "akses ditolak"}. Coba pilih ulang folder."
            }
        } else {
            status = "Pemilihan folder dibatalkan."
        }
    }

    fun startExport() {
        if (usesScopedStorage) exportDirectly() else folderPicker.launch(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup Aman") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Ekspor panduan proyek RELX.iO", style = MaterialTheme.typography.titleLarge)
            Text(
                "Fitur ini membuat satu berkas teks .TXT berisi identitas aplikasi, cara kerja fitur, peta arsitektur, inventaris folder/file source, izin Android, dan petunjuk melanjutkan proyek dengan AI assistant lain. Ini bukan backup atau restore database/data aplikasi.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Lokasi: Penyimpanan internal/Relxio/Backup aman/RELX-iO_BackupAman_[waktu].txt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (usesScopedStorage) {
                    "Izin penyimpanan: Android 10+ menggunakan MediaStore scoped untuk membuat file milik aplikasi pada folder di atas. Tidak perlu meminta akses seluruh penyimpanan atau permission sensitif."
                } else {
                    "Izin penyimpanan: Android akan meminta Anda memilih folder. Persetujuan SAF memberi akses baca/tulis hanya ke folder pilihan dan disimpan untuk ekspor berikutnya; tidak diperlukan izin akses seluruh penyimpanan."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { startExport() },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Text(if (usesScopedStorage) "  Buat Backup Aman" else "  Pilih folder & buat Backup Aman")
            }
            OutlinedButton(
                onClick = {
                    if (usesScopedStorage) exportDirectly()
                    else selectedTree?.let(::exportToTree) ?: folderPicker.launch(null)
                },
                enabled = !exporting && (usesScopedStorage || selectedTree != null),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (exporting) "Sedang membuat…" else "Buat backup lagi di lokasi tersimpan")
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
