package dev.andikune.robotasisten.io.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.data.ai.RailxChatMode
import dev.andikune.robotasisten.io.data.ai.RelxChatMessage
import dev.andikune.robotasisten.io.data.ai.RelxChatProfile
import dev.andikune.robotasisten.io.data.ai.RelxChatStore
import dev.andikune.robotasisten.io.data.ai.RelxPendingChatRequest
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.service.RelxChatReplyHelper
import dev.andikune.robotasisten.io.util.AttachmentAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelxAiChatScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppModule.provideSettingsRepository(context) }
    val store = remember { RelxChatStore(context) }
    val activeMode by settings.activeMode.collectAsState()
    val replyWork by RelxChatReplyHelper.observe(context).collectAsState(initial = null)
    var conversation by remember { mutableStateOf(store.currentConversation() ?: store.newConversation()) }
    var messages by remember { mutableStateOf(conversation.messages) }
    var profile by remember { mutableStateOf(store.profile()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(store.pendingRequest() != null) }
    var showProfile by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showEditMessage by remember { mutableStateOf(false) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingDraft by remember { mutableStateOf("") }
    var historyRevision by remember { mutableIntStateOf(0) }
    var selectedAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var attachmentContext by remember { mutableStateOf("") }
    var attachmentStatus by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        selectedAttachments = uris
        attachmentContext = ""
        attachmentStatus = if (uris.isEmpty()) "" else "Menganalisa ${uris.size} lampiran secara offline…"
    }
    val modeLabel = when (activeMode.uppercase()) {
        "AI_OFFLINE" -> "AI Offline"
        "AI_OPEN_KEY" -> "AI API Key"
        else -> "Pilih AI Offline atau AI API Key di Beranda"
    }

    LaunchedEffect(replyWork?.id, replyWork?.state) {
        if (replyWork?.state?.isFinished == true) {
            store.currentConversation()?.let { current ->
                if (current.id == conversation.id) messages = current.messages
            }
            busy = false
        }
    }

    LaunchedEffect(selectedAttachments) {
        if (selectedAttachments.isEmpty()) return@LaunchedEffect
        val results = selectedAttachments.map { AttachmentAnalyzer.analyze(context, it) }
        attachmentContext = AttachmentAnalyzer.composeContext(results)
        val warnings = results.count { it.warning.isNotBlank() }
        attachmentStatus = if (warnings == 0) {
            "${results.size} lampiran siap dianalisa AI Offline"
        } else {
            "${results.size} lampiran siap; $warnings memiliki catatan extractor"
        }
    }

    fun newConversation() {
        conversation = store.newConversation()
        messages = emptyList()
    }

    fun requestReply(userText: String, history: List<RelxChatMessage>, pending: List<RelxChatMessage>, conversationId: String) {
        if (userText.isBlank() || busy) return
        val requestHistory = history.map { it.role to it.content }
        val requestMode = activeMode
        val requestProfile = profile
        val request = RelxPendingChatRequest(
            conversationId = conversationId,
            mode = requestMode,
            history = requestHistory,
            userMessage = userText,
            pendingMessages = pending,
            persona = requestProfile.persona,
            memory = requestProfile.memory,
            instructions = requestProfile.instructions,
            reasoningDepth = requestProfile.reasoningDepth,
            chatMode = requestProfile.chatMode
        )
        store.savePendingRequest(request)
        RelxChatReplyHelper.enqueue(context, request)
        busy = true
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty() || busy) return
        if (activeMode.uppercase() !in setOf("AI_OFFLINE", "AI_OPEN_KEY")) {
            Toast.makeText(context, "Pilih AI Offline atau AI API Key sebagai mode aktif di Beranda", Toast.LENGTH_LONG).show()
            return
        }
        val messageWithAttachments = buildString {
            append(text)
            if (attachmentContext.isNotBlank()) {
                append("\n\nAnalisa lampiran lokal:\n")
                append(attachmentContext)
            }
        }.take(MAX_USER_MESSAGE_CHARS)
        val before = messages
        val pending = before + RelxChatMessage(role = RelxChatStore.ROLE_USER, content = messageWithAttachments)
        messages = pending
        store.saveMessages(conversation.id, pending)
        draft = ""
        selectedAttachments = emptyList()
        attachmentContext = ""
        attachmentStatus = ""
        requestReply(messageWithAttachments, before, pending, conversation.id)
    }

    fun regenerateFrom(userMessageId: String) {
        if (busy) return
        val targetIndex = messages.indexOfFirst { it.id == userMessageId }
        if (targetIndex < 0) return
        val userIndex = if (messages[targetIndex].role == RelxChatStore.ROLE_USER) targetIndex
            else messages.take(targetIndex).indexOfLast { it.role == RelxChatStore.ROLE_USER }
        if (userIndex < 0) return
        val history = messages.take(userIndex)
        val pending = messages.take(userIndex + 1)
        messages = pending
        store.saveMessages(conversation.id, pending)
        requestReply(pending.last().content, history, pending, conversation.id)
    }

    fun saveEditedMessage() {
        val id = editingMessageId ?: return
        val index = messages.indexOfFirst { it.id == id && it.role == RelxChatStore.ROLE_USER }
        val text = editingDraft.trim()
        if (index < 0 || text.isBlank() || busy) return
        val history = messages.take(index)
        val pending = history + messages[index].copy(content = text)
        messages = pending
        store.saveMessages(conversation.id, pending)
        showEditMessage = false
        editingMessageId = null
        requestReply(text, history, pending, conversation.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("RELX AI", fontWeight = FontWeight.Bold)
                            val (badgeText, badgeBg) = when (profile.chatMode) {
                                RailxChatMode.FAST -> "FAST ⚡" to MaterialTheme.colorScheme.tertiaryContainer
                                RailxChatMode.SEDANG -> "SEDANG ⚖️" to MaterialTheme.colorScheme.primaryContainer
                                RailxChatMode.PRO -> "PRO 🧠" to MaterialTheme.colorScheme.secondaryContainer
                            }
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                        Text("Riwayat tersimpan lokal. Atur persona, knowledge, instruction, dan kedalaman jawaban melalui tombol pengaturan.", style = MaterialTheme.typography.bodySmall)
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
                        busy = busy,
                        onEdit = {
                            editingMessageId = message.id
                            editingDraft = message.content
                            showEditMessage = true
                        },
                        onRegenerate = { regenerateFrom(message.id) },
                        onCopySnippet = { copyText(context, it) }
                    )
                }
                if (busy) item(key = "thinking") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        Text("RELX AI sedang menyusun jawaban…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            LaunchedEffect(messages.size, busy) {
                runCatching {
                    val totalItems = messages.size + if (busy) 1 else 0
                    if (totalItems > 0) {
                        listState.animateScrollToItem(totalItems - 1)
                    }
                }
            }
            if (attachmentStatus.isNotBlank()) {
                Text(
                    attachmentStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
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
                IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Tambah foto, video, atau dokumen")
                }
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
                Toast.makeText(context, "Persona, memory, instruction, dan kedalaman tersimpan di perangkat", Toast.LENGTH_SHORT).show()
            }
        )
    }
    if (showEditMessage) {
        AlertDialog(
            onDismissRequest = { showEditMessage = false },
            title = { Text("Edit pesan") },
            text = {
                OutlinedTextField(
                    value = editingDraft,
                    onValueChange = { editingDraft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    minLines = 3,
                    maxLines = 12
                )
            },
            confirmButton = { Button(onClick = { saveEditedMessage() }, enabled = editingDraft.isNotBlank() && !busy) { Text("Simpan & kirim ulang") } },
            dismissButton = { TextButton(onClick = { showEditMessage = false }) { Text("Batal") } }
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
private fun ChatMessageBubble(
    message: RelxChatMessage,
    busy: Boolean,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onCopySnippet: (String) -> Unit
) {
    val isUser = message.role == RelxChatStore.ROLE_USER
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.9f else 1f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(if (isUser) "Anda" else "RELX AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (isUser) SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyMedium) }
                else AssistantMessageContent(message.content, onCopySnippet)
                if (isUser) TextButton(onClick = onEdit, enabled = !busy) { Text("Edit & kirim ulang") }
                else TextButton(onClick = onRegenerate, enabled = !busy) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Balas ulang")
                }
            }
        }
    }
}

private data class MessagePart(val code: Boolean, val text: String, val language: String = "")

@Composable
private fun AssistantMessageContent(content: String, onCopySnippet: (String) -> Unit) {
    val fencedCode = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
    val parts = buildList {
        var cursor = 0
        fencedCode.findAll(content).forEach { match ->
            if (match.range.first > cursor) add(MessagePart(false, content.substring(cursor, match.range.first).trim()))
            add(MessagePart(true, match.groupValues[2].trimEnd(), match.groupValues[1].trim()))
            cursor = match.range.last + 1
        }
        if (cursor < content.length) add(MessagePart(false, content.substring(cursor).trim()))
        if (isEmpty() && content.isNotBlank()) add(MessagePart(false, content))
    }.filter { it.text.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            if (!part.code) {
                SelectionContainer { Text(part.text, style = MaterialTheme.typography.bodyMedium) }
            } else {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(part.language.ifBlank { "Teks siap salin" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { onCopySnippet(part.text) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Text("Salin bagian ini")
                            }
                        }
                        SelectionContainer {
                            Text(part.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatProfileDialog(initial: RelxChatProfile, onDismiss: () -> Unit, onSave: (RelxChatProfile) -> Unit) {
    var persona by remember(initial) { mutableStateOf(initial.persona) }
    var memory by remember(initial) { mutableStateOf(initial.memory) }
    var instructions by remember(initial) { mutableStateOf(initial.instructions) }
    var reasoningDepth by remember(initial) { mutableStateOf(initial.reasoningDepth) }
    var chatMode by remember(initial) { mutableStateOf(initial.chatMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan RAILX AI Engine", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Mode Obrolan & Densitas Respons",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RailxChatMode.values().forEach { mode ->
                        val isSelected = chatMode == mode
                        val (label, icon) = when (mode) {
                            RailxChatMode.FAST -> "FAST" to "⚡"
                            RailxChatMode.SEDANG -> "SEDANG" to "⚖️"
                            RailxChatMode.PRO -> "PRO" to "🧠"
                        }
                        OutlinedButton(
                            onClick = { chatMode = mode },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = if (isSelected) androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(
                                text = "$icon $label",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val desc = when (chatMode) {
                        RailxChatMode.FAST -> "⚡ FAST: Respons kilat & to-the-point (max 250 token, temp 0.3). Cocok untuk tanya cepat, konfirmasi, atau sintaks ringkas (maks 2-3 kalimat)."
                        RailxChatMode.SEDANG -> "⚖️ SEDANG (Default): Respons standar & terstruktur (max 750 token, temp 0.65). Keseimbangan antara kelugasan dan kelengkapan berformat rapi."
                        RailxChatMode.PRO -> "🧠 PRO: Analisis mendalam & komprehensif (max 2048 token, temp 0.75). Bedah konsep rumit, arsitektur/coding, logika, contoh konkret, dan kesimpulan."
                    }
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    "Basis Memori & Pengetahuan Permanen",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = memory,
                    onValueChange = { memory = it },
                    label = { Text("Memory Knowledge") },
                    placeholder = { Text("Fakta penting pengguna, preferensi, atau konteks permanen yang selalu diingat AI...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Persona & Instruksi Khusus Pengguna",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instruction Response") },
                    placeholder = { Text("Aturan gaya bahasa, format khusus, batasan kalimat, atau persona khusus...") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Gaya Bahasa Tambahan (Opsional)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    label = { Text("Persona / Gaya") },
                    placeholder = { Text("cth: Santai, ramah, mentor senior...") },
                    minLines = 1,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Kedalaman Penalaran", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("brief" to "Ringkas", "balanced" to "Seimbang", "deep" to "Mendalam").forEach { (value, label) ->
                        OutlinedButton(onClick = { reasoningDepth = value }) {
                            Text(if (reasoningDepth == value) "✓ $label" else label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    RelxChatProfile(
                        persona = persona.trim(),
                        memory = memory.trim(),
                        instructions = instructions.trim(),
                        reasoningDepth = reasoningDepth,
                        chatMode = chatMode
                    )
                )
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

private fun copyText(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("RELX AI", text))
        Toast.makeText(context, "Bagian teks disalin", Toast.LENGTH_SHORT).show()
    }
}

private const val MAX_USER_MESSAGE_CHARS = 40_000
