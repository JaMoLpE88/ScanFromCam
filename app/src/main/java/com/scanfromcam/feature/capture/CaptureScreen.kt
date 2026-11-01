package com.scanfromcam.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scanfromcam.core.AppSettings
import com.scanfromcam.core.SoundFeedback
import com.scanfromcam.core.Strings
import com.scanfromcam.data.CameraWork
import com.scanfromcam.domain.model.Point
import com.scanfromcam.feature.capture.detection.AutoCaptureGate
import com.scanfromcam.feature.capture.detection.StabilityTracker
import com.scanfromcam.feature.capture.detection.FrameDetector
import com.scanfromcam.feature.capture.detection.QuadSmoother
import com.scanfromcam.feature.editor.SaveSheet
import com.scanfromcam.feature.editor.ScanErrorDialog
import com.scanfromcam.feature.flow.ScanFlowViewModel
import com.scanfromcam.feature.flow.ScanPhase
import com.scanfromcam.ui.theme.Tokens
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val QUAD_VALID_MS = 1_500L
private const val QUAD_FALLBACK_MS = 1_000L


/** The page must cover at least this much of a side to auto capture. */
private const val COVERAGE_MIN = 0.5f

@Composable
fun CaptureScreen(
    vm: ScanFlowViewModel,
    settings: AppSettings,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val t = Strings.t
    
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }


    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    // Preview, analysis and the still photo must share aspect ratio, otherwise
    // the detected quad and the cropped photo cover different regions.
    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .build()
    }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolutionSelector)
            .build()
    }
    val smoother = remember { QuadSmoother() }
    val autoGate = remember { AutoCaptureGate(settings.autoCaptureDelay * 1_000L) }
    val sounds = remember { SoundFeedback(settings) }
    var wasFramed by remember { mutableStateOf(false) }
    val cameraExecutor = CameraWork.executor
    val capturing = remember { AtomicBoolean(false) }

    var quad by remember { mutableStateOf<List<Point>?>(null) }
    var stable by remember { mutableStateOf(false) }
    var covered by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(3f / 4f) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var autoCapture by remember { mutableStateOf(settings.autoCapture) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var frozenFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var countdown by remember { mutableStateOf(0) }
    var showSave by remember { mutableStateOf(false) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val stability = remember { StabilityTracker(context) }
    var deviceStable by remember { mutableStateOf(true) }

    // Border detection and auto capture pause while the phone moves: a frame
    // taken mid-motion is blurred and the preview quad is stale.
    DisposableEffect(Unit) {
        stability.onChange = { stable -> mainHandler.post { deviceStable = stable } }
        stability.start()
        onDispose {
            stability.onChange = null
            stability.stop()
        }
    }

    BackHandler {
        if (vm.canFinish) confirmDiscard = true else { vm.cancel(); onExit() }
    }


    // Drop the frozen frame once processing ended without leaving the camera.
    LaunchedEffect(vm.busy, vm.phase) {
        if (!vm.busy && vm.phase == ScanPhase.CAMERA && frozenFrame != null) {
            kotlinx.coroutines.delay(700)
            if (!vm.busy) frozenFrame = null
        }
    }
    fun captureNow() {
        if (!capturing.compareAndSet(false, true)) return
        frozenFrame = runCatching { previewView.bitmap?.asImageBitmap() }.getOrNull()
        sounds.shutter()

        // Freeze the framing at press time: a slow high-quality capture must not
        // invalidate the quad and fall back to the whole photo.
        val quadAtShutter = smoother.latestIfFresh(QUAD_VALID_MS, SystemClock.elapsedRealtime())
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = image.toJpegBytes()
                    image.close()
                    capturing.set(false)
                    // The screen may already have moved to the editor: never append
                    // a second page that the user did not ask for.
                    if (vm.phase == ScanPhase.CAMERA) {
                        vm.onCaptured(bytes, quadAtShutter)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing.set(false)
                    cameraError = t.captureFailed
                }
            }
        )
    }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        val provider = awaitProvider(context)
        cameraProvider = provider
        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply { setSurfaceProvider(previewView.surfaceProvider) }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                analysis,
            )
        } catch (error: Throwable) {
            cameraError = t.cameraFailed
        }
    }

    DisposableEffect(permissionGranted) {
        var lastAnalysis = 0L
        if (permissionGranted) {
            analysis.setAnalyzer(cameraExecutor) { proxy ->
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAnalysis < 90) return@setAnalyzer
                    lastAnalysis = now
                    aspect = FrameDetector.uprightAspect(proxy)
                    val detected = FrameDetector.detect(proxy)
                    val result = smoother.add(detected, now)
                    mainHandler.post {
                        // Without a detection for a second, fall back to the full frame.
                        val fresh = smoother.latestIfFresh(QUAD_FALLBACK_MS, now)
                        quad = fresh
                        stable = result.isStable && fresh != null

                        if (stable && !wasFramed) {
                            wasFramed = true
                            sounds.documentFramed()
                        } else if (!stable) {
                            wasFramed = false
                        }

                        val isCovered = smoother.covers(COVERAGE_MIN)
                        covered = isCovered
                        val gate = autoGate.update(
                            now = now,
                            framed = stable,
                            autoEnabled = autoCapture,
                            covered = isCovered,
                            deviceStable = deviceStable,
                        )
                        countdown = gate.remainingSeconds
                        if (gate.shouldCapture && !vm.busy && vm.phase == ScanPhase.CAMERA) {
                            countdown = 0
                            captureNow()
                        }
                    }
                } catch (_: Throwable) {
                    // A frame that cannot be analysed is simply skipped.
                } finally {
                    proxy.close()
                }
            }
        }
        onDispose {
            analysis.clearAnalyzer()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            sounds.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        QuadOverlay(
            quad = quad,
            aspect = aspect,
            stable = stable,
            modifier = Modifier.fillMaxSize(),
        )

        if (!permissionGranted) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        t.cameraNeeded,
                        color = Tokens.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t.cameraNeededHint,
                        color = Tokens.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(t.grantPermission, color = Tokens.AccentMid)
                    }
                    TextButton(onClick = { openAppSettings(context) }) {
                        Text(t.openSettings, color = Tokens.TextMuted)
                    }
                }
            }
        }

        val snapshot = frozenFrame
        if (snapshot != null) {
            CaptureTransition(snapshot)
        } else if (vm.busy) {
            ProcessingOverlay()
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { if (vm.canFinish) confirmDiscard = true else { vm.cancel(); onExit() } }) {
                    Icon(Icons.Filled.Close, contentDescription = t.close, tint = Color.White)
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Tokens.Stroke),
                ) {
                    Text(
                        text = t.pageCount(vm.pages.size),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = Tokens.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                TextButton(
                    onClick = { showSave = true },
                    enabled = vm.canFinish,
                ) {
                    Text(
                        t.finish,
                        color = if (vm.canFinish) Tokens.AccentMid else Tokens.TextMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            cameraError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Tokens.Danger, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when {
                        quad == null -> t.findEdges
                        !covered -> t.moveCloser
                        !deviceStable -> t.holdStill
                        stable && countdown > 0 -> t.framedCountdown(countdown)
                        stable -> t.capturing
                        else -> t.adjustDocument
                    },
                    color = if (stable) Tokens.Success else Tokens.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = {
                        autoCapture = !autoCapture
                        settings.autoCapture = autoCapture
                    }) {
                        Text(
                            if (autoCapture) "Auto" else "Manual",
                            color = if (autoCapture) Tokens.AccentMid else Tokens.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    ShutterButton(enabled = !vm.busy && permissionGranted) { captureNow() }

                    Spacer(Modifier.size(64.dp))
                }
            }
        }
    }

    if (showSave) {
        SaveSheet(
            vm = vm,
            onDismiss = { showSave = false },
            onSaved = { showSave = false },
        )
    }

    ScanErrorDialog(vm)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(t.discardScan) },
            text = { Text(t.lostPages(vm.pages.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.cancel()
                    onExit()
                }) { Text(t.discard, color = Tokens.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(t.keepScanning) }
            },
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .border(3.dp, if (enabled) Tokens.AccentMid else Tokens.TextMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(listOf(Tokens.AccentStart, Tokens.AccentMid, Tokens.AccentEnd)),
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun QuadOverlay(
    quad: List<Point>?,
    aspect: Float,
    stable: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "quad")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        val content = fitRect(aspect)
        if (quad == null) {
            val inset = 18.dp.toPx()
            drawCorners(
                rect = Rect(
                    content.left + inset,
                    content.top + inset,
                    content.right - inset,
                    content.bottom - inset,
                ),
                color = Color.White.copy(alpha = 0.28f),
            )
            return@Canvas
        }

        val path = Path()
        quad.forEachIndexed { index, point ->
            val x = content.left + point.x * content.width
            val y = content.top + point.y * content.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
            drawRect(Color.Black.copy(alpha = 0.55f))
            canvas.drawPath(path, Paint().apply { blendMode = BlendMode.Clear })
            canvas.restore()
        }

        drawPath(
            path = path,
            color = if (stable) Tokens.Success else Color.White.copy(alpha = 0.85f),
            style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawPath(
            path = path,
            color = if (stable) Tokens.Success.copy(alpha = pulse) else Color.White.copy(alpha = 0.2f),
            style = Stroke(width = 8.dp.toPx()),
        )
    }
}

/** Same fitting as PreviewView.FIT_CENTER, so overlay coordinates match the video. */
private fun DrawScope.fitRect(aspect: Float): Rect {
    return if (size.width / size.height > aspect) {
        val height = size.height
        val width = height * aspect
        val left = (size.width - width) / 2f
        Rect(left, 0f, left + width, height)
    } else {
        val width = size.width
        val height = width / aspect
        val top = (size.height - height) / 2f
        Rect(0f, top, width, top + height)
    }
}

private fun DrawScope.drawCorners(rect: Rect, color: Color) {
    val arm = minOf(rect.width, rect.height) * 0.08f
    val stroke = 3.dp.toPx()
    val points = listOf(
        rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft,
    )
    val directions = listOf(
        Offset(1f, 1f), Offset(-1f, 1f), Offset(-1f, -1f), Offset(1f, -1f),
    )
    points.forEachIndexed { index, corner ->
        val dir = directions[index]
        drawLine(color, corner, Offset(corner.x + dir.x * arm, corner.y), stroke)
        drawLine(color, corner, Offset(corner.x, corner.y + dir.y * arm), stroke)
    }
}

/**
 * Freezes the last preview frame, sweeps it with the accent line and shrinks
 * it away while the page is being processed.
 */
@Composable
private fun CaptureTransition(snapshot: ImageBitmap) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(snapshot) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        )
    }
    val p = progress.value
    val scale = 1f - 0.6f * p
    val alpha = 1f - 0.75f * p

    // Opaque enough to hide the live preview, so the shrink reads as one photo.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f + 0.22f * p))) {
        Image(
            bitmap = snapshot,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    translationY = size.height * 0.12f * p
                },
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweep = (p / 0.55f).coerceIn(0f, 1f)
            val y = size.height * sweep
            drawLine(
                color = Tokens.AccentMid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 3.dp.toPx(),
            )
        }

        if (p >= 1f) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Tokens.AccentMid)
                Spacer(Modifier.height(12.dp))
                Text(
                    Strings.t.processing,
                    color = Tokens.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    val transition = rememberInfiniteTransition(label = "sweep")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "sweepProgress",
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * progress
            val bandHeight = 120.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Tokens.AccentMid.copy(alpha = 0f),
                        Tokens.AccentMid.copy(alpha = 0.55f),
                        Tokens.AccentMid.copy(alpha = 0f),
                    ),
                    startY = y - bandHeight,
                    endY = y + bandHeight,
                ),
                topLeft = Offset(0f, y - bandHeight),
                size = androidx.compose.ui.geometry.Size(size.width, bandHeight * 2),
            )
            drawLine(
                color = Tokens.AccentMid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Strings.t.processing,
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private suspend fun awaitProvider(context: android.content.Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (t: Throwable) {
                    continuation.cancel(t)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }


private fun openAppSettings(context: android.content.Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes.first().buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.rewind()
    buffer.get(bytes)
    return bytes
}




















