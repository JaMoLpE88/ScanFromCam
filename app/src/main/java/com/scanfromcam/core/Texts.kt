package com.scanfromcam.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Every user-visible string of the app. Both languages must fill in every
 * member, so a forgotten translation is a compile error, not a stray Spanish
 * word on an English screen.
 */
interface Texts {
    val locale: Locale

    // Shared actions.
    val back: String
    val close: String
    val cancel: String
    val save: String
    val delete: String
    val settingsTitle: String
    val somethingWrong: String
    val gotIt: String
    val loading: String
    val processing: String

    // Counts and dynamic lines.
    fun pageCount(n: Int): String
    fun documentCount(n: Int): String
    fun selectedCount(n: Int): String
    fun lostPages(n: Int): String
    fun pageNumber(n: Int): String
    fun framedCountdown(seconds: Int): String
    fun recognizingPage(index: Int, total: Int): String

    val imageFormat: String

    // Library.
    val library: String
    val libraryError: String
    val noScansTitle: String
    val noScansHint: String
    val storedLocally: String
    val scan: String
    val now: String
    val shareSelected: String
    val deleteSelected: String
    val deleteSelectedHint: String
    val shareFailed: String

    // Folders and moving documents.
    val newFolder: String
    val cut: String
    val paste: String
    val gridView: String
    val listView: String
    val folderEmpty: String
    val deleteFolder: String
    val deleteFolderHint: String
    val folderCreateFailed: String
    val folderDeleteFailed: String
    val pasteFailed: String

    // App lock.
    val lockSection: String
    val appLockToggle: String
    val appLockHint: String
    val lockNeedsDeviceSecurity: String
    val lockedApp: String
    val lockedAppHint: String
    val unlockApp: String
    val unlockAppTitle: String

    // Format chooser.
    val formatTitle: String
    val formatHint: String
    val imageFormatHint: String
    val pdfFormatHint: String

    // Capture.
    val captureFailed: String
    val cameraFailed: String
    val cameraNeeded: String
    val cameraNeededHint: String
    val grantPermission: String
    val openSettings: String
    val finish: String
    val findEdges: String
    val moveCloser: String
    val holdStill: String
    val capturing: String
    val adjustDocument: String
    val discardScan: String
    val discard: String
    val keepScanning: String

    // Editor.
    val scannedPage: String
    val retake: String
    val rotate: String
    val crop: String
    val text: String
    val watermarkMark: String
    val add: String
    val extractingText: String
    val detectedText: String
    val noTextDetected: String
    val copy: String
    val leaveWithoutSaving: String
    val leave: String
    val keepEditing: String
    val original: String
    val optimize: String
    val cropHint: String
    val cropCrossed: String
    val apply: String

    // Saving.
    val saveDocument: String
    val name: String
    val pdfWithText: String
    val saving: String
    val generatingPdf: String
    val defaultDocumentPrefix: String

    // Flow failures.
    val optimizeFailed: String
    val captureProcessFailed: String
    val cropFailed: String
    val extractFailed: String
    val saveFailed: String

    // Viewer.
    val options: String
    val share: String
    val shareDocument: String
    val nothingToShare: String
    val rename: String
    val viewText: String
    val noPages: String
    val openPdf: String
    val extractedText: String
    val deleteDocument: String
    val deleteDocumentHint: String
    val renameFailed: String
    val deleteFailed: String

    // Settings.
    val language: String
    val sound: String
    val beepFramed: String
    val beepFramedHint: String
    val shutterSound: String
    val shutterSoundHint: String
    val camera: String
    val autoCapture: String
    val autoCaptureHint: String
    val framingDelay: String
    val framingDelayHint: String
    val watermark: String
    val watermarkEnabled: String
    val watermarkHint: String
    val watermarkText: String
    val watermarkTextHint: String
    val about: String
    val web: String
    val repository: String

    // Updates.
    val updates: String
    val updateCurrent: String
    val updateCheck: String
    val updateChecking: String
    val updateUpToDate: String
    val updateAvailable: String
    val updateConsentTitle: String
    val updateConsentBody: String
    val updateAllow: String
    val updateDeny: String
    val updateDownload: String
    val updateLater: String
    val updateDownloading: String
    val updateReady: String
    val updateReadyHint: String
    val updateInstall: String
    val updateDelete: String
    val updatePermissionTitle: String
    val updatePermissionBody: String
    val updatePermissionLegacy: String
    val updatePermissionAction: String
    val updateAuto: String
    val updateAutoHint: String
    val updateDisabled: String
    val updateNotes: String
    val updateRetry: String
    val updateErrorNetwork: String
    val updateErrorRate: String
    val updateErrorIntegrity: String
    val updateErrorStorage: String
    val updateErrorDevice: String
    fun updateSize(text: String): String
}

/** One readable line per update failure, in the current language. */
fun Failure.message(t: Texts): String = when (this) {
    Failure.Network -> t.updateErrorNetwork
    Failure.RateLimited -> t.updateErrorRate
    Failure.Integrity -> t.updateErrorIntegrity
    Failure.Storage -> t.updateErrorStorage
    Failure.NoAsset -> t.updateErrorDevice
}

object Spanish : Texts {
    override val locale: Locale = Locale.forLanguageTag("es-ES")

    override val back = "Volver"
    override val close = "Cerrar"
    override val cancel = "Cancelar"
    override val save = "Guardar"
    override val delete = "Borrar"
    override val settingsTitle = "Ajustes"
    override val somethingWrong = "Algo ha ido mal"
    override val gotIt = "Entendido"
    override val loading = "Cargando…"
    override val processing = "Procesando…"

    override fun pageCount(n: Int) = if (n == 1) "1 página" else "$n páginas"
    override fun documentCount(n: Int) = if (n == 1) "1 documento" else "$n documentos"
    override fun selectedCount(n: Int) = if (n == 1) "1 seleccionado" else "$n seleccionados"
    override fun lostPages(n: Int) = "Se perderá el documento con ${pageCount(n)}."
    override fun pageNumber(n: Int) = "Página $n"
    override fun framedCountdown(seconds: Int) = "Documento encuadrado · capturando en ${seconds}s"
    override fun recognizingPage(index: Int, total: Int) = "Reconociendo $index de $total"

    override val imageFormat = "Imagen"

    override val library = "Biblioteca"
    override val libraryError = "No se ha podido leer la biblioteca"
    override val noScansTitle = "Aún no tienes escaneos"
    override val noScansHint = "Pulsa Escanear para convertir tu primer documento en imagen o PDF."
    override val storedLocally = "Los documentos se guardan en este dispositivo."
    override val scan = "Escanear"
    override val now = "ahora"
    override val shareSelected = "Compartir"
    override val deleteSelected = "Borrar seleccionados"
    override val deleteSelectedHint = "Se eliminarán los documentos seleccionados y sus páginas de este dispositivo."
    override val shareFailed = "No se ha podido compartir"

    override val newFolder = "Nueva carpeta"
    override val cut = "Cortar"
    override val paste = "Pegar"
    override val gridView = "Vista de miniaturas"
    override val listView = "Vista de lista"
    override val folderEmpty = "Carpeta vacía"
    override val deleteFolder = "¿Borrar carpeta?"
    override val deleteFolderHint = "Se borrarán la carpeta y todos sus documentos de este dispositivo."
    override val folderCreateFailed = "No se ha podido crear la carpeta"
    override val folderDeleteFailed = "No se ha podido borrar la carpeta"
    override val pasteFailed = "No se han podido pegar los documentos"

    override val lockedApp = "ScanFromCam está bloqueada"
    override val lockedAppHint = "Usa tu huella o el PIN, patrón o contraseña del móvil."
    override val unlockApp = "Desbloquear"
    override val unlockAppTitle = "Desbloquea ScanFromCam"
    override val lockSection = "Bloqueo"
    override val appLockToggle = "Pedir el bloqueo del móvil"
    override val appLockHint = "Al abrir la app pedirá la huella o el PIN, patrón o contraseña del dispositivo."
    override val lockNeedsDeviceSecurity = "Configura antes un PIN, patrón o huella en el móvil."

    override val formatTitle = "¿Cómo quieres guardarlo?"
    override val formatHint = "Podrás elegir el acabado de cada página después de capturarla."
    override val imageFormatHint = "Un JPG por página"
    override val pdfFormatHint = "Con texto seleccionable"

    override val captureFailed = "No se ha podido capturar"
    override val cameraFailed = "No se ha podido abrir la cámara"
    override val cameraNeeded = "Necesitamos la cámara"
    override val cameraNeededHint = "Sin permiso de cámara no se pueden escanear documentos."
    override val grantPermission = "Conceder permiso"
    override val openSettings = "Abrir ajustes"
    override val finish = "Finalizar"
    override val findEdges = "Busca los bordes del documento"
    override val moveCloser = "Acerca el documento"
    override val holdStill = "Sujeta el móvil quieto"
    override val capturing = "Capturando…"
    override val adjustDocument = "Ajusta el documento"
    override val discardScan = "¿Descartar el escaneo?"
    override val discard = "Descartar"
    override val keepScanning = "Seguir"

    override val scannedPage = "Página escaneada"
    override val retake = "Retomar"
    override val rotate = "Rotar"
    override val crop = "Recortar"
    override val text = "Texto"
    override val watermarkMark = "Marca"
    override val add = "Agregar"
    override val extractingText = "Extrayendo texto…"
    override val detectedText = "Texto detectado"
    override val noTextDetected = "No se ha detectado texto."
    override val copy = "Copiar"
    override val leaveWithoutSaving = "¿Salir sin guardar?"
    override val leave = "Salir"
    override val keepEditing = "Seguir editando"
    override val original = "Original"
    override val optimize = "Optimizar"
    override val cropHint = "Arrastra un vértice para afinar o un lateral para moverlo entero."
    override val cropCrossed = "El recorte se cruza: ajusta los vértices."
    override val apply = "Aplicar"

    override val saveDocument = "Guardar documento"
    override val name = "Nombre"
    override val pdfWithText = "PDF con texto"
    override val saving = "Guardando…"
    override val generatingPdf = "Generando PDF"
    override val defaultDocumentPrefix = "Escaneo"

    override val optimizeFailed = "No se ha podido optimizar la página"
    override val captureProcessFailed = "No se ha podido procesar la captura"
    override val cropFailed = "No se ha podido recortar"
    override val extractFailed = "No se ha podido extraer texto"
    override val saveFailed = "No se ha podido guardar"

    override val options = "Opciones"
    override val share = "Compartir"
    override val shareDocument = "Compartir documento"
    override val nothingToShare = "No hay nada que compartir"
    override val rename = "Renombrar"
    override val viewText = "Ver texto"
    override val noPages = "Sin páginas"
    override val openPdf = "Abrir PDF"
    override val extractedText = "Texto extraído"
    override val deleteDocument = "¿Borrar documento?"
    override val deleteDocumentHint = "Se eliminarán el documento y sus páginas de este dispositivo."
    override val renameFailed = "No se ha podido renombrar"
    override val deleteFailed = "No se ha podido borrar el documento"

    override val language = "Idioma"
    override val sound = "Sonido"
    override val beepFramed = "Beep al encuadrar"
    override val beepFramedHint = "Avisa cuando el documento queda encuadrado."
    override val shutterSound = "Sonido de obturador"
    override val shutterSoundHint = "Suena al tomar la foto."
    override val camera = "Cámara"
    override val autoCapture = "Auto-disparo"
    override val autoCaptureHint = "Captura sola cuando el documento está quieto y encuadrado."
    override val framingDelay = "Espera del encuadre"
    override val framingDelayHint = "Segundos que el documento debe quedarse encuadrado antes de disparar."
    override val watermark = "Marca de agua"
    override val watermarkEnabled = "Activar marca de agua"
    override val watermarkHint = "Repite un texto en diagonal sobre cada imagen y PDF guardado."
    override val watermarkText = "Texto de la marca"
    override val watermarkTextHint = "Cada escaneo puede activarla o desactivarla desde el editor."
    override val about = "Acerca de"
    override val web = "Web"
    override val repository = "Repositorio"

    override val updates = "Actualizaciones"
    override val updateCurrent = "Versión actual"
    override val updateCheck = "Buscar actualizaciones"
    override val updateChecking = "Buscando…"
    override val updateUpToDate = "Tienes la última versión"
    override val updateAvailable = "Nueva versión"
    override val updateConsentTitle = "¿Buscar actualizaciones?"
    override val updateConsentBody =
        "ScanFromCam solo se conecta a github.com para ver si hay una versión nueva. " +
            "No envía nada tuyo y nada más sale del móvil. Si dices que no, la app no " +
            "volverá a usar la red."
    override val updateAllow = "Permitir comprobaciones"
    override val updateDeny = "Seguir sin conexión"
    override val updateDownload = "Descargar y actualizar"
    override val updateLater = "Ahora no"
    override val updateDownloading = "Descargando la actualización…"
    override val updateReady = "Lista para instalar"
    override val updateReadyHint = "El APK se ha descargado y verificado. Android te pedirá confirmar la instalación."
    override val updateInstall = "Instalar"
    override val updateDelete = "Borrar la descarga"
    override val updatePermissionTitle = "Android necesita tu permiso"
    override val updatePermissionBody =
        "Para instalar la actualización, permite a ScanFromCam instalar apps desconocidas " +
            "en los ajustes del móvil. Solo se usa para esto."
    override val updatePermissionLegacy = "Actívalo en Ajustes → Seguridad → Fuentes desconocidas."
    override val updatePermissionAction = "Permitir instalación"
    override val updateAuto = "Descargar sin preguntar"
    override val updateAutoHint = "Baja la versión nueva en segundo plano y solo pregunta al instalar."
    override val updateDisabled = "Desactivadas"
    override val updateNotes = "Novedades"
    override val updateRetry = "Reintentar"
    override val updateErrorNetwork = "No se ha podido conectar con GitHub"
    override val updateErrorRate = "GitHub ha limitado las peticiones. Prueba más tarde"
    override val updateErrorIntegrity = "El APK descargado no coincide con el publicado"
    override val updateErrorStorage = "No hay espacio suficiente para la descarga"
    override val updateErrorDevice = "No hay una versión publicada para este dispositivo"
    override fun updateSize(text: String) = "$text MB"
}

object English : Texts {
    override val locale: Locale = Locale.US

    override val back = "Back"
    override val close = "Close"
    override val cancel = "Cancel"
    override val save = "Save"
    override val delete = "Delete"
    override val settingsTitle = "Settings"
    override val somethingWrong = "Something went wrong"
    override val gotIt = "Got it"
    override val loading = "Loading…"
    override val processing = "Processing…"

    override fun pageCount(n: Int) = if (n == 1) "1 page" else "$n pages"
    override fun documentCount(n: Int) = if (n == 1) "1 document" else "$n documents"
    override fun selectedCount(n: Int) = if (n == 1) "1 selected" else "$n selected"
    override fun lostPages(n: Int) = "The document with ${pageCount(n)} will be lost."
    override fun pageNumber(n: Int) = "Page $n"
    override fun framedCountdown(seconds: Int) = "Document framed · capturing in ${seconds}s"
    override fun recognizingPage(index: Int, total: Int) = "Recognizing $index of $total"

    override val imageFormat = "Image"

    override val library = "Library"
    override val libraryError = "Couldn't read the library"
    override val noScansTitle = "No scans yet"
    override val noScansHint = "Tap Scan to turn your first document into an image or PDF."
    override val storedLocally = "Documents are stored on this device."
    override val scan = "Scan"
    override val now = "now"
    override val shareSelected = "Share"
    override val deleteSelected = "Delete selected"
    override val deleteSelectedHint = "The selected documents and their pages will be deleted from this device."
    override val shareFailed = "Couldn't share"

    override val newFolder = "New folder"
    override val cut = "Cut"
    override val paste = "Paste"
    override val gridView = "Thumbnail view"
    override val listView = "List view"
    override val folderEmpty = "Empty folder"
    override val deleteFolder = "Delete folder?"
    override val deleteFolderHint = "The folder and all its documents will be deleted from this device."
    override val folderCreateFailed = "Couldn't create the folder"
    override val folderDeleteFailed = "Couldn't delete the folder"
    override val pasteFailed = "Couldn't paste the documents"

    override val lockedApp = "ScanFromCam is locked"
    override val lockedAppHint = "Use your fingerprint or the phone's PIN, pattern or password."
    override val unlockApp = "Unlock"
    override val unlockAppTitle = "Unlock ScanFromCam"
    override val lockSection = "Lock"
    override val appLockToggle = "Ask for the phone lock"
    override val appLockHint = "The app will ask for the device fingerprint or PIN, pattern or password when opened."
    override val lockNeedsDeviceSecurity = "Set up a PIN, pattern or fingerprint on the phone first."

    override val formatTitle = "How do you want to save it?"
    override val formatHint = "You can pick the finish of each page after capturing it."
    override val imageFormatHint = "One JPG per page"
    override val pdfFormatHint = "With selectable text"

    override val captureFailed = "Couldn't capture the photo"
    override val cameraFailed = "Couldn't open the camera"
    override val cameraNeeded = "We need the camera"
    override val cameraNeededHint = "Camera permission is required to scan documents."
    override val grantPermission = "Grant permission"
    override val openSettings = "Open settings"
    override val finish = "Finish"
    override val findEdges = "Find the document edges"
    override val moveCloser = "Move the document closer"
    override val holdStill = "Hold the phone still"
    override val capturing = "Capturing…"
    override val adjustDocument = "Adjust the document"
    override val discardScan = "Discard the scan?"
    override val discard = "Discard"
    override val keepScanning = "Keep scanning"

    override val scannedPage = "Scanned page"
    override val retake = "Retake"
    override val rotate = "Rotate"
    override val crop = "Crop"
    override val text = "Text"
    override val watermarkMark = "Mark"
    override val add = "Add"
    override val extractingText = "Extracting text…"
    override val detectedText = "Detected text"
    override val noTextDetected = "No text detected."
    override val copy = "Copy"
    override val leaveWithoutSaving = "Leave without saving?"
    override val leave = "Leave"
    override val keepEditing = "Keep editing"
    override val original = "Original"
    override val optimize = "Optimize"
    override val cropHint = "Drag a corner to fine-tune it or a side to move the whole edge."
    override val cropCrossed = "The crop crosses itself: adjust the corners."
    override val apply = "Apply"

    override val saveDocument = "Save document"
    override val name = "Name"
    override val pdfWithText = "PDF with text"
    override val saving = "Saving…"
    override val generatingPdf = "Generating PDF"
    override val defaultDocumentPrefix = "Scan"

    override val optimizeFailed = "Couldn't optimize the page"
    override val captureProcessFailed = "Couldn't process the capture"
    override val cropFailed = "Couldn't crop the page"
    override val extractFailed = "Couldn't extract text"
    override val saveFailed = "Couldn't save"

    override val options = "Options"
    override val share = "Share"
    override val shareDocument = "Share document"
    override val nothingToShare = "Nothing to share"
    override val rename = "Rename"
    override val viewText = "View text"
    override val noPages = "No pages"
    override val openPdf = "Open PDF"
    override val extractedText = "Extracted text"
    override val deleteDocument = "Delete document?"
    override val deleteDocumentHint = "The document and its pages will be deleted from this device."
    override val renameFailed = "Couldn't rename"
    override val deleteFailed = "Couldn't delete the document"

    override val language = "Language"
    override val sound = "Sound"
    override val beepFramed = "Beep when framed"
    override val beepFramedHint = "Sounds when the document is framed."
    override val shutterSound = "Shutter sound"
    override val shutterSoundHint = "Plays when taking the photo."
    override val camera = "Camera"
    override val autoCapture = "Auto capture"
    override val autoCaptureHint = "Captures by itself when the document is still and framed."
    override val framingDelay = "Framing delay"
    override val framingDelayHint = "Seconds the document must stay framed before shooting."
    override val watermark = "Watermark"
    override val watermarkEnabled = "Enable watermark"
    override val watermarkHint = "Repeats diagonal text over every saved image and PDF."
    override val watermarkText = "Watermark text"
    override val watermarkTextHint = "Each scan can turn it on or off from the editor."
    override val about = "About"
    override val web = "Web"
    override val repository = "Repository"

    override val updates = "Updates"
    override val updateCurrent = "Current version"
    override val updateCheck = "Check for updates"
    override val updateChecking = "Checking…"
    override val updateUpToDate = "You're on the latest version"
    override val updateAvailable = "New version"
    override val updateConsentTitle = "Look for updates?"
    override val updateConsentBody =
        "ScanFromCam only connects to github.com to see if there is a new version. " +
            "It sends nothing of yours and nothing else leaves the phone. If you say no, " +
            "the app will not use the network again."
    override val updateAllow = "Allow checks"
    override val updateDeny = "Stay offline"
    override val updateDownload = "Download and update"
    override val updateLater = "Not now"
    override val updateDownloading = "Downloading the update…"
    override val updateReady = "Ready to install"
    override val updateReadyHint = "The APK was downloaded and verified. Android will ask you to confirm the install."
    override val updateInstall = "Install"
    override val updateDelete = "Delete the download"
    override val updatePermissionTitle = "Android needs your permission"
    override val updatePermissionBody =
        "To install the update, let ScanFromCam install unknown apps in the phone settings. " +
            "It is only used for this."
    override val updatePermissionLegacy = "Turn it on in Settings → Security → Unknown sources."
    override val updatePermissionAction = "Allow installs"
    override val updateAuto = "Download without asking"
    override val updateAutoHint = "Fetches the new version in the background and only asks when installing."
    override val updateDisabled = "Off"
    override val updateNotes = "What's new"
    override val updateRetry = "Retry"
    override val updateErrorNetwork = "Couldn't reach GitHub"
    override val updateErrorRate = "GitHub is rate limiting the requests. Try later"
    override val updateErrorIntegrity = "The downloaded APK doesn't match the published one"
    override val updateErrorStorage = "Not enough space for the download"
    override val updateErrorDevice = "No published version for this device"
    override fun updateSize(text: String) = "$text MB"
}

/**
 * Current language of the app. Compose state, so every screen using [t]
 * recomposes the moment the user changes it in Settings.
 */
object Strings {
    var lang by mutableStateOf(Lang.EN)

    val t: Texts
        get() = if (lang == Lang.ES) Spanish else English
}
