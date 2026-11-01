package com.scanfromcam.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanfromcam.core.Images
import com.scanfromcam.core.Strings
import com.scanfromcam.domain.geometry.CropHandle
import com.scanfromcam.domain.geometry.CropHandles
import com.scanfromcam.domain.geometry.Quads
import com.scanfromcam.domain.model.Finish
import com.scanfromcam.domain.model.Point
import com.scanfromcam.feature.flow.ScanFlowViewModel
import com.scanfromcam.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    vm: ScanFlowViewModel,
    onDiscard: () -> Unit,
) {
    val t = Strings.t
    val page = vm.currentPage
    if (page == null) {
        LaunchedEffect(Unit) { onDiscard() }
        return
    }

    var showSave by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val path = page.activePath(vm.finish)
    val rotation = page.rotation
    val bitmap by produceState<android.graphics.Bitmap?>(null, path, rotation, vm.revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val decoded = Images.decodeUpright(File(path), maxSide = 2048) ?: return@runCatching null
                if (rotation == 0) decoded else Images.rotate(decoded, rotation.toFloat())
            }.getOrNull()
        }
    }

    BackHandler { confirmDiscard = true }

    Box(modifier = Modifier.fillMaxSize().background(Tokens.Surface0)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { confirmDiscard = true }) {
                    Icon(Icons.Filled.Close, contentDescription = t.close, tint = Tokens.TextPrimary)
                }

                FinishToggle(
                    value = vm.finish,
                    onChange = vm::updateFinish,
                )

                Text(
                    text = t.pageNumber(vm.pages.size),
                    color = Tokens.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = t.scannedPage,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(color = Tokens.AccentMid)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionButton(Icons.Filled.Refresh, t.retake) { vm.retake() }
                ActionButton(Icons.Filled.RotateRight, t.rotate) { vm.rotate() }
                ActionButton(Icons.Filled.Edit, t.crop) { showCrop = true }
                ActionButton(Icons.Filled.TextFields, t.text) {
                    vm.extractText { text -> extractedText = text }
                }
                ActionButton(
                    icon = Icons.Filled.BrandingWatermark,
                    label = t.watermarkMark,
                    tint = if (vm.watermark) Tokens.AccentMid else Tokens.TextMuted,
                ) { vm.toggleWatermark() }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { vm.addAnother() },
                    modifier = Modifier.weight(1f),
                    brush = Brush.linearGradient(
                        listOf(Tokens.AccentStart, Tokens.AccentMid, Tokens.AccentEnd)
                    ),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Tokens.OnAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(t.add, color = Tokens.OnAccent, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { showSave = true },
                    modifier = Modifier.weight(1f),
                    outline = true,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Tokens.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(t.finish, color = Tokens.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (vm.busy || vm.analyzing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Tokens.AccentMid)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (vm.analyzing) t.extractingText else t.processing,
                        color = Tokens.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    ScanErrorDialog(vm)

    if (showCrop) {
        CropDialog(
            rawPath = page.rawPath,
            quad = page.quad,
            onDismiss = { showCrop = false },
            onApply = { updated ->
                showCrop = false
                vm.applyCrop(updated)
            },
        )
    }

    if (showSave) {
        SaveSheet(
            vm = vm,
            onDismiss = { showSave = false },
            onSaved = { showSave = false },
        )
    }

    val clipboard = LocalClipboardManager.current
    extractedText?.let { text ->
        AlertDialog(
            onDismissRequest = { extractedText = null },
            title = { Text(t.detectedText) },
            text = {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text.ifBlank { t.noTextDetected },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tokens.TextPrimary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { extractedText = null }) { Text(t.close) }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    extractedText = null
                }) { Text(t.copy) }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(t.leaveWithoutSaving) },
            text = { Text(t.lostPages(vm.pages.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.cancel()
                    onDiscard()
                }) { Text(t.leave, color = Tokens.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(t.keepEditing) }
            },
        )
    }
}

@Composable
private fun FinishToggle(value: Finish, onChange: (Finish) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Tokens.Surface1,
        border = androidx.compose.foundation.BorderStroke(1.dp, Tokens.Stroke),
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            listOf(Finish.ORIGINAL to Strings.t.original, Finish.OPTIMIZED to Strings.t.optimize)
                .forEach { (option, label) ->
                val selected = value == option
                val active = true
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Tokens.AccentMid else Color.Transparent)
                        .clickable(enabled = active) { onChange(option) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = label,
                        color = when {
                            selected -> Tokens.OnAccent
                            active -> Tokens.TextPrimary
                            else -> Tokens.TextMuted
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Tokens.TextPrimary,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Tokens.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    outline: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .then(
                when {
                    brush != null -> Modifier.background(brush)
                    outline -> Modifier
                        .background(Tokens.Surface1)
                        .border(1.dp, Tokens.Stroke, shape)
                    else -> Modifier.background(Tokens.Surface1)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun CropDialog(
    rawPath: String?,
    quad: List<Point>?,
    onDismiss: () -> Unit,
    onApply: (List<Point>) -> Unit,
) {
    if (rawPath == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val bitmap by produceState<android.graphics.Bitmap?>(null, rawPath) {
        value = withContext(Dispatchers.IO) {
            runCatching { Images.decodeUpright(File(rawPath), maxSide = 1600) }.getOrNull()
        }
    }

    var points by remember {
        mutableStateOf(
            quad ?: listOf(
                Point(0.02f, 0.02f),
                Point(0.98f, 0.02f),
                Point(0.98f, 0.98f),
                Point(0.02f, 0.98f),
            )
        )
    }
    var handle by remember { mutableStateOf<CropHandle?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var crossed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Tokens.Surface0)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                Text(
                    Strings.t.crop,
                    color = Tokens.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    Strings.t.cropHint,
                    color = Tokens.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val bmp = bitmap
                    if (bmp == null) {
                        CircularProgressIndicator(color = Tokens.AccentMid)
                    } else {
                        val imageAspect = bmp.width.toFloat() / bmp.height
                        val boxAspect = maxWidth.value / maxHeight.value
                        val drawWidth = if (boxAspect > imageAspect) maxHeight.value * imageAspect else maxWidth.value
                        val drawHeight = if (boxAspect > imageAspect) maxHeight.value else maxWidth.value / imageAspect
                        val offsetX = (maxWidth.value - drawWidth) / 2f
                        val offsetY = (maxHeight.value - drawHeight) / 2f

                        val localDensity = LocalDensity.current
                        val ox = with(localDensity) { offsetX.dp.toPx() }
                        val oy = with(localDensity) { offsetY.dp.toPx() }
                        val dw = with(localDensity) { drawWidth.dp.toPx() }
                        val dh = with(localDensity) { drawHeight.dp.toPx() }
                        val cornerRadius = with(localDensity) { 24.dp.toPx() }
                        val sideTolerance = with(localDensity) { 18.dp.toPx() }
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bmp, dw, dh, ox, oy) {
                                    fun cornerPixels(): List<Point> = points.map {
                                        Point(ox + it.x * dw, oy + it.y * dh)
                                    }
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            finger = start
                                            handle = CropHandles.hitTest(
                                                pointsPx = cornerPixels(),
                                                x = start.x,
                                                y = start.y,
                                                cornerRadius = cornerRadius,
                                                sideTolerance = sideTolerance,
                                            )
                                            crossed = false
                                        },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val grabbed = handle
                                            if (grabbed != null && dw > 0f && dh > 0f) {
                                                val dx = drag.x / dw
                                                val dy = drag.y / dh
                                                points = when (grabbed) {
                                                    is CropHandle.Corner ->
                                                        CropHandles.moveCorner(points, grabbed.index, dx, dy)
                                                    is CropHandle.Side ->
                                                        CropHandles.moveSide(points, grabbed.index, dx, dy)
                                                }
                                                finger += drag
                                            }
                                        },
                                        onDragEnd = { handle = null },
                                        onDragCancel = { handle = null },
                                    )
                                },
                        ) {
                            val quadPath = Path()
                            points.forEachIndexed { index, point ->
                                val x = ox + point.x * dw
                                val y = oy + point.y * dh
                                if (index == 0) quadPath.moveTo(x, y) else quadPath.lineTo(x, y)
                            }
                            quadPath.close()

                            drawIntoCanvas { canvas ->
                                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
                                drawRect(Color.Black.copy(alpha = 0.5f))
                                canvas.drawPath(quadPath, Paint().apply { blendMode = BlendMode.Clear })
                                canvas.restore()
                            }
                            drawPath(
                                path = quadPath,
                                color = Tokens.AccentMid,
                                style = Stroke(width = 2.dp.toPx()),
                            )

                            // Side handles: a small grip in the middle of each edge.
                            CropHandles.sideMidpoints(points).forEachIndexed { index, mid ->
                                val x = ox + mid.x * dw
                                val y = oy + mid.y * dh
                                val active = (handle as? CropHandle.Side)?.index == index
                                drawCircle(
                                    color = if (active) Tokens.AccentMid else Color.White,
                                    radius = 5.dp.toPx(),
                                    center = Offset(x, y),
                                )
                                drawCircle(
                                    color = Tokens.AccentMid,
                                    radius = 5.dp.toPx(),
                                    center = Offset(x, y),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }

                            // Corner handles.
                            points.forEachIndexed { index, point ->
                                val x = ox + point.x * dw
                                val y = oy + point.y * dh
                                val active = (handle as? CropHandle.Corner)?.index == index
                                drawCircle(
                                    color = if (active) Tokens.AccentMid else Color.White,
                                    radius = 9.dp.toPx(),
                                    center = Offset(x, y),
                                )
                                drawCircle(
                                    color = Tokens.AccentMid,
                                    radius = 9.dp.toPx(),
                                    center = Offset(x, y),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }

                            if (handle != null) {
                                drawCropMagnifier(
                                    image = imageBitmap,
                                    source = bmp,
                                    finger = finger,
                                    originX = ox,
                                    originY = oy,
                                    drawWidth = dw,
                                    drawHeight = dh,
                                )
                            }
                        }
                    }
                }

                if (crossed) {
                    Text(
                        Strings.t.cropCrossed,
                        color = Tokens.Danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), outline = true) {
                        Text(Strings.t.cancel, color = Tokens.TextPrimary)
                    }
                    Button(
                        onClick = {
                            if (Quads.isConvex(points)) onApply(points) else crossed = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(Strings.t.apply, color = Tokens.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Loupe under the drag: while a corner or a side is being moved, the finger
 * covers the exact pixel being decided, so the editor repeats the area around
 * it above the finger at 2.5x. It is drawn on the same canvas as the crop, so
 * it always tracks the finger without recomposing anything.
 */
private fun DrawScope.drawCropMagnifier(
    image: ImageBitmap,
    source: android.graphics.Bitmap,
    finger: Offset,
    originX: Float,
    originY: Float,
    drawWidth: Float,
    drawHeight: Float,
) {
    val radius = 60.dp.toPx()
    val margin = radius + 6.dp.toPx()
    val center = Offset(
        x = finger.x.coerceIn(margin, (size.width - margin).coerceAtLeast(margin)),
        y = (finger.y - 104.dp.toPx()).coerceIn(margin, (size.height - margin).coerceAtLeast(margin)),
    )
    val scale = drawWidth / source.width
    if (scale <= 0f || drawHeight <= 0f) return

    val zoom = 2.5f
    val halfView = radius / zoom
    val viewX = (finger.x - originX).coerceIn(halfView, (drawWidth - halfView).coerceAtLeast(halfView))
    val viewY = (finger.y - originY).coerceIn(halfView, (drawHeight - halfView).coerceAtLeast(halfView))

    val sourceHalf = halfView / scale
    val sourceSize = (sourceHalf * 2f).roundToInt().coerceIn(8, source.width)
    val sourceLeft = (viewX / scale - sourceHalf).roundToInt()
        .coerceIn(0, (source.width - sourceSize).coerceAtLeast(0))
    val sourceTop = (viewY / scale - sourceHalf).roundToInt()
        .coerceIn(0, (source.height - sourceSize).coerceAtLeast(0))

    val box = Rect(
        offset = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
    )
    val circle = Path().apply { addOval(box) }

    drawCircle(
        color = Color.Black.copy(alpha = 0.35f),
        radius = radius + 2.dp.toPx(),
        center = center,
    )
    clipPath(circle) {
        drawRect(
            color = Color.White,
            topLeft = box.topLeft,
            size = box.size,
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(sourceLeft, sourceTop),
            srcSize = IntSize(sourceSize, sourceSize),
            dstOffset = IntOffset(box.left.roundToInt(), box.top.roundToInt()),
            dstSize = IntSize(box.width.roundToInt(), box.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
    drawCircle(
        color = Tokens.AccentMid,
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
    val tick = 7.dp.toPx()
    drawLine(
        color = Tokens.AccentMid,
        start = Offset(center.x - tick, center.y),
        end = Offset(center.x + tick, center.y),
        strokeWidth = 1.5.dp.toPx(),
    )
    drawLine(
        color = Tokens.AccentMid,
        start = Offset(center.x, center.y - tick),
        end = Offset(center.x, center.y + tick),
        strokeWidth = 1.5.dp.toPx(),
    )
}









