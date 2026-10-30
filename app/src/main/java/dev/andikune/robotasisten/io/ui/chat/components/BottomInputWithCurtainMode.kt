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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.data.ai.RailxChatMode

@Composable
fun BottomInputWithCurtainMode(
    currentMode: RailxChatMode,
    isGenerating: Boolean,
    inputText: String,
    attachments: List<Uri>,
    onModeChanged: (RailxChatMode) -> Unit,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onUploadClick: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit
) {
    var isCurtainExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ==========================================
        // 1. PREVIEW BAR LAMPIRAN FILE (JIKA ADA)
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
                        label = { Text(uri.lastPathSegment ?: "File", fontSize = 11.sp) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hapus",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onRemoveAttachment(uri) }
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        // ==========================================
        // 2. PANEL SLIDING TIRAI MODE (EXPANDED)
        // ==========================================
        AnimatedVisibility(
            visible = isCurtainExpanded,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeItem("⚡ Fast", "Instan & Ringkas", currentMode == RailxChatMode.FAST, Color(0xFFFFB74D)) {
                    onModeChanged(RailxChatMode.FAST)
                    isCurtainExpanded = false
                }
                ModeItem("⚖️ Sedang", "Rapi & Seimbang", currentMode == RailxChatMode.SEDANG, Color(0xFF64B5F6)) {
                    onModeChanged(RailxChatMode.SEDANG)
                    isCurtainExpanded = false
                }
                ModeItem("🧠 Pro", "Mendalam & Komplit", currentMode == RailxChatMode.PRO, Color(0xFFBA68C8)) {
                    onModeChanged(RailxChatMode.PRO)
                    isCurtainExpanded = false
                }
            }
        }

        // ==========================================
        // 3. BARIS UTAMA INPUT TEKS & AKSI DINAMIS
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tombol Tirai Mode (Toggle)
            val (modeIcon, modeTint) = when (currentMode) {
                RailxChatMode.FAST -> "⚡" to Color(0xFFFFB74D)
                RailxChatMode.SEDANG -> "⚖️" to Color(0xFF64B5F6)
                RailxChatMode.PRO -> "🧠" to Color(0xFFBA68C8)
            }
            Surface(
                shape = CircleShape,
                color = modeTint.copy(alpha = 0.2f),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { isCurtainExpanded = !isCurtainExpanded }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = modeIcon, fontSize = 20.sp)
                }
            }

            // Tombol Lampiran (Upload File)
            IconButton(
                onClick = onUploadClick,
                enabled = !isGenerating,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Lampirkan file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Field Input Teks
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                placeholder = { Text("Tanya RAILX AI…", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (!isGenerating && (inputText.isNotBlank() || attachments.isNotEmpty())) {
                        onSendClick()
                    }
                })
            )

            // Tombol Dinamis: Kirim atau Stop
            if (isGenerating) {
                IconButton(
                    onClick = onStopClick,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Hentikan respon",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            } else {
                val canSend = inputText.isNotBlank() || attachments.isNotEmpty()
                IconButton(
                    onClick = onSendClick,
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim pesan",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
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
            .background(if (isSelected) accentColor.copy(alpha = 0.25f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
