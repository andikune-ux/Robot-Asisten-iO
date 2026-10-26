package dev.andikune.robotasisten.io.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.util.CrashHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashReportScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashHandler.read(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan Force Close") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Laporan terakhir disimpan secara lokal. Teks ini berisi stack trace dan info perangkat; tinjau sebelum membagikannya.",
                style = MaterialTheme.typography.bodySmall
            )
            if (report.isNullOrBlank()) {
                Text("Belum ada force close yang tertangkap sejak fitur laporan crash aktif.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(report.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        runCatching {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("RELX.iO crash report", report))
                        }.onSuccess { Toast.makeText(context, "Laporan crash disalin", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Gagal menyalin laporan", Toast.LENGTH_SHORT).show() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text("  Salin Cepat")
                }
                OutlinedButton(
                    onClick = {
                        CrashHandler.clear(context)
                        report = null
                        Toast.makeText(context, "Laporan crash dihapus", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("  Hapus laporan")
                }
            }
        }
    }
}
