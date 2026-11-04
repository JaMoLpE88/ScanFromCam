package com.scanfromcam.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.scanfromcam.R
import com.scanfromcam.core.AppSettings
import com.scanfromcam.core.Lang
import com.scanfromcam.core.Strings
import com.scanfromcam.core.UpdateState
import com.scanfromcam.core.Updater
import com.scanfromcam.core.message
import com.scanfromcam.ui.theme.Tokens
import com.scanfromcam.ui.deviceHasCredential

private const val WEB_URL = "http://jmolo.es"
private const val GITHUB_URL = "https://github.com/JaMoLpE88"
private const val REPO_URL = "https://github.com/JaMoLpE88/ScanFromCam"

@Composable
fun SettingsScreen(
    settings: AppSettings,
    updater: Updater,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val t = Strings.t
    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize().background(Tokens.Surface0)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = t.back, tint = Tokens.TextPrimary)
                }
                Text(
                    t.settingsTitle,
                    color = Tokens.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Section(t.language) {
                    LanguageRow(selected = settings.language) { settings.language = it }
                }

                Section(t.sound) {
                    ToggleRow(
                        title = t.beepFramed,
                        subtitle = t.beepFramedHint,
                        checked = settings.frameBeep,
                    ) { settings.frameBeep = it }
                    RowDivider()
                    ToggleRow(
                        title = t.shutterSound,
                        subtitle = t.shutterSoundHint,
                        checked = settings.shutterSound,
                    ) { settings.shutterSound = it }
                }

                Section(t.camera) {
                    ToggleRow(
                        title = t.autoCapture,
                        subtitle = t.autoCaptureHint,
                        checked = settings.autoCapture,
                    ) { settings.autoCapture = it }
                    RowDivider()
                    DelayRow(
                        title = t.framingDelay,
                        subtitle = t.framingDelayHint,
                        selected = settings.autoCaptureDelay,
                    ) { settings.autoCaptureDelay = it }
                }

                Section(t.watermark) {
                    ToggleRow(
                        title = t.watermarkEnabled,
                        subtitle = t.watermarkHint,
                        checked = settings.watermark,
                    ) { settings.watermark = it }
                    RowDivider()
                    WatermarkTextRow(
                        title = t.watermarkText,
                        subtitle = t.watermarkTextHint,
                        value = settings.watermarkText,
                        onChange = { settings.watermarkText = it },
                    )
                }

                Section(t.lockSection) {
                    val canLock = remember { deviceHasCredential(context) }
                    ToggleRow(
                        title = t.appLockToggle,
                        subtitle = if (canLock) t.appLockHint else t.lockNeedsDeviceSecurity,
                        checked = settings.appLockEnabled && canLock,
                        enabled = canLock,
                    ) { enabled ->
                        settings.appLockEnabled = enabled
                    }
                }

                Section(t.updates) {
                    UpdateCheckRow(settings = settings, updater = updater)
                    RowDivider()
                    ToggleRow(
                        title = t.updateAuto,
                        subtitle = t.updateAutoHint,
                        checked = settings.autoDownload && settings.updateConsent == true,
                    ) { checked ->
                        if (checked && settings.updateConsent != true) {
                            // Turning it on is asking again: the consent sheet answers.
                            settings.updateConsent = null
                        } else {
                            settings.autoDownload = checked
                        }
                    }
                }

                Section(t.about) {
                    LinkRow(
                        title = t.web,
                        value = "jmolo.es",
                        icon = { Icon(Icons.Filled.Public, contentDescription = null, tint = Tokens.AccentMid) },
                    ) { open(context, WEB_URL) }
                    RowDivider()
                    LinkRow(
                        title = "GitHub",
                        value = "@JaMoLpE88",
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = null,
                                tint = Tokens.AccentMid,
                            )
                        },
                    ) { open(context, GITHUB_URL) }
                    RowDivider()
                    LinkRow(
                        title = t.repository,
                        value = "JaMoLpE88/ScanFromCam",
                        icon = { Icon(Icons.Filled.Code, contentDescription = null, tint = Tokens.AccentMid) },
                    ) { open(context, REPO_URL) }
                }

                Text(
                    text = "ScanFromCam ${versionName(context)}",
                    color = Tokens.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            color = Tokens.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Tokens.RadiusCard.dp))
                .background(Tokens.Surface1)
                .border(1.dp, Tokens.Stroke, RoundedCornerShape(Tokens.RadiusCard.dp)),
            content = content,
        )
    }
}

@Composable
private fun UpdateCheckRow(settings: AppSettings, updater: Updater) {
    val t = Strings.t
    val state by updater.state.collectAsState()
    val current = state
    val allowed = settings.updateConsent == true
    val checking = current is UpdateState.Checking
    val status = when {
        !allowed -> t.updateDisabled
        current is UpdateState.Checking -> t.updateChecking
        current is UpdateState.Available -> "${t.updateAvailable} · ${current.release.version}"
        current is UpdateState.Downloading -> t.updateDownloading
        current is UpdateState.Ready -> t.updateReady
        current is UpdateState.NeedsPermission -> t.updateReady
        current is UpdateState.Failed && current.manual -> current.kind.message(t)
        else -> t.updateUpToDate
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !checking) {
                if (allowed) updater.checkManually() else settings.updateConsent = null
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(t.updateCheck, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(status, color = Tokens.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (checking) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(color = Tokens.AccentMid, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) Tokens.TextPrimary else Tokens.TextMuted,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(subtitle, color = Tokens.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun WatermarkTextRow(
    title: String,
    subtitle: String,
    value: String,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            title,
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            subtitle,
            color = Tokens.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(AppSettings.DEFAULT_WATERMARK) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DelayRow(
    title: String,
    subtitle: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Tokens.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..AppSettings.MAX_DELAY).forEach { seconds ->
                DelayPill(
                    label = "${seconds}s",
                    selected = seconds == selected,
                ) { onSelect(seconds) }
            }
        }
    }
}

@Composable
private fun DelayPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Tokens.AccentMid.copy(alpha = 0.16f) else Tokens.Surface2)
            .border(1.dp, if (selected) Tokens.AccentMid else Tokens.Stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Tokens.AccentMid else Tokens.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LanguageRow(selected: Lang, onSelect: (Lang) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LanguagePill(Lang.ES, "Español", selected == Lang.ES) { onSelect(Lang.ES) }
        LanguagePill(Lang.EN, "English", selected == Lang.EN) { onSelect(Lang.EN) }
    }
}

@Composable
private fun LanguagePill(lang: Lang, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Tokens.AccentMid.copy(alpha = 0.16f) else Tokens.Surface2)
            .border(1.dp, if (selected) Tokens.AccentMid else Tokens.Stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Flag(lang, Modifier.size(width = 24.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) Tokens.AccentMid else Tokens.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Spain for Spanish, United States for English. */
@Composable
private fun Flag(lang: Lang, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (lang) {
            Lang.ES -> {
                drawRect(Color(0xFFAA151B))
                drawRect(
                    color = Color(0xFFF1BF00),
                    topLeft = Offset(0f, size.height * 0.25f),
                    size = Size(size.width, size.height * 0.5f),
                )
            }
            Lang.EN -> {
                drawRect(Color.White)
                val stripe = size.height / 13f
                for (index in 0 until 13 step 2) {
                    drawRect(
                        color = Color(0xFFB22234),
                        topLeft = Offset(0f, index * stripe),
                        size = Size(size.width, stripe),
                    )
                }
                val canton = Size(size.width * 0.42f, stripe * 7f)
                drawRect(color = Color(0xFF3C3B6E), size = canton)
                for (row in 0 until 4) {
                    for (column in 0 until 4) {
                        drawCircle(
                            color = Color.White,
                            radius = stripe * 0.22f,
                            center = Offset(
                                x = canton.width * (column + 0.5f) / 4f,
                                y = canton.height * (row + 0.5f) / 4f,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    value: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                color = Tokens.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = Tokens.Stroke,
        modifier = Modifier.padding(start = 16.dp),
    )
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun versionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
