package dev.andikune.robotasisten.io.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.logic.DetectedQuestion
import dev.andikune.robotasisten.io.ui.theme.WarningAmber
import dev.andikune.robotasisten.io.util.SanitizerHelper

@Composable
fun AnalysisPreviewCard(
    detectedQuestion: DetectedQuestion,
    onAnswerNow: () -> Unit,
    onReanalyze: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isAnswering: Boolean = false
) {
    val hasExamWarning = SanitizerHelper.containsExamKeyword(detectedQuestion.questionText)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.width(300.dp).heightIn(max = 520.dp).shadow(8.dp, RoundedCornerShape(18.dp)).testTag("analysis_preview_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🔍 Hasil Analisa Layar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(detectedQuestion.type.name.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Soal terdeteksi", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(detectedQuestion.questionText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                if (detectedQuestion.options.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Opsi jawaban:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    detectedQuestion.options.forEachIndexed { index, option ->
                        Text("${'A' + index}. $option", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                if (hasExamWarning) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Terdeteksi kata ujian/ulangan. Gunakan beretika.", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                    }
                }
            }
            if (isAnswering) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("RELX AI sedang menyiapkan jawaban…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 5.dp)) { Text("Tutup", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = onReanalyze, modifier = Modifier.padding(end = 5.dp)) { Text("Ulang", style = MaterialTheme.typography.labelSmall) }
                Button(onClick = onAnswerNow, enabled = !isAnswering) { Text(if (isAnswering) "Proses…" else "⚡ Jawab", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
