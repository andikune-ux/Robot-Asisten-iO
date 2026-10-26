package dev.andikune.robotasisten.io.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.logic.ResolutionResult

@Composable
fun AnswerPreviewCard(
    result: ResolutionResult,
    questionSummary: String,
    allowFill: Boolean = true,
    onFillAnswer: () -> Unit,
    onEditAnswer: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(result.answer) { mutableStateOf(false) }
    var draft by remember(result.answer) { mutableStateOf(result.answer) }
    val alreadyFilled = result.source == "Sudah diisi"
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.width(300.dp).heightIn(max = 520.dp).shadow(8.dp, RoundedCornerShape(18.dp)).testTag("answer_preview_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (allowFill) "Jawaban Siap" else "Jawaban Belum Tersedia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(result.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState())) {
                Text("Soal: $questionSummary", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (editing) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Edit jawaban") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8)
                } else {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Column {
                            Text("Jawaban:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(result.answer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                        }
                    }
                }
                if (result.explanation.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Info: ${result.explanation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 3.dp)) { Text("Tutup", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = {
                    if (editing) draft = result.answer
                    editing = !editing
                }) { Text(if (editing) "Batal edit" else "Edit") }
                Button(
                    onClick = {
                        if (editing) {
                            val value = draft.trim()
                            if (value.isNotEmpty()) { onEditAnswer(value); editing = false }
                        } else onFillAnswer()
                    },
                    enabled = if (editing) draft.isNotBlank() else allowFill && !alreadyFilled
                ) { Text(if (editing) "Simpan" else if (alreadyFilled) "Sudah diisi" else if (allowFill) "Isi kolom" else "Tidak tersedia", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
