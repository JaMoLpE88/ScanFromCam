package com.scanfromcam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.scanfromcam.ui.theme.ScanFromCamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanFromCamTheme {
                val container = (application as ScanFromCamApp).container
                AppRoot(container)
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
                    flow.start(format)
                },
                onDismiss = { showFormat = false },
            )
        }
    }
}

