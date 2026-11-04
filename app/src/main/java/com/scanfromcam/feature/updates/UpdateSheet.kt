package com.scanfromcam.feature.updates

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanfromcam.core.AppRelease
import com.scanfromcam.core.AppSettings
import com.scanfromcam.core.DownloadProgress
import com.scanfromcam.core.Failure
import com.scanfromcam.core.Lang
import com.scanfromcam.core.Strings
import com.scanfromcam.core.UpdateState
import com.scanfromcam.core.Updater
import com.scanfromcam.core.extractNotes
import com.scanfromcam.core.message
import com.scanfromcam.ui.theme.Tokens
import java.util.Locale

/**
 * One sheet for the whole update flow: asking for consent, offering the version,
 * the download and the install hand-off. Drawn inside the composition like the
 * rest of the app's sheets, so insets and touch handling stay predictable.
 */
@Composable
fun UpdateSheet(updater: Updater, settings: AppSettings) {
    val t = Strings.t
    val state by updater.state.collectAsState()
    val activity = LocalContext.current as? Activity
    val current = state
    val dismissible = current is UpdateState.Available ||
        current is UpdateState.Ready ||
        current is UpdateState.NeedsPermission ||
        current is UpdateState.Failed

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                enabled = dismissible,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { updater.dismiss() },
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
            when {
                settings.updateConsent == null -> ConsentContent(
                    onAllow = {
                        settings.updateConsent = true
                        updater.checkManually()
                    },
                    onDeny = { settings.updateConsent = false },
                )

                current is UpdateState.Available -> AvailableContent(
                    installed = installedVersion(),
                    release = current.release,
                    onDownload = { updater.startDownload() },
                    onLater = { updater.dismiss() },
                )

                current is UpdateState.Downloading -> DownloadingContent(
                    installed = installedVersion(),
                    release = current.release,
                    progress = current.progress,
                    onCancel = { updater.cancelDownload() },
                )

                current is UpdateState.Ready -> ReadyContent(
                    installed = installedVersion(),
                    release = current.release,
                    onInstall = { activity?.let(updater::install) },
                    onDelete = { updater.deleteDownload() },
                )

                current is UpdateState.NeedsPermission -> PermissionContent(
                    onGrant = { activity?.let(updater::openInstallSettings) },
                )

                current is UpdateState.Failed && current.manual -> FailedContent(
                    kind = current.kind,
                    onRetry = { updater.checkManually() },
                    onClose = { updater.dismiss() },
                )

                current is UpdateState.Checking -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = Tokens.AccentMid, modifier = Modifier.width(20.dp).height(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(t.updateChecking, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleMedium)
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun ConsentContent(onAllow: () -> Unit, onDeny: () -> Unit) {
    val t = Strings.t
    Text(t.updateConsentTitle, color = Tokens.TextPrimary, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(10.dp))
    Text(t.updateConsentBody, color = Tokens.TextMuted, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(20.dp))
    PrimaryButton(t.updateAllow, onAllow)
    TextButton(onClick = onDeny, modifier = Modifier.fillMaxWidth()) {
        Text(t.updateDeny, color = Tokens.TextMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AvailableContent(
    installed: String,
    release: AppRelease,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    val t = Strings.t
    Text(t.updateAvailable, color = Tokens.TextPrimary, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(10.dp))
    VersionRow(installed, release.version)
    val notes = remember(release.notes, Strings.lang) {
        extractNotes(release.notes, Strings.lang == Lang.ES)
    }
    if (notes.isNotBlank()) {
        Spacer(Modifier.height(18.dp))
        Text(
            t.updateNotes.uppercase(),
            color = Tokens.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 176.dp)
                .clip(RoundedCornerShape(Tokens.RadiusCard.dp))
                .background(Tokens.Surface2)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            Text(notes, color = Tokens.TextPrimary, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        t.updateSize(megabytes(release.sizeBytes)),
        color = Tokens.TextMuted,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(t.updateDownload, onDownload)
    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
        Text(t.updateLater, color = Tokens.TextMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DownloadingContent(
    installed: String,
    release: AppRelease,
    progress: DownloadProgress,
    onCancel: () -> Unit,
) {
    val t = Strings.t
    Text(t.updateDownloading, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    VersionRow(installed, release.version)
    Spacer(Modifier.height(18.dp))
    LinearProgressIndicator(
        progress = { progress.fraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        color = Tokens.AccentMid,
        trackColor = Tokens.Surface2,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "${(progress.fraction * 100).toInt()}% · " +
            "${megabytes(progress.bytes)} / ${t.updateSize(megabytes(progress.total))} · " +
            "${megabytes(progress.bytesPerSecond)} MB/s",
        color = Tokens.TextMuted,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
    )
    Spacer(Modifier.height(14.dp))
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(t.cancel, color = Tokens.TextMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ReadyContent(
    installed: String,
    release: AppRelease,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = Strings.t
    Text(t.updateReady, color = Tokens.Success, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    VersionRow(installed, release.version)
    Spacer(Modifier.height(12.dp))
    Text(t.updateReadyHint, color = Tokens.TextMuted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(16.dp))
    PrimaryButton(t.updateInstall, onInstall)
    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
        Text(t.updateDelete, color = Tokens.TextMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PermissionContent(onGrant: () -> Unit) {
    val t = Strings.t
    Text(t.updatePermissionTitle, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    Text(t.updatePermissionBody, color = Tokens.TextMuted, style = MaterialTheme.typography.bodyMedium)
    if (Build.VERSION.SDK_INT < 26) {
        Spacer(Modifier.height(8.dp))
        Text(t.updatePermissionLegacy, color = Tokens.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(18.dp))
    if (Build.VERSION.SDK_INT >= 26) PrimaryButton(t.updatePermissionAction, onGrant)
}

@Composable
private fun FailedContent(kind: Failure, onRetry: () -> Unit, onClose: () -> Unit) {
    val t = Strings.t
    Text(kind.message(t), color = Tokens.Danger, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(18.dp))
    PrimaryButton(t.updateRetry, onRetry)
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
        Text(t.close, color = Tokens.TextMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun VersionRow(installed: String, available: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Pill(installed, Tokens.Surface2, Tokens.TextMuted)
        Text("→", color = Tokens.TextMuted, style = MaterialTheme.typography.titleMedium)
        Pill(available, Tokens.AccentMid.copy(alpha = 0.16f), Tokens.AccentMid)
    }
}

@Composable
private fun Pill(text: String, background: Color, content: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.RadiusCard.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Tokens.AccentMid,
            contentColor = Tokens.OnAccent,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun megabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f", bytes / 1_000_000.0)

/** The installed version, as the sheet prints it. */
@Composable
private fun installedVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}
