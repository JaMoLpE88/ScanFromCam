package com.scanfromcam.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scanfromcam.core.Strings
import com.scanfromcam.core.openFile
import com.scanfromcam.core.shareFiles
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.ui.theme.Tokens
import java.io.File

@Composable
fun ViewerScreen(
    vm: ViewerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val t = Strings.t
    val document = vm.document
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document?.name ?: "",
                        color = Tokens.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(if (document?.format == DocFormat.PDF) "PDF" else t.imageFormat)
                            append(" · ")
                            append(t.pageCount(vm.pages.size))
                        },
                        color = Tokens.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Opciones", tint = Tokens.TextPrimary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(t.share) },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menuOpen = false
                                val files = vm.shareFiles()
                                if (files.isEmpty()) {
                                    vm.error = t.nothingToShare
                                } else {
                                    shareFiles(context, files)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(t.rename) },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuOpen = false; showRename = true },
                        )
                        if (vm.pages.any { !it.ocrText.isNullOrBlank() }) {
                            DropdownMenuItem(
                                text = { Text(t.viewText) },
                                leadingIcon = { Icon(Icons.Filled.TextFields, null) },
                                onClick = { menuOpen = false; showText = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(t.delete, color = Tokens.Danger) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Tokens.Danger) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }

            if (vm.pages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t.noPages, color = Tokens.TextMuted)
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { vm.pages.size })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) { index ->
                    val page = vm.pages[index]
                    Box(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = File(page.originalPath),
                            contentDescription = t.pageNumber(index + 1),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${vm.pages.size}",
                        color = Tokens.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.size(16.dp))
                    val pdf = vm.pdfFile()
                    if (pdf != null && pdf.exists()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Tokens.Surface2)
                                .clickable { openFile(context, pdf, "application/pdf") }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text(t.openPdf, color = Tokens.AccentMid, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        if (document == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(t.loading, color = Tokens.TextMuted)
            }
        }
    }

    if (showRename && document != null) {
        var name by remember { mutableStateOf(document.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(t.rename) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t.name) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRename = false
                    vm.rename(name) {}
                }) { Text(t.save) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(t.cancel) }
            },
        )
    }

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.error = null },
            title = { Text(t.somethingWrong) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.error = null }) { Text(t.gotIt) }
            },
        )
    }

    if (showText) {
        AlertDialog(
            onDismissRequest = { showText = false },
            title = { Text(t.extractedText) },
            text = {
                Column(modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        vm.pages.mapNotNull { it.ocrText }.filter { it.isNotBlank() }.joinToString("\n\n"),
                        color = Tokens.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showText = false }) { Text(t.close) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t.deleteDocument) },
            text = { Text(t.deleteDocumentHint) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete { onBack() }
                }) { Text(t.delete, color = Tokens.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(t.cancel) }
            },
        )
    }
}
