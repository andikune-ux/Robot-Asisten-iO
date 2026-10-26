package dev.andikune.robotasisten.io.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.ui.theme.ErrorRed
import dev.andikune.robotasisten.io.ui.theme.SuccessGreen
import dev.andikune.robotasisten.io.ui.theme.WarningAmber

@Composable
fun StatusIndicator(
    text: String,
    status: StatusType,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        StatusType.ONLINE, StatusType.SUCCESS -> SuccessGreen
        StatusType.WARNING, StatusType.BUSY -> WarningAmber
        StatusType.ERROR -> ErrorRed
        StatusType.IDLE -> Color.Gray
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

enum class StatusType {
    ONLINE,
    SUCCESS,
    WARNING,
    BUSY,
    ERROR,
    IDLE
}
