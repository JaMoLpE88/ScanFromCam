package com.scanfromcam.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scanfromcam.core.AppSettings
import com.scanfromcam.core.Strings
import com.scanfromcam.core.Texts
import com.scanfromcam.core.shareFiles
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.DocumentMeta
import com.scanfromcam.domain.model.FolderMeta
import com.scanfromcam.ui.theme.Tokens
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

/** One row or tile of the library: a folder or a document. */
private sealed interface Entry {
    val key: String

    data class Dir(val folder: FolderMeta) : Entry {
        override val key = "f:${folder.id}"
    }

    data class Doc(val meta: DocumentMeta, val thumbnail: String?) : Entry {
        override val key = "d:${meta.id}"
    }
}


@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    settings: AppSettings,
    onScan: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val t = Strings.t
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    // Row left swiped open while its confirmation dialog is up; it stays open until you decide.
    var revealedId by remember { mutableStateOf<String?>(null) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<FolderMeta?>(null) }
    var folderRename by remember { mutableStateOf<FolderMeta?>(null) }
    var folderDelete by remember { mutableStateOf<FolderMeta?>(null) }

    fun dismissDeleteDialog() {
        pendingDelete = null
        revealedId = null
    }

    fun share(ids: Set<String>) {
        scope.launch {
            if (!shareFiles(context, vm.shareFilesOf(ids))) vm.showError(t.shareFailed)
        }
    }

    BackHandler(enabled = vm.selecting) { vm.clearSelection() }
    BackHandler(enabled = !vm.selecting && vm.folderId != null) { vm.up() }

    // Folders are flat: they only make sense at the root, not inside another folder.
    val entries: List<Entry> = (if (vm.folderId == null) vm.folders.map(Entry::Dir) else emptyList()) +
        vm.documents.map { Entry.Doc(it, vm.thumbnails[it.id]) }

    fun openEntry(entry: Entry) {
        when (entry) {
            is Entry.Dir -> vm.open(entry.folder)
            is Entry.Doc -> onOpen(entry.meta.id)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Tokens.Surface0)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            if (vm.selecting) {
                SelectionBar(
                    count = vm.selection.size,
                    onClose = { vm.clearSelection() },
                    onShare = { share(vm.selection) },
                    onCut = { vm.cut() },
                    onCopy = { vm.copySelection() },
                    onDelete = { pendingDelete = vm.selection },
                )
            } else {
                LibraryHeader(
                    title = vm.folderName ?: t.library,
                    count = if (vm.folderId == null) vm.totalCount else vm.documents.size,
                    inFolder = vm.folderId != null,
                    grid = settings.gridView,
                    onUp = { vm.up() },
                    onNewFolder = { newFolderOpen = true },
                    onToggleView = { settings.gridView = !settings.gridView },
                    onSettings = onSettings,
                )
            }

            vm.clipboard?.let { clip ->
                ClipboardBar(
                    count = clip.ids.size,
                    onPaste = { vm.paste() },
                    onClear = { vm.clearClipboard() },
                )
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
                vm.loading && entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Tokens.AccentMid)
                    }
                }

                entries.isEmpty() -> {
                    if (vm.folderId != null) EmptyFolder() else EmptyState()
                }

                settings.gridView -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    gridItems(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is Entry.Dir -> FolderTile(
                                folder = entry.folder,
                                count = vm.folderCounts[entry.folder.id] ?: 0,
                                onClick = { openEntry(entry) },
                                onLongClick = { folderMenu = entry.folder },
                            )

                            is Entry.Doc -> DocumentTile(
                                document = entry.meta,
                                thumbnail = entry.thumbnail,
                                selecting = vm.selecting,
                                selected = entry.meta.id in vm.selection,
                                onClick = {
                                    if (vm.selecting) vm.toggle(entry.meta.id) else openEntry(entry)
                                },
                                onLongClick = { vm.toggle(entry.meta.id) },
                            )
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is Entry.Dir -> FolderRow(
                                folder = entry.folder,
                                count = vm.folderCounts[entry.folder.id] ?: 0,
                                onClick = { openEntry(entry) },
                                onLongClick = { folderMenu = entry.folder },
                            )

                            is Entry.Doc -> SwipeableDocumentRow(
                                document = entry.meta,
                                thumbnail = entry.thumbnail,
                                selecting = vm.selecting,
                                selected = entry.meta.id in vm.selection,
                                revealed = revealedId == entry.meta.id,
                                onClick = {
                                    if (vm.selecting) vm.toggle(entry.meta.id) else openEntry(entry)
                                },
                                onLongClick = { vm.toggle(entry.meta.id) },
                                onSwipeToDelete = {
                                    revealedId = entry.meta.id
                                    pendingDelete = setOf(entry.meta.id)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!vm.selecting) {
            ScanButton(
                onClick = onScan,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
            )
        }
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onDismiss = { newFolderOpen = false },
            onCreate = { name ->
                newFolderOpen = false
                vm.createFolder(name)
            },
        )
    }

    folderMenu?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderMenu = null },
            title = { Text(folder.name) },
            text = {
                Column {
                    TextButton(onClick = { folderMenu = null; folderRename = folder }) {
                        Text(t.rename)
                    }
                    TextButton(onClick = { folderMenu = null; folderDelete = folder }) {
                        Text(t.delete, color = Tokens.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { folderMenu = null }) { Text(t.close) }
            },
        )
    }


    folderRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderRename = null },
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
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        folderRename = null
                        vm.renameFolder(folder, name)
                    },
                ) { Text(t.save) }
            },
            dismissButton = {
                TextButton(onClick = { folderRename = null }) { Text(t.cancel) }
            },
        )
    }

    folderDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderDelete = null },
            title = { Text(t.deleteFolder) },
            text = { Text(t.deleteFolderHint) },
            confirmButton = {
                TextButton(onClick = {
                    folderDelete = null
                    vm.deleteFolder(folder)
                }) { Text(t.delete, color = Tokens.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { folderDelete = null }) { Text(t.cancel) }
            },
        )
    }

    pendingDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { dismissDeleteDialog() },
            title = { Text(if (ids.size == 1) t.deleteDocument else t.deleteSelected) },
            text = { Text(if (ids.size == 1) t.deleteDocumentHint else t.deleteSelectedHint) },
            confirmButton = {
                TextButton(onClick = {
                    dismissDeleteDialog()
                    vm.delete(ids)
                }) { Text(t.delete, color = Tokens.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { dismissDeleteDialog() }) { Text(t.cancel) }
            },
        )
    }
}

@Composable
private fun LibraryHeader(
    title: String,
    count: Int,
    inFolder: Boolean,
    grid: Boolean,
    onUp: () -> Unit,
    onNewFolder: () -> Unit,
    onToggleView: () -> Unit,
    onSettings: () -> Unit,
) {
    val t = Strings.t
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (inFolder) 4.dp else 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inFolder) {
            IconButton(onClick = onUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.library, tint = Tokens.TextPrimary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = t.documentCount(count),
                color = Tokens.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
        }

        if (!inFolder) {
            IconButton(onClick = onNewFolder) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = t.newFolder, tint = Tokens.TextMuted)
            }
        }
        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                contentDescription = if (grid) t.listView else t.gridView,
                tint = Tokens.TextMuted,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = t.settingsTitle, tint = Tokens.TextMuted)
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = Strings.t
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = t.cancel, tint = Tokens.TextPrimary)
            }
            Text(
                text = t.selectedCount(count),
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionItem(Modifier.weight(1f), Icons.Filled.Share, t.share, onShare)
            ActionItem(Modifier.weight(1f), Icons.Filled.ContentCut, t.cut, onCut)
            ActionItem(Modifier.weight(1f), Icons.Filled.ContentCopy, t.copy, onCopy)
            ActionItem(Modifier.weight(1f), Icons.Filled.Delete, t.delete, onDelete, danger = true)
        }
    }
}

@Composable
private fun ActionItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = if (danger) Tokens.Danger else Tokens.TextPrimary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun ClipboardBar(count: Int, onPaste: () -> Unit, onClear: () -> Unit) {
    val t = Strings.t
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Tokens.Surface2)
            .clickable(onClick = onPaste)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = Tokens.AccentMid)
        Spacer(Modifier.size(10.dp))
        Text(
            text = "${t.paste} ($count)",
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = t.cancel, tint = Tokens.TextMuted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderMeta,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.Surface1)
            .border(1.dp, Tokens.Stroke, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = Tokens.AccentMid)
        Spacer(Modifier.size(12.dp))
        Text(
            text = folder.name,
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "($count)",
            color = Tokens.TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(
    folder: FolderMeta,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(Tokens.Surface1)
                .border(1.dp, Tokens.Stroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = Tokens.AccentMid, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                text = folder.name,
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "($count)",
                color = Tokens.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentTile(
    document: DocumentMeta,
    thumbnail: String?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = Strings.t
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(shape)
                .background(Tokens.Surface1)
                .border(if (selected) 2.dp else 1.dp, if (selected) Tokens.AccentMid else Tokens.Stroke, shape),
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = File(thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("—", color = Tokens.TextMuted, modifier = Modifier.align(Alignment.Center))
            }
            if (selecting) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) Tokens.AccentMid else Tokens.TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = document.name,
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = t.pageCount(document.pageCount),
            color = Tokens.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableDocumentRow(
    document: DocumentMeta,
    thumbnail: String?,
    selecting: Boolean,
    selected: Boolean,
    revealed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToDelete: () -> Unit,
) {
    val t = Strings.t
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val revealPx = with(density) { RevealWidth.toPx() }
    val thresholdPx = with(density) { RevealThreshold.toPx() }
    val settle = tween<Float>(durationMillis = 160)

    // Where the card sits. While the dialog is up it stays pushed aside on purpose.
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }

    // Only this effect animates, so a release can never cancel the decision by racing the drag.
    LaunchedEffect(dragging, revealed) {
        if (dragging) return@LaunchedEffect
        offset.animateTo(if (revealed) -revealPx else 0f, settle)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(Tokens.RadiusCard.dp))
                .background(Tokens.Danger)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Delete, contentDescription = t.delete, tint = Tokens.OnAccent)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !selecting && !revealed,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStarted = { dragging = true },
                    onDragStopped = { velocity ->
                        val confirm = offset.value < -thresholdPx || velocity < -FlingVelocity
                        dragging = false
                        if (confirm) onSwipeToDelete()
                    },
                ),
        ) {
            DocumentCard(
                document = document,
                thumbnail = thumbnail,
                selecting = selecting,
                selected = selected,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}

private val RevealWidth = 96.dp
private val RevealThreshold = 48.dp
private const val FlingVelocity = 900f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCard(
    document: DocumentMeta,
    thumbnail: String?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = Strings.t
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.Surface1)
            .border(if (selected) 2.dp else 1.dp, if (selected) Tokens.AccentMid else Tokens.Stroke, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (selected) Tokens.AccentMid else Tokens.TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(12.dp))
        }

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
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val t = Strings.t
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.newFolder) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(t.name) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name) }) { Text(t.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        },
    )
}

@Composable
private fun EmptyFolder() {
    val t = Strings.t
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t.folderEmpty, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            t.noScansHint,
            color = Tokens.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState() {
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
