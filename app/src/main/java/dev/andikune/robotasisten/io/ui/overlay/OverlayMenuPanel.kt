package dev.andikune.robotasisten.io.ui.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.robotasisten.io.R

@Composable
fun OverlayMenuPanel(
    activeMode: String,
    confirmBeforeFill: Boolean,
    onAnalyze: () -> Unit,
    onAutoAnswer: () -> Unit,
    onOcrRead: () -> Unit,
    onOpenPower: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onHideBubble: () -> Unit,
    onTurnOffRobot: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .width(280.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .testTag("overlay_menu_panel")
    ) {
        Column(modifier = Modifier.padding(14.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Mode: ${activeMode.ifBlank { "Belum dipilih" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Ketuk bubble untuk membuka/menutup menu atau menutup hasil; ketuk dua kali untuk analisa; tahan untuk membuka menu; seret untuk memindahkan.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            MenuItemRow(title = "🔍 Analisa Layar", onClick = onAnalyze)
            MenuItemRow(
                title = if (confirmBeforeFill) "🧠 Siapkan Jawaban (konfirmasi)" else "⚡ Jawab & Isi Otomatis",
                onClick = onAutoAnswer
            )
            MenuItemRow(title = "📸 Baca Teks Layar (OCR)", onClick = onOcrRead)
            MenuItemRow(title = "⚡ RELX Power", onClick = onOpenPower)
            MenuItemRow(title = "📜 Riwayat", onClick = onOpenHistory)
            MenuItemRow(title = "⚙️ Pengaturan", onClick = onOpenSettings)
            MenuItemRow(title = "👁 Sembunyikan Bubble", onClick = onHideBubble)
            MenuItemRow(
                title = "❌ Matikan Robot",
                onClick = onTurnOffRobot,
                isDanger = true
            )
        }
    }
}

@Composable
private fun MenuItemRow(
    title: String,
    onClick: () -> Unit,
    isDanger: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isDanger) FontWeight.Bold else FontWeight.Medium,
            color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}
