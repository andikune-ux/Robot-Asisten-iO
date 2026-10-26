package dev.andikune.robotasisten.io

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.andikune.robotasisten.io.navigation.AppNavigation
import dev.andikune.robotasisten.io.ui.theme.RAiOTheme
import dev.andikune.robotasisten.io.util.Constants
import dev.andikune.robotasisten.io.util.CrashHandler

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)
    private var pendingCrashReport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent?.getStringExtra(Constants.EXTRA_NAVIGATION_ROUTE)
        pendingCrashReport = CrashHandler.read(this)
        setContent {
            val context = LocalContext.current
            RAiOTheme {
                AppNavigation(
                    initialRoute = pendingRoute,
                    onInitialRouteHandled = { pendingRoute = null }
                )
                pendingCrashReport?.let { report ->
                    AlertDialog(
                        onDismissRequest = { pendingCrashReport = null },
                        title = { Text("Aplikasi sebelumnya berhenti mendadak") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text("Laporan force close tersimpan di perangkat. Salin dan kirimkan teks ini untuk membantu mencari penyebabnya.")
                                Text(report, modifier = Modifier.padding(top = 12.dp))
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                runCatching {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("RELX.iO crash report", report))
                                }.onSuccess {
                                    Toast.makeText(context, "Laporan crash disalin", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Tidak dapat menyalin laporan", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Salin Cepat") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { pendingCrashReport = null }) { Text("Kembali") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(Constants.EXTRA_NAVIGATION_ROUTE)
    }
}
