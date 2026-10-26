package dev.andikune.robotasisten.io.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.ui.theme.ErrorRed
import dev.andikune.robotasisten.io.ui.theme.SuccessGreen

@Composable
fun PermissionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("permission_item_${title.replace(" ", "_").lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) SuccessGreen else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (isGranted) "Aktif" else "Belum aktif",
                        tint = if (isGranted) SuccessGreen else ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isGranted) {
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("grant_btn_${title.replace(" ", "_").lowercase()}")
                ) {
                    Text("Buka Izin", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                OutlinedButton(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Pengaturan", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
