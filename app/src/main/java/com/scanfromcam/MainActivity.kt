package com.scanfromcam

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.scanfromcam.di.AppContainer
import com.scanfromcam.feature.capture.CaptureScreen
import com.scanfromcam.feature.editor.EditorScreen
import com.scanfromcam.feature.format.FormatSheet
import com.scanfromcam.feature.flow.ScanFlowViewModel
import com.scanfromcam.feature.flow.ScanPhase
import com.scanfromcam.feature.library.LibraryScreen
import com.scanfromcam.feature.library.LibraryViewModel
import com.scanfromcam.feature.settings.SettingsScreen
import com.scanfromcam.feature.viewer.ViewerScreen
import com.scanfromcam.feature.viewer.ViewerViewModel
import com.scanfromcam.ui.AppLockGate
import com.scanfromcam.ui.theme.ScanFromCamTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanFromCamTheme {
                val container = (application as ScanFromCamApp).container
                // Nothing is shown until the device credential clears the app lock.
                AppLockGate(container.appLock) { AppRoot(container) }
            }
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    val flowFactory = remember(container) {
        viewModelFactory {
            initializer { ScanFlowViewModel(container.repository, container.ocr, container.settings) }
        }
    }
    val flow: ScanFlowViewModel = viewModel(factory = flowFactory)

    val libraryFactory = remember(container) {
        viewModelFactory { initializer { LibraryViewModel(container.repository) } }
    }
    val library: LibraryViewModel = viewModel(factory = libraryFactory)

    // Leaving the app locks it again: locked folders and documents ask for the PIN.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) container.appLock.lock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var viewerDocId by remember { mutableStateOf<String?>(null) }
    var showFormat by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(flow.phase) {
        if (flow.phase == null) library.refresh()
    }

    val phase = flow.phase
    val docId = viewerDocId

    Box(modifier = Modifier.fillMaxSize()) {
        when {
        phase == ScanPhase.CAMERA -> CaptureScreen(vm = flow, settings = container.settings, onExit = {})
        phase == ScanPhase.EDITOR -> EditorScreen(vm = flow, onDiscard = {})
        docId != null -> {
            val factory = remember(docId) {
                viewModelFactory { initializer { ViewerViewModel(container.repository, docId) } }
            }
            val viewer: ViewerViewModel = viewModel(key = "viewer-$docId", factory = factory)
            ViewerScreen(
                vm = viewer,
                onBack = {
                    viewerDocId = null
                    library.refresh()
                },
            )
        }
        else -> LibraryScreen(
            vm = library,
            settings = container.settings,
            onScan = { showFormat = true },
            onOpen = { viewerDocId = it },
            onSettings = { showSettings = true },
        )
    }

        if (showSettings) {
            SettingsScreen(settings = container.settings, onBack = { showSettings = false })
        }
        if (showFormat) {
            FormatSheet(
                onPick = { format ->
                    showFormat = false
                    // A scan started inside a folder is saved into that folder.
                    flow.start(format, library.folderId)
                },
                onDismiss = { showFormat = false },
            )
        }
    }
}

