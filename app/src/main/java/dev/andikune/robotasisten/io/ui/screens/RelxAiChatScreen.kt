package dev.andikune.robotasisten.io.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.data.ai.RelxChatMessage
import dev.andikune.robotasisten.io.data.ai.RelxChatProfile
import dev.andikune.robotasisten.io.data.ai.RelxChatStore
import dev.andikune.robotasisten.io.di.AppModule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelxAiChatScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppModule.provideSettingsRepository(context) }
    val aiRepository = remember { AppModule.provideAiRepository(context) }
    val store = remember { RelxChatStore(context) }
    val activeMode by settings.activeMode.collectAsState()
    val scope = rememberCoroutineScope()
    var conversation by remember { mutableStateOf(store.currentConversation() ?: store.newConversation()) }
    var messages by remember { mutableStateOf(conversation.messages) }
    var profile by remember { mutableStateOf(store.profile()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var historyRevision by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val modeLabel = when (activeMode.uppercase()) {
        "AI_OFFLINE" -> "AI Offline"
        "AI_OPEN_KEY" -> "AI API Key"
        else -> "Pilih AI Offline atau AI API Key di Beranda"
    }

    fun newConversation() {
        conversation = store.newConversation()
        messages = emptyList()
    }

    fun sendMessage() {
        val userText = draft.trim()
        if (userText.isEmpty() || busy) return
        if (activeMode.uppercase() !in setOf("AI_OFFLINE", "AI_OPEN_KEY")) {
            Toast.makeText(context, "Pilih AI Offline atau AI API Key sebagai mode aktif di Beranda", Toast.LENGTH_LONG).show()
            return
        }
        val beforeSend = messages
        val requestConversationId = conversation.id
        val requestMode = activeMode
        val requestProfile = profile
        val userMessage = RelxChatMessage(role = RelxChatStore.ROLE_USER, content = userText)
        val pendingMessages = beforeSend + userMessage
        messages = pendingMessages
        store.saveMessages(requestConversationId, pendingMessages)
        draft = ""
        busy = true
        scope.launch {
            val reply = runCatching {
                aiRepository.chatReply(
                    mode = requestMode,
                    history = beforeSend.map { it.role to it.content },
                    userMessage = userText,
                    persona = requestProfile.persona,
                    memory = requestProfile.memory,
                    instructions = requestProfile.instructions
                )
            }.getOrNull()
            val assistantMessage = RelxChatMessage(
                role = RelxChatStore.ROLE_ASSISTANT,
                content = reply ?: "RELX AI belum menerima jawaban. Periksa model offline, koneksi, API key, provider, dan model aktif; lalu coba lagi."
            )
            val completedMessages = pendingMessages + assistantMessage
            store.saveMessages(requestConversationId, completedMessages)
            if (conversation.id == requestConversationId) messages = completedMessages
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RELX AI", fontWeight = FontWeight.Bold)
                        Text(modeLabel, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }, enabled = !busy) {
                        Icon(Icons.Default.History, contentDescription = "Riwayat chat")
                    }
                    IconButton(onClick = { newConversation() }, enabled = !busy) {
                        Icon(Icons.Default.Add, contentDescription = "Percakapan baru")
                    }
                    IconButton(onClick = { profile = store.profile(); showProfile = true }, enabled = !busy) {
                        Icon(Icons.Default.Tune, contentDescription = "Persona dan memory")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (activeMode.uppercase() !in setOf("AI_OFFLINE", "AI_OPEN_KEY")) {
                Text(
                    "Chat memakai mode AI aktif. Pilih AI Offline atau AI API Key di Beranda; konfigurasi API key ada di Pengaturan Mode Cloud.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
            if (messages.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tanyakan apa saja", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Percakapan tersimpan lokal di perangkat. Gunakan tombol pengaturan untuk menyetel persona, knowledge/memory, dan instruction.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        message = message,
                        onCopy = { copyText(context, message.content) }
                    )
                }
                if (busy) item(key = "thinking") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        Text("RELX AI sedang berpikir…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            LaunchedEffect(messages.size, busy) {
                val lastIndex = messages.lastIndex + if (busy) 1 else 0
                if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ketik pesan…") },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() })
                )
                IconButton(onClick = { sendMessage() }, enabled = draft.isNotBlank() && !busy) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim pesan", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showProfile) {
        ChatProfileDialog(
            initial = profile,
            onDismiss = { showProfile = false },
            onSave = {
                profile = it
                store.saveProfile(it)
                showProfile = false
                Toast.makeText(context, "Persona, memory, dan instruction tersimpan di perangkat", Toast.LENGTH_SHORT).show()
            }
        )
    }
    if (showHistory) {
        val conversations = remember(showHistory, historyRevision) { store.conversations() }
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Riwayat RELX AI") },
            text = {
                if (conversations.isEmpty()) Text("Belum ada percakapan.")
                else LazyColumn {
                    items(conversations, key = { it.id }) { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    store.selectConversation(item.id)?.let { selected ->
                                        conversation = selected
                                        messages = selected.messages
                                    }
                                    showHistory = false
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(item.title, maxLines = 2) }
                            IconButton(onClick = {
                                store.deleteConversation(item.id)
                                if (conversation.id == item.id) newConversation()
                                historyRevision++
                            }) { Icon(Icons.Default.Delete, contentDescription = "Hapus percakapan") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun ChatMessageBubble(message: RelxChatMessage, onCopy: () -> Unit) {
    val isUser = message.role == RelxChatStore.ROLE_USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.9f else 1f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(if (isUser) "Anda" else "RELX AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
                if (!isUser) {
                    TextButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text("Salin")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatProfileDialog(
    initial: RelxChatProfile,
    onDismiss: () -> Unit,
    onSave: (RelxChatProfile) -> Unit
) {
    var persona by remember(initial) { mutableStateOf(initial.persona) }
    var memory by remember(initial) { mutableStateOf(initial.memory) }
    var instructions by remember(initial) { mutableStateOf(initial.instructions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Persona, Memory & Instruction") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Text("Riwayat chat dan konteks tersimpan di penyimpanan privat aplikasi. Konteks ini disertakan pada permintaan ke provider AI aktif; jangan masukkan password, API key, atau informasi sensitif.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(persona, { persona = it }, label = { Text("Persona / gaya") }, minLines = 2, maxLines = 3)
                OutlinedTextField(memory, { memory = it }, label = { Text("Memory / knowledge") }, minLines = 3, maxLines = 5)
                OutlinedTextField(instructions, { instructions = it }, label = { Text("Instruction / preferensi") }, minLines = 2, maxLines = 4)
            }
        },
        confirmButton = { Button(onClick = { onSave(RelxChatProfile(persona.trim(), memory.trim(), instructions.trim())) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

private fun copyText(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("RELX AI", text))
        Toast.makeText(context, "Teks disalin", Toast.LENGTH_SHORT).show()
    }
}
