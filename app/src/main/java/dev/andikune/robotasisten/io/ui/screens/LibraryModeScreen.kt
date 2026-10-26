package dev.andikune.robotasisten.io.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.data.db.RuleEntity
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.ui.components.EmptyState
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryModeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val ruleRepo = AppModule.provideRuleRepository(context)
    val rules by ruleRepo.allRules.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var patternInput by remember { mutableStateOf("") }
    var answerInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("Umum") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📚 Mode Library (Aturan Lokal)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Quick CSV / JSON template import
                        scope.launch {
                            val dummyCsvRules = listOf(
                                RuleEntity(questionPattern = "hukum newton 1", answer = "Hukum Kelembaman (Inersia): Benda diam tetap diam atau bergerak lurus beraturan jika resultan gaya = 0.", category = "Fisika"),
                                RuleEntity(questionPattern = "rumus pythagoras", answer = "a² + b² = c²", category = "Matematika"),
                                RuleEntity(questionPattern = "penemu telepon", answer = "Alexander Graham Bell", category = "Sejarah")
                            )
                            ruleRepo.insertAll(dummyCsvRules)
                            ToastHelper.showShort(context, "✅ Berhasil import 3 aturan baru dari CSV")
                        }
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_rule_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Aturan")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Tabel aturan lokal berjalan 100% offline tanpa kuota AI. Robot akan langsung mencocokkan kata kunci soal ke tabel ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (rules.isEmpty()) {
                EmptyState(
                    title = "Belum Ada Aturan",
                    message = "Tambahkan aturan baru atau ketuk ikon import untuk memuat data awal.",
                    icon = Icons.Default.Book
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.questionPattern,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Jawaban: ${rule.answer}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (rule.explanation.isNotBlank()) {
                                        Text(
                                            text = "Penjelasan: ${rule.explanation}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    scope.launch {
                                        ruleRepo.deleteRule(rule)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Aturan",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Tambah Aturan Baru") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = patternInput,
                            onValueChange = { patternInput = it },
                            label = { Text("Pola / Kata Kunci Pertanyaan") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answerInput,
                            onValueChange = { answerInput = it },
                            label = { Text("Jawaban") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = categoryInput,
                            onValueChange = { categoryInput = it },
                            label = { Text("Kategori") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (patternInput.isNotBlank() && answerInput.isNotBlank()) {
                            scope.launch {
                                ruleRepo.insertRule(
                                    RuleEntity(
                                        questionPattern = patternInput.trim(),
                                        answer = answerInput.trim(),
                                        category = categoryInput.trim()
                                    )
                                )
                                patternInput = ""
                                answerInput = ""
                                showAddDialog = false
                                ToastHelper.showShort(context, "Aturan berhasil disimpan")
                            }
                        }
                    }) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showAddDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}
