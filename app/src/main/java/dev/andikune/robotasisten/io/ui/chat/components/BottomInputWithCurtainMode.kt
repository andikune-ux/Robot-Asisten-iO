package dev.andikune.robotasisten.io.ui.chat.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.data.ai.RailxChatMode
import dev.andikune.robotasisten.io.data.ai.RelxChatMessage
import dev.andikune.robotasisten.io.data.ai.RelxChatStore

/**
 * Modern Bottom Bar layout for RAILX AI:
 * - Speedometer icon stacked vertically directly above the Attachment icon in the bottom-left corner.
 * - Expanded, full-width input field (OutlinedTextField) for enhanced typing comfort.
 * - Quoted message preview bar when answering via Swipe-to-Reply.
 * - Dynamic Send / Stop action button.
 */
@Composable
fun BottomInputWithCurtainMode(
    currentMode: RailxChatMode,
    isGenerating: Boolean,
    inputText: String,
    attachments: List<Uri>,
    replyToMessage: RelxChatMessage? = null,
    onCancelReply: () -> Unit = {},
    onModeChanged: (RailxChatMode) -> Unit,
    onSpeedometerClick: () -> Unit = {},
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onUploadClick: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit
) {
    var isQuickModeExpanded by remember { mutableStateOf(false) }

    val modeAccentColor = when (currentMode) {
        RailxChatMode.FAST -> Color(0xFFFFB74D) // Amber / Fast
        RailxChatMode.SEDANG -> Color(0xFF64B5F6) // Sky Blue / Sedang
        RailxChatMode.PRO -> Color(0xFFBA68C8) // Purple / Pro
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.92f)) // Dark Slate container
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // ==========================================
        // 1. BANNER QUOTE (SWIPE TO REPLY) JIKA AKTIF
        // ==========================================
        AnimatedVisibility(
            visible = replyToMessage != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            replyToMessage?.let { reply ->
                val isReplyUser = reply.role == RelxChatStore.ROLE_USER
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colored vertical accent line
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isReplyUser) Color(0xFF38BDF8) else Color(0xFF56F0D0))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Membalas",
                            tint = if (isReplyUser) Color(0xFF38BDF8) else Color(0xFF56F0D0),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isReplyUser) "Membalas Anda" else "Membalas RAILX AI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isReplyUser) Color(0xFF38BDF8) else Color(0xFF56F0D0)
                            )
                            Text(
                                text = reply.content.replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onCancelReply,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Batal kutipan",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. PREVIEW BAR LAMPIRAN FILE (JIKA ADA)
        // ==========================================
        if (attachments.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                items(attachments) { uri ->
                    InputChip(
                        selected = true,
                        onClick = { },
                        label = {
                            Text(
                                uri.lastPathSegment ?: "File",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hapus",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onRemoveAttachment(uri) }
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = Color(0xFF334155)
                        )
                    )
                }
            }
        }

        // ==========================================
        // 3. MINI SPEEDOMETER POPUP (TOGGLE)
        // ==========================================
        AnimatedVisibility(
            visible = isQuickModeExpanded,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeItem("⚡ Fast", "Instan & Ringkas", currentMode == RailxChatMode.FAST, Color(0xFFFFB74D)) {
                    onModeChanged(RailxChatMode.FAST)
                    isQuickModeExpanded = false
                }
                ModeItem("⚖️ Sedang", "Rapi & Seimbang", currentMode == RailxChatMode.SEDANG, Color(0xFF64B5F6)) {
                    onModeChanged(RailxChatMode.SEDANG)
                    isQuickModeExpanded = false
                }
                ModeItem("🧠 Pro", "Mendalam & Komplit", currentMode == RailxChatMode.PRO, Color(0xFFBA68C8)) {
                    onModeChanged(RailxChatMode.PRO)
                    isQuickModeExpanded = false
                }
            }
        }

        // ==========================================
        // 4. BARIS UTAMA INPUT TEKS & DUA IKON KIRI VERTIKAL
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // SISI KIRI BAWAH: KOLOM VERTIKAL DUA TOMBOL (SPEEDOMETER DI ATAS DOKUMEN/FILE)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                // Ikon Speedometer (di atas)
                IconButton(
                    onClick = {
                        isQuickModeExpanded = !isQuickModeExpanded
                        onSpeedometerClick()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(modeAccentColor.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Mode Kecepatan AI (Speedometer)",
                        tint = modeAccentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Ikon File / Dokumen (di bawah speedometer)
                IconButton(
                    onClick = onUploadClick,
                    enabled = !isGenerating,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Lampirkan file dokumen",
                        tint = if (!isGenerating) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // AREA TENGAH: FIELD INPUT TEKS (DIPERLUAS PENUH)
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        "Tanya RAILX AI…",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp)),
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                    focusedBorderColor = modeAccentColor,
                    unfocusedBorderColor = Color(0xFF334155),
                    cursorColor = modeAccentColor
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (!isGenerating && (inputText.isNotBlank() || attachments.isNotEmpty())) {
                        onSendClick()
                    }
                })
            )

            // SISI KANAN: TOMBOL AKSI DINAMIS (KIRIM ATAU STOP)
            Box(
                modifier = Modifier.padding(bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    IconButton(
                        onClick = onStopClick,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Hentikan respon AI",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    val canSend = inputText.isNotBlank() || attachments.isNotEmpty()
                    IconButton(
                        onClick = onSendClick,
                        enabled = canSend,
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (canSend) Color(0xFF2563EB) else Color(0xFF334155),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim pesan",
                            tint = if (canSend) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            color = if (isSelected) accentColor else Color(0xFFCBD5E1)
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = Color(0xFF94A3B8)
        )
    }
}
