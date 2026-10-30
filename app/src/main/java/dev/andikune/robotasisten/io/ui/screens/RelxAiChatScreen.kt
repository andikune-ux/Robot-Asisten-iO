package dev.andikune.robotasisten.io.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.data.ai.RailxChatMode
import dev.andikune.robotasisten.io.data.ai.RelxChatMessage
import dev.andikune.robotasisten.io.data.ai.RelxChatProfile
import dev.andikune.robotasisten.io.data.ai.RelxChatStore
import dev.andikune.robotasisten.io.data.ai.RelxPendingChatRequest
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.service.RelxChatReplyHelper
import dev.andikune.robotasisten.io.ui.chat.components.AuroraBackground
import dev.andikune.robotasisten.io.ui.chat.components.BottomInputWithCurtainMode
import dev.andikune.robotasisten.io.ui.chat.components.ModernChatBubble
import dev.andikune.robotasisten.io.ui.chat.components.ThinkingPulseIndicator
import dev.andikune.robotasisten.io.util.AttachmentAnalyzer
import dev.andikune.robotasisten.io.util.NetworkHelper

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

    // Quoted message state for Swipe to Reply
    var replyingToMessage by remember { mutableStateOf<RelxChatMessage?>(null) }

    // TopBar mode dropdown menu visibility
    var showModeDropdown by remember { mutableStateOf(false) }

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

    val isNetworkConnected = remember { NetworkHelper.isConnected(context) }
    val modeSubLabel = when (activeMode.uppercase()) {
        "AI_OFFLINE" -> "AI Offline"
        "AI_OPEN_KEY" -> "AI Cloud Key"
        else -> "Mode AI"
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
        replyingToMessage = null
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

        // Incorporate quote if replying to a specific message
        val finalContent = buildString {
            replyingToMessage?.let { reply ->
                val quoteSnippet = reply.content.lines().firstOrNull()?.take(80) ?: ""
                append("> $quoteSnippet\n\n")
            }
            append(text)
            if (attachmentContext.isNotBlank()) {
                append("\n\nAnalisa lampiran lokal:\n")
                append(attachmentContext)
            }
        }.take(MAX_USER_MESSAGE_CHARS)

        val before = messages
        val pending = before + RelxChatMessage(role = RelxChatStore.ROLE_USER, content = finalContent)
        messages = pending
        store.saveMessages(conversation.id, pending)

        draft = ""
        replyingToMessage = null
        selectedAttachments = emptyList()
        attachmentContext = ""
        attachmentStatus = ""

        requestReply(finalContent, before, pending, conversation.id)
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

    // ==========================================
    // AURORA MESH BACKGROUND LAYER (Z-INDEX 0)
    // ==========================================
    AuroraBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A).copy(alpha = 0.88f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "RAILX AI",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )

                                // Dropdown Pemilihan Mode AI (Fast / Sedang / Pro) di Atas
                                Box {
                                    val (modeLabel, modeColor) = when (profile.chatMode) {
                                        RailxChatMode.FAST -> "Fast ⚡" to Color(0xFFFFB74D)
                                        RailxChatMode.SEDANG -> "Sedang ⚖️" to Color(0xFF64B5F6)
                                        RailxChatMode.PRO -> "Pro 🧠" to Color(0xFFBA68C8)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = modeColor.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showModeDropdown = !showModeDropdown }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = modeLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = modeColor
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Pilih Mode",
                                                tint = modeColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showModeDropdown,
                                        onDismissRequest = { showModeDropdown = false },
                                        modifier = Modifier.background(Color(0xFF0F172A))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("⚡ Fast", fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
                                                    Text("Instan, ringkas, to-the-point", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                                }
                                            },
                                            onClick = {
                                                profile = profile.copy(chatMode = RailxChatMode.FAST)
                                                store.saveProfile(profile)
                                                showModeDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("⚖️ Sedang", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                                                    Text("Rapi, seimbang, dan terstruktur", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                                }
                                            },
                                            onClick = {
                                                profile = profile.copy(chatMode = RailxChatMode.SEDANG)
                                                store.saveProfile(profile)
                                                showModeDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("🧠 Pro", fontWeight = FontWeight.Bold, color = Color(0xFFBA68C8))
                                                    Text("Mendalam, komprehensif, arsitektur", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                                }
                                            },
                                            onClick = {
                                                profile = profile.copy(chatMode = RailxChatMode.PRO)
                                                store.saveProfile(profile)
                                                showModeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Subtitle: Titik Indikator Status Koneksi Pulsing Halus
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PulsingConnectionDot(isOnline = isNetworkConnected || activeMode.equals("AI_OFFLINE", ignoreCase = true))
                                Text(
                                    text = if (isNetworkConnected || activeMode.equals("AI_OFFLINE", ignoreCase = true)) "Online • $modeSubLabel" else "Offline • $modeSubLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
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
                        // Ikon Gerigi Standar (Settings Gear)
                        IconButton(onClick = { profile = store.profile(); showProfile = true }, enabled = !busy) {
                            Icon(Icons.Default.Settings, contentDescription = "Pengaturan AI Engine & Memory")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (activeMode.uppercase() !in setOf("AI_OFFLINE", "AI_OPEN_KEY")) {
                    Text(
                        "Chat memakai mode AI aktif. Pilih AI Offline atau AI API Key di Beranda.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                if (messages.isEmpty()) {
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.65f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Mulai Diskusi dengan RAILX AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF56F0D0)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tekan lama pada pesan untuk menu aksi (Salin, Balas, Edit, Regenerate, Hapus). Geser pesan ke kanan untuk membalas/mengutip.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // ==========================================
                // DAFTAR PESAN DENGAN ANIMASI ENTRANCE
                // ==========================================
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                                initialOffsetY = { 40 },
                                animationSpec = tween(300)
                            )
                        ) {
                            ModernChatBubble(
                                message = message,
                                busy = busy,
                                onSwipeToReply = { replyMsg ->
                                    replyingToMessage = replyMsg
                                },
                                onCopy = { copyText(context, it) },
                                onEdit = if (message.role == RelxChatStore.ROLE_USER) {
                                    {
                                        editingMessageId = message.id
                                        editingDraft = message.content
                                        showEditMessage = true
                                    }
                                } else null,
                                onRegenerate = if (message.role != RelxChatStore.ROLE_USER) {
                                    { regenerateFrom(message.id) }
                                } else null,
                                onDelete = {
                                    messages = store.deleteSingleMessage(conversation.id, message.id)
                                }
                            )
                        }
                    }

                    // INDIKATOR LOADING DINAMIS (WAVE/PULSING DOTS SKELETON)
                    if (busy) {
                        item(key = "thinking_indicator") {
                            ThinkingPulseIndicator(
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                text = "RAILX AI sedang merespons…"
                            )
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
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                // ==========================================
                // LAYOUT BOTTOM BAR (SPEEDOMETER DI ATAS DOKUMEN)
                // ==========================================
                BottomInputWithCurtainMode(
                    currentMode = profile.chatMode,
                    isGenerating = busy,
                    inputText = draft,
                    attachments = selectedAttachments,
                    replyToMessage = replyingToMessage,
                    onCancelReply = { replyingToMessage = null },
                    onModeChanged = { newMode ->
                        profile = profile.copy(chatMode = newMode)
                        store.saveProfile(profile)
                    },
                    onSpeedometerClick = {
                        // Quick toggle or opens mode dropdown
                        showModeDropdown = true
                    },
                    onTextChange = { draft = it },
                    onSendClick = { sendMessage() },
                    onStopClick = {
                        RelxChatReplyHelper.cancel(context)
                        store.clearPendingRequest()
                        busy = false
                        Toast.makeText(context, "Respon dihentikan oleh pengguna", Toast.LENGTH_SHORT).show()
                    },
                    onUploadClick = { attachmentPicker.launch(arrayOf("*/*")) },
                    onRemoveAttachment = { uri ->
                        selectedAttachments = selectedAttachments.filterNot { it == uri }
                    }
                )
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
                Toast.makeText(context, "Pengaturan AI Engine & Memory tersimpan di perangkat", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showEditMessage) {
        AlertDialog(
            onDismissRequest = { showEditMessage = false },
            title = { Text("Edit pesan", color = Color.White) },
            containerColor = Color(0xFF0F172A),
            text = {
                OutlinedTextField(
                    value = editingDraft,
                    onValueChange = { editingDraft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    minLines = 3,
                    maxLines = 12
                )
            },
            confirmButton = {
                Button(
                    onClick = { saveEditedMessage() },
                    enabled = editingDraft.isNotBlank() && !busy
                ) { Text("Simpan & kirim ulang") }
            },
            dismissButton = { TextButton(onClick = { showEditMessage = false }) { Text("Batal") } }
        )
    }

    if (showHistory) {
        val conversations = remember(showHistory, historyRevision) { store.conversations() }
        AlertDialog(
            onDismissRequest = { showHistory = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Riwayat Percakapan", color = Color.White)
                    if (conversations.isNotEmpty()) {
                        TextButton(onClick = {
                            store.deleteAllConversations()
                            newConversation()
                            historyRevision++
                            showHistory = false
                        }) {
                            Text("Reset Total", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            text = {
                if (conversations.isEmpty()) Text("Belum ada riwayat percakapan.", color = Color(0xFF94A3B8))
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
                            ) { Text(item.title, maxLines = 2, color = Color.White) }
                            IconButton(onClick = {
                                store.deleteConversation(item.id)
                                if (conversation.id == item.id) newConversation()
                                historyRevision++
                            }) { Icon(Icons.Default.Delete, contentDescription = "Hapus percakapan", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Tutup") } }
        )
    }
}

/**
 * Pulsing connection indicator dot (smooth animated glowing green when online, slate when offline).
 */
@Composable
private fun PulsingConnectionDot(isOnline: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_conn")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val dotColor = if (isOnline) Color(0xFF10B981) else Color(0xFF64748B)

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor.copy(alpha = if (isOnline) alpha else 1f))
    )
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
    var reasoningDepth by remember(initial) { mutableStateOf(initial.reasoningDepth) }
    var chatMode by remember(initial) { mutableStateOf(initial.chatMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Pengaturan RAILX AI Engine", fontWeight = FontWeight.Bold, color = Color.White) },
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
                    color = Color(0xFF56F0D0)
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
                                containerColor = Color(0xFF1E293B)
                            ) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(
                                text = "$icon $label",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color(0xFF56F0D0) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val desc = when (chatMode) {
                        RailxChatMode.FAST -> "⚡ FAST: Respons kilat & to-the-point (max 250 token, temp 0.3). Cocok untuk tanya cepat, konfirmasi, atau sintaks ringkas."
                        RailxChatMode.SEDANG -> "⚖️ SEDANG: Respons standar & terstruktur (max 750 token, temp 0.65). Keseimbangan antara kelugasan dan kelengkapan berformat rapi."
                        RailxChatMode.PRO -> "🧠 PRO: Analisis mendalam & komprehensif (max 2048 token, temp 0.75). Bedah konsep rumit, arsitektur/coding, logika, contoh konkret, dan kesimpulan."
                    }
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    "Basis Memori & Pengetahuan Permanen",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF56F0D0)
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
                    color = Color(0xFF56F0D0)
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

                Text("Gaya Bahasa Tambahan (Opsional)", style = MaterialTheme.typography.labelMedium, color = Color(0xFFCBD5E1))
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    label = { Text("Persona / Gaya") },
                    placeholder = { Text("cth: Santai, ramah, mentor senior...") },
                    minLines = 1,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Kedalaman Penalaran", style = MaterialTheme.typography.labelMedium, color = Color(0xFFCBD5E1))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("brief" to "Ringkas", "balanced" to "Seimbang", "deep" to "Mendalam").forEach { (value, label) ->
                        OutlinedButton(onClick = { reasoningDepth = value }) {
                            Text(
                                if (reasoningDepth == value) "✓ $label" else label,
                                color = if (reasoningDepth == value) Color(0xFF56F0D0) else Color(0xFF94A3B8)
                            )
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
        clipboard.setPrimaryClip(ClipData.newPlainText("RAILX AI", text))
        Toast.makeText(context, "Bagian teks disalin", Toast.LENGTH_SHORT).show()
    }
}

private const val MAX_USER_MESSAGE_CHARS = 40_000
