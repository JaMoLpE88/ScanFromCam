package com.scanfromcam.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scanfromcam.core.Strings
import com.scanfromcam.core.Texts
import com.scanfromcam.domain.model.DocumentMeta
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.ui.theme.Tokens
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onScan: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val t = Strings.t
    Box(modifier = Modifier.fillMaxSize().background(Tokens.Surface0)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        t.library,
                        color = Tokens.TextPrimary,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        text = t.documentCount(vm.documents.size),
                        color = Tokens.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = t.settingsTitle,
                        tint = Tokens.TextMuted,
                    )
                }
            }

            vm.error?.let { message ->
                Text(
                    text = message,
                    color = Tokens.Danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            when {
                vm.loading && vm.documents.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Tokens.AccentMid)
                    }
                }

                vm.documents.isEmpty() -> EmptyState(onScan = onScan)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(vm.documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            thumbnail = vm.thumbnails[document.id],
                            onClick = { onOpen(document.id) },
                        )
                    }
                }
            }
        }

        ScanButton(
            onClick = onScan,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    val t = Strings.t
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            t.noScansTitle,
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            t.noScansHint,
            color = Tokens.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            t.storedLocally,
            color = Tokens.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun DocumentCard(
    document: DocumentMeta,
    thumbnail: String?,
    onClick: () -> Unit,
) {
    val t = Strings.t
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.RadiusCard.dp))
            .background(Tokens.Surface1)
            .border(1.dp, Tokens.Stroke, RoundedCornerShape(Tokens.RadiusCard.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Tokens.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = File(thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("—", color = Tokens.TextMuted)
            }
        }

        Spacer(Modifier.size(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                document.name,
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(if (document.format == DocFormat.PDF) "PDF" else t.imageFormat)
                    append(" · ")
                    append(t.pageCount(document.pageCount))
                },
                color = Tokens.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = relativeDate(t, document.updatedAt),
                color = Tokens.TextMuted.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ScanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.linearGradient(listOf(Tokens.AccentStart, Tokens.AccentMid, Tokens.AccentEnd))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = Tokens.OnAccent)
            Spacer(Modifier.size(8.dp))
            Text(
                Strings.t.scan,
                color = Tokens.OnAccent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun relativeDate(t: Texts, timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> t.now
        diff < 3_600_000 -> "${diff / 60_000} min"
        diff < 86_400_000 -> "${diff / 3_600_000} h"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} d"
        else -> SimpleDateFormat("dd MMM yyyy", t.locale).format(Date(timestamp))
    }
}


