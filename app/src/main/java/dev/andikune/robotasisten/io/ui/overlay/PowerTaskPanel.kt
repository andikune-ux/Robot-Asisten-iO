package dev.andikune.robotasisten.io.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PowerTaskPanel(
    prompt: String,
    result: String,
    isBusy: Boolean,
    status: String,
    autoReadContinuations: Boolean,
    onAutoReadContinuationsChange: (Boolean) -> Unit,
    onPromptChange: (String) -> Unit,
    onConfirmTask: () -> Unit,
    onAnalyzeAndAnswer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(320.dp).heightIn(max = 600.dp).shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⚡ RELX Power", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onClose) { Text("Tutup") }
            }
            Text("Apa yang harus saya lakukan?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Opsional — jelaskan tugas atau fokus analisa") },
                minLines = 2,
                maxLines = 4
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = autoReadContinuations, onCheckedChange = onAutoReadContinuationsChange)
                Column(Modifier.weight(1f)) {
                    Text("Baca sambungan otomatis", style = MaterialTheme.typography.labelLarge)
                    Text("RELX Power melanjutkan membaca sampai layar selesai atau tidak ada konten baru, lalu mencoba kembali ke posisi awal.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Ketuk Konfirmasi untuk membaca layar dan meminta rencana AI. Gulir otomatis hanya untuk membaca teks. AI tidak otomatis menekan tombol, mengirim pesan, Submit, menghapus, atau membayar; tindakan seperti itu harus ditinjau dan disetujui pengguna.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onConfirmTask, enabled = !isBusy) { Text("Konfirmasi & Analisa") }
                if (isBusy) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            if (result.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Analisa dan saran langkah", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
                    Text(result, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAnalyzeAndAnswer, modifier = Modifier.fillMaxWidth(), enabled = !isBusy) {
                Text("Analisa layar & tampilkan jawaban")
            }
        }
    }
}
