package dev.andikune.robotasisten.io.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
    modifier: Modifier = Modifier
) {
    val hasExamWarning = SanitizerHelper.containsExamKeyword(detectedQuestion.questionText)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .width(300.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .testTag("analysis_preview_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔍 Hasil Analisa Layar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = detectedQuestion.type.name.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = detectedQuestion.questionText.take(180) + if (detectedQuestion.questionText.length > 180) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (detectedQuestion.options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Opsi terdeteksi (${detectedQuestion.options.size}): " +
                            detectedQuestion.options.take(3).joinToString("; "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasExamWarning) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Terdeteksi kata ujian/ulangan. Gunakan beretika.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("Batal", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onReanalyze,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("Ulang", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onAnswerNow,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("⚡ Jawab", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
