package dev.andikune.robotasisten.io.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.data.ai.RelxChatMessage
import dev.andikune.robotasisten.io.data.ai.RelxChatStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Modern Chat Bubble for RAILX AI with:
 * - Clear visual hierarchy (Deep Blue for User, Dark Slate Cyber for AI)
 * - Action buttons HIDDEN by default
 * - Context menu popping up on LONG PRESS (Copy, Reply, Edit, Regenerate, Delete)
 * - Swipe-to-Reply gesture to quote specific messages
 * - Partial text selection via SelectionContainer
 */
@Composable
fun ModernChatBubble(
    message: RelxChatMessage,
    busy: Boolean,
    onSwipeToReply: (RelxChatMessage) -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val isUser = message.role == RelxChatStore.ROLE_USER
    val scope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    var showContextMenu by remember { mutableStateOf(false) }

    // Parse potential reply quote from message content (e.g. "> [Quoted text]")
    val lines = message.content.lines()
    val hasQuote = lines.firstOrNull()?.startsWith("> ") == true
    val quotedText = if (hasQuote) lines.first().removePrefix("> ").trim() else null
    val actualContent = if (hasQuote) lines.drop(1).joinToString("\n").trimStart() else message.content

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Swipe to Reply Indicator behind the bubble (revealed on right swipe)
        if (dragOffsetX.value > 10f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0EA5E9).copy(alpha = (dragOffsetX.value / 80f).coerceIn(0.2f, 1f))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Swipe to reply",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Main Bubble Row that translates on swipe
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (delta > 0 || dragOffsetX.value > 0f) {
                            val newOffset = (dragOffsetX.value + delta).coerceIn(0f, 160f)
                            scope.launch { dragOffsetX.snapTo(newOffset) }
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (dragOffsetX.value > 75f) {
                            onSwipeToReply(message)
                        }
                        scope.launch {
                            dragOffsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    }
                ),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // 1. AVATAR RAILX AI (KIRI)
            if (!isUser) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_art),
                    contentDescription = "RAILX AI Icon",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 2. BUBBLE KONTEN DENGAN DETEKSI LONG PRESS
            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                showContextMenu = true
                            }
                        )
                    }
            ) {
                Surface(
                    color = if (isUser) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    ),
                    border = if (!isUser) BorderStroke(1.dp, Color(0xFF334155)) else null,
                    shadowElevation = 2.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        // Header Nama Pengirim
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isUser) "Anda" else "RAILX AI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) Color(0xFF93C5FD) else Color(0xFF56F0D0)
                            )
                        }

                        // Preview Pesan Yang Dibalas (Jika Ada Quote)
                        if (!quotedText.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = if (isUser) Color(0xFF172554) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(22.dp)
                                            .background(if (isUser) Color(0xFF60A5FA) else Color(0xFF56F0D0))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = quotedText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Konten Teks
                        if (isUser) {
                            SelectionContainer {
                                Text(
                                    text = actualContent,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            AssistantFormattedContent(
                                content = actualContent,
                                onCopySnippet = onCopy
                            )
                        }
                    }
                }

                // ==========================================
                // 3. MENU AKSI CEPAT (LONG-PRESS CONTEXT MENU)
                // ==========================================
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    modifier = Modifier.background(Color(0xFF0F172A))
                ) {
                    // Salin Teks
                    DropdownMenuItem(
                        text = { Text("Salin Pesan", color = Color.White) },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        onClick = {
                            showContextMenu = false
                            onCopy(actualContent)
                        }
                    )

                    // Balas Pesan (Quote / Reply)
                    DropdownMenuItem(
                        text = { Text("Balas", color = Color.White) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Color(0xFF38BDF8))
                        },
                        onClick = {
                            showContextMenu = false
                            onSwipeToReply(message)
                        }
                    )

                    // Edit Pesan (hanya jika pesan pengguna)
                    if (isUser && onEdit != null) {
                        DropdownMenuItem(
                            text = { Text("Edit Pesan", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFFBBF24))
                            },
                            enabled = !busy,
                            onClick = {
                                showContextMenu = false
                                onEdit()
                            }
                        )
                    }

                    // Regenerate (hanya jika pesan AI)
                    if (!isUser && onRegenerate != null) {
                        DropdownMenuItem(
                            text = { Text("Regenerate", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF34D399))
                            },
                            enabled = !busy,
                            onClick = {
                                showContextMenu = false
                                onRegenerate()
                            }
                        )
                    }

                    // Hapus Pesan
                    DropdownMenuItem(
                        text = { Text("Hapus", color = Color(0xFFEF4444)) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                        },
                        enabled = !busy,
                        onClick = {
                            showContextMenu = false
                            onDelete()
                        }
                    )
                }
            }

            // 4. AVATAR USER (KANAN)
            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Profile Icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private data class MessagePart(val code: Boolean, val text: String, val language: String = "")

@Composable
private fun AssistantFormattedContent(content: String, onCopySnippet: (String) -> Unit) {
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
                SelectionContainer {
                    Text(
                        text = part.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 20.sp
                    )
                }
            } else {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = part.language.ifBlank { "Snippet Kode" },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF56F0D0)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onCopySnippet(part.text) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Salin snippet",
                                        tint = Color(0xFF56F0D0),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "Salin",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF56F0D0)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer {
                            Text(
                                text = part.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }
        }
    }
}
