package dev.andikune.robotasisten.io.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.ui.theme.CyanAccent
import dev.andikune.robotasisten.io.ui.theme.ErrorRed
import dev.andikune.robotasisten.io.ui.theme.SuccessGreen
import dev.andikune.robotasisten.io.ui.theme.TechBluePrimary
import dev.andikune.robotasisten.io.ui.theme.WarningAmber

enum class AutomationTool(val displayName: String, val iconDescription: String) {
    PREPARE_ANSWER("Siapkan Jawaban", "🧠"),
    ANALYZE_SCREEN("Analisis Layar", "🔍"),
    RAILX_POWER("RAILX Power", "⚡"),
    OCR_READ("Baca Teks Layar OCR", "📸")
}

enum class RuntimeStep(val stepNumber: Int, val title: String) {
    CONFIG(0, "Siap Mulai"),
    SCANNING(1, "Sedang memindai tampilan layar..."),
    QUESTIONS_DETECTED(2, "Menemukan pertanyaan"),
    OCR_EXTRACTING(3, "Membaca & mengekstrak teks pertanyaan (OCR)"),
    ANALYZING_ANSWER(4, "Menganalisis pertanyaan & mencari jawaban terbaik..."),
    FILLING_ANSWER(5, "Mengisi / memilih jawaban otomatis"),
    ANSWER_SUCCESS(6, "Jawaban berhasil diberikan!"),
    AUTO_SCROLLING(7, "Menggulir layar (auto-scroll) / berpindah ke pertanyaan berikutnya..."),
    COMPLETED(8, "Seluruh tugas telah selesai diproses.")
}

@Composable
fun AutomationRuntimePanel(
    tool: AutomationTool,
    currentStep: RuntimeStep,
    userPrompt: String,
    onPromptChange: (String) -> Unit,
    autoScrollEnabled: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    totalQuestions: Int,
    currentQuestionIndex: Int,
    currentQuestionText: String,
    currentAnswerText: String,
    isPaused: Boolean,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onBackToTools: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = modifier
            .width(320.dp)
            .heightIn(max = 620.dp)
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .testTag("automation_runtime_panel")
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Bar: Tool Icon + Title + Minimize & Close actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.iconDescription,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Column {
                        Text(
                            text = tool.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (currentStep != RuntimeStep.CONFIG) {
                            Text(
                                text = if (isPaused) "⏸ DIJEDA" else "● BERJALAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (isPaused) WarningAmber else SuccessGreen
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Minimize",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // PHASE 1: CONFIGURATION & OPTIONAL COMMAND
            // ==========================================
            if (currentStep == RuntimeStep.CONFIG) {
                Text(
                    text = "Perintah / Catatan AI (Opsional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Misal: 'Pilih opsi yang paling tepat', 'Fokus pada soal matematika'...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-scroll berikutnya",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Lanjut membaca saat halaman selesai",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoScrollEnabled,
                        onCheckedChange = onAutoScrollChange
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SINGLE Prominent Execution Button: "Mulai"
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechBluePrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🚀 Mulai",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onBackToTools,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kembali ke Tools Awal")
                }

            } else {
                // ==========================================
                // PHASE 2: LIVE RUNTIME AUTOMATION EXECUTION
                // ==========================================

                // Progress Bar or Questions Indicator
                if (totalQuestions > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progres Soal:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pertanyaan $currentQuestionIndex dari $totalQuestions",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TechBluePrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (totalQuestions > 0) (currentQuestionIndex.toFloat() / totalQuestions).coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = TechBluePrimary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Sequential Structured Status Card
                val statusBgColor by animateColorAsState(
                    targetValue = when (currentStep) {
                        RuntimeStep.COMPLETED -> SuccessGreen.copy(alpha = 0.12f)
                        RuntimeStep.ANSWER_SUCCESS -> CyanAccent.copy(alpha = 0.12f)
                        else -> TechBluePrimary.copy(alpha = 0.08f)
                    },
                    label = "status_bg"
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = statusBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentStep == RuntimeStep.COMPLETED) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else if (isPaused) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = TechBluePrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            val dynamicStatusTitle = when (currentStep) {
                                RuntimeStep.SCANNING -> "1. Sedang memindai tampilan layar..."
                                RuntimeStep.QUESTIONS_DETECTED -> "2. Menemukan pertanyaan [Terdeteksi $totalQuestions Pertanyaan]"
                                RuntimeStep.OCR_EXTRACTING -> "3. Membaca & mengekstrak teks pertanyaan (OCR)"
                                RuntimeStep.ANALYZING_ANSWER -> "4. Menganalisis pertanyaan & mencari jawaban terbaik..."
                                RuntimeStep.FILLING_ANSWER -> "5. Mengisi / memilih jawaban otomatis pada pertanyaan [Ke-$currentQuestionIndex]"
                                RuntimeStep.ANSWER_SUCCESS -> "6. Jawaban berhasil diberikan!"
                                RuntimeStep.AUTO_SCROLLING -> "7. Menggulir layar (auto-scroll) / berpindah ke pertanyaan berikutnya..."
                                RuntimeStep.COMPLETED -> "Seluruh tugas telah selesai diproses."
                                else -> currentStep.title
                            }

                            Text(
                                text = dynamicStatusTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Preview snippet of current question
                        if (currentQuestionText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Soal:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentQuestionText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Preview snippet of current answer
                        if (currentAnswerText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Jawaban:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                            Text(
                                text = currentAnswerText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // If Paused banner
                if (isPaused) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Automasi dijeda. Tekan tombol Play untuk melanjutkan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }

                // If Completed banner
                if (currentStep == RuntimeStep.COMPLETED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SuccessGreen.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎉", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Semua pertanyaan pada sesi ini selesai diproses.",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Controls Bar: [Play / Jeda], [Stop], [Kembali], [Minimize]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Jeda Toggle Button
                    Button(
                        onClick = onTogglePause,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) SuccessGreen else WarningAmber,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Play" else "Jeda",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPaused) "Play" else "Jeda",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Stop Button
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Stop",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Kembali ke Tools Awal
                    OutlinedButton(
                        onClick = onBackToTools,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Menu",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Minimize Button
                    OutlinedButton(
                        onClick = onMinimize,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Minimize",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Minimize",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
