package com.scanfromcam.feature.format

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanfromcam.core.Strings
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.ui.theme.Tokens

/**
 * Format chooser drawn inside the app composition: no separate window, so touch
 * handling and insets always match what is on screen.
 */
@Composable
fun FormatSheet(
    onPick: (DocFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = Strings.t
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Tokens.RadiusSheet.dp, topEnd = Tokens.RadiusSheet.dp))
                .background(Tokens.Surface1)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(
                t.formatTitle,
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                t.formatHint,
                color = Tokens.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormatOption(
                    title = t.imageFormat,
                    subtitle = t.imageFormatHint,
                    icon = Icons.Filled.Image,
                    modifier = Modifier.weight(1f),
                    onClick = { onPick(DocFormat.IMAGE) },
                )
                FormatOption(
                    title = "PDF",
                    subtitle = t.pdfFormatHint,
                    icon = Icons.Filled.PictureAsPdf,
                    modifier = Modifier.weight(1f),
                    onClick = { onPick(DocFormat.PDF) },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FormatOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Tokens.RadiusCard.dp))
            .background(Tokens.Surface2)
            .border(1.dp, Tokens.Stroke, RoundedCornerShape(Tokens.RadiusCard.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Tokens.AccentMid, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Tokens.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}
