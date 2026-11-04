package com.scanfromcam.feature.flow

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanfromcam.core.AppSettings
import com.scanfromcam.core.Images
import com.scanfromcam.core.Strings
import com.scanfromcam.core.Watermark
import com.scanfromcam.core.sanitizeName
import com.scanfromcam.data.Cv
import com.scanfromcam.data.OcrResult
import com.scanfromcam.data.OcrService
import com.scanfromcam.data.PdfPageSource
import com.scanfromcam.data.PdfWriter
import com.scanfromcam.domain.geometry.Quads
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.Finish
import com.scanfromcam.domain.model.PageDraft
import com.scanfromcam.domain.model.Point
import com.scanfromcam.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

enum class ScanPhase { CAMERA, EDITOR }

/**
 * Owns the whole scan session: pages captured so far, the current editor state
 * and the save pipeline. The session can be finished at any moment once it has
 * at least one page, even right after requesting another capture.
 */
class ScanFlowViewModel(
    private val repository: DocumentRepository,
    private val ocr: OcrService,
    private val settings: AppSettings,
) : ViewModel() {

    var format by mutableStateOf(DocFormat.PDF)
        private set

    val pages = mutableStateListOf<PageDraft>()

    var phase by mutableStateOf<ScanPhase?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var finish by mutableStateOf(Finish.ORIGINAL)
        private set

    var analyzing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    var saveStage by mutableStateOf<String?>(null)
        private set

    /** Per-document watermark switch, seeded from the settings when the scan starts. */
    var watermark by mutableStateOf(false)
        private set

    /** Bumped whenever a page file is rewritten, so the editor reloads it. */
    var revision by mutableStateOf(0)
        private set

    private val ocrResults = mutableMapOf<String, OcrResult>()
    private val tempFiles = mutableListOf<File>()

    val currentPage: PageDraft?
        get() = pages.lastOrNull()

    val canFinish: Boolean
        get() = pages.isNotEmpty()

    /** Folder the next save goes into; null is the library root. */
    private var targetFolderId: String? = null

    fun start(selected: DocFormat, folderId: String? = null) {
        resetSession()
        format = selected
        watermark = settings.watermark
        targetFolderId = folderId
        phase = ScanPhase.CAMERA
    }

    fun toggleWatermark() {
        watermark = !watermark
    }

    fun cancel() {
        resetSession()
        phase = null
    }

    fun addAnother() {
        phase = ScanPhase.CAMERA
    }

    fun retake() {
        if (pages.isNotEmpty()) {
            val removed = pages.removeAt(pages.lastIndex)
            ocrResults.remove(removed.id)
        }
        phase = ScanPhase.CAMERA
    }

    fun updateFinish(value: Finish) {
        finish = value
        if (value == Finish.OPTIMIZED) {
            val page = currentPage ?: return
            if (page.optimizedPath == null) optimizePage(page)
        }
    }

    /** Computes the Optimizar version of the current page the first time it is needed. */
    private fun optimizePage(page: PageDraft) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val path = optimizedFileFor(page)
                if (path != null) {
                    replacePage(page.copy(optimizedPath = path))
                } else {
                    finish = Finish.ORIGINAL
                    error = Strings.t.optimizeFailed
                }
            } catch (t: Throwable) {
                finish = Finish.ORIGINAL
                error = Strings.t.optimizeFailed
            } finally {
                busy = false
            }
        }
    }

    /**
     * Stores an optimised page. A sheet without colour is written in one
     * channel: same picture, smaller file, and the flat look of a scan.
     */
    private fun saveOptimized(bitmap: Bitmap, file: File) {
        if (!Cv.savePageJpeg(bitmap, file, 88)) Images.saveJpeg(bitmap, file, 88)
    }

    /** Renders the Optimizar version of [page] into a temp file, or null if it cannot. */
    private suspend fun optimizedFileFor(page: PageDraft): String? = withContext(Dispatchers.Default) {
        val source = Images.decodeUpright(File(page.originalPath), maxSide = MAX_CAPTURE_SIDE)
            ?: return@withContext null
        try {
            val enhanced = Cv.optimize(source)
            try {
                val target = repository.newTempPageFile()
                saveOptimized(enhanced, target)
                tempFiles.add(target)
                target.absolutePath
            } finally {
                enhanced.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    fun rotate() {
        val page = currentPage ?: return
        // Text boxes belong to the previous orientation: drop them.
        ocrResults.remove(page.id)
        replacePage(page.copy(rotation = (page.rotation + 90) % 360, ocrText = null))
    }

    fun onCaptured(jpeg: ByteArray, quad: List<Point>?) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val draft = withContext(Dispatchers.Default) { buildDraft(jpeg, quad) }
                pages.add(draft)
                phase = ScanPhase.EDITOR
            } catch (t: Throwable) {
                error = Strings.t.captureProcessFailed
            } finally {
                busy = false
            }
        }
    }

    fun applyCrop(newQuad: List<Point>) {
        val page = currentPage ?: return
        val rawPath = page.rawPath ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val applied = withContext(Dispatchers.Default) { recrop(page, rawPath, newQuad) }
                if (applied) {
                    ocrResults.remove(page.id)
                    replacePage(page.copy(quad = newQuad, ocrText = null))
                    revision++
                } else {
                    error = Strings.t.cropFailed
                }
            } catch (t: Throwable) {
                error = Strings.t.cropFailed
            } finally {
                busy = false
            }
        }
    }

    fun extractText(onResult: (String) -> Unit) {
        val page = currentPage ?: return
        if (analyzing) return
        viewModelScope.launch {
            analyzing = true
            try {
                val result = withContext(Dispatchers.Default) {
                    val bitmap = Images.decodeUpright(File(page.activePath(finish)), maxSide = 2200)
                        ?: error("bitmap")
                    try {
                        ocr.recognize(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
                ocrResults[page.id] = result
                replacePage(page.copy(ocrText = result.text))
                onResult(result.text)
            } catch (t: Throwable) {
                error = Strings.t.extractFailed
            } finally {
                analyzing = false
            }
        }
    }

    fun save(name: String, onSaved: (String) -> Unit) {
        if (!canFinish || busy) return
        viewModelScope.launch {
            busy = true
            var createdDocumentId: String? = null
            try {
                val safeName = sanitizeName(name, prefix = Strings.t.defaultDocumentPrefix)
                val document = repository.create(safeName, format, targetFolderId)
                createdDocumentId = document.id

                // Saving right after picking Optimizar must not race the pass:
                // if a page still lacks it, compute it now instead of storing
                // the original with an Optimize badge on screen.
                if (finish == Finish.OPTIMIZED) {
                    for (index in pages.indices) {
                        val page = pages[index]
                        if (page.optimizedPath != null) continue
                        val optimized = optimizedFileFor(page)
                        if (optimized != null) pages[index] = page.copy(optimizedPath = optimized)
                    }
                }

                // Rotation is baked into the stored files, so the viewer, the
                // thumbnail and the PDF all see the page upright.
                val saved = pages.map { page ->
                    val upright = if (page.rotation == 0) page else (bakeRotation(page) ?: page)
                    val marked = if (!watermark) upright else withContext(Dispatchers.Default) { applyWatermark(upright) }
                    marked.forSave(finish)
                }
                for (page in saved) {
                    repository.addPage(document.id, page)
                }

                if (format == DocFormat.PDF) {
                    val sources = ArrayList<PdfPageSource>(saved.size)
                    saved.forEachIndexed { index, page ->
                        saveStage = Strings.t.recognizingPage(index + 1, saved.size)
                        val path = page.activePath(finish)
                        val bitmap = withContext(Dispatchers.Default) {
                            Images.decodeUpright(File(path), maxSide = MAX_CAPTURE_SIDE)
                        }
                        if (bitmap == null) {
                            throw IOException("Unreadable page ${index + 1}")
                        }
                        val recognized = ocrResults[page.id] ?: withContext(Dispatchers.Default) {
                            runCatching { ocr.recognize(bitmap) }.getOrNull()
                        }
                        if (recognized != null) ocrResults[page.id] = recognized
                        bitmap.recycle()
                        sources.add(PdfPageSource(path, recognized))
                    }
                    saveStage = Strings.t.generatingPdf
                    val bytes = withContext(Dispatchers.Default) { PdfWriter.build(sources) }
                    val pdfFile = File(repository.documentDir(document.id), "document.pdf")
                    pdfFile.writeBytes(bytes)
                    repository.setPdfPath(document.id, pdfFile)
                }

                cleanupTemps()
                pages.clear()
                ocrResults.clear()
                phase = null
                onSaved(document.id)
            } catch (t: Throwable) {
                // Never leave a half written document in the library.
                createdDocumentId?.let { id -> runCatching { repository.delete(id) } }
                error = Strings.t.saveFailed
            } finally {
                busy = false
                saveStage = null
            }
        }
    }

    private fun resetSession() {
        cleanupTemps()
        pages.clear()
        ocrResults.clear()
        finish = Finish.ORIGINAL
        error = null
        saveStage = null
        targetFolderId = null
    }

    private fun buildDraft(jpeg: ByteArray, quad: List<Point>?): PageDraft {
        val upright = Images.decodeUpright(jpeg, maxSide = MAX_CAPTURE_SIDE)
            ?: error("No se pudo leer la captura")

        val rawFile = repository.newTempPageFile()
        Images.saveJpeg(upright, rawFile, 95)
        tempFiles.add(rawFile)

        // Detect again on the captured photo: the preview frame and the still can
        // be cropped differently, and that mismatch is what left extra margins.
        // The still is sharper than the preview, so its quad is refined against
        // the real edges before warping.
        val picked = pickQuad(Cv.detectQuad(upright), quad) ?: FULL_FRAME
        val effectiveQuad = runCatching { Cv.refineQuad(upright, picked) }.getOrElse { picked }
        val cropped = runCatching { Cv.warp(upright, effectiveQuad) }
            .getOrElse { Cv.warp(upright, FULL_FRAME) }
        upright.recycle()

        val originalFile = repository.newTempPageFile()
        Images.saveJpeg(cropped, originalFile, 92)
        tempFiles.add(originalFile)

        cropped.recycle()

        // Optimizar is computed on demand: the page must appear immediately
        // after the capture, not wait for the enhancement pass.
        val optimizedPath: String? = null

        return PageDraft(
            id = UUID.randomUUID().toString(),
            rawPath = rawFile.absolutePath,
            originalPath = originalFile.absolutePath,
            optimizedPath = optimizedPath,
            rotation = 0,
            quad = effectiveQuad,
            ocrText = null,
        )
    }

    /**
     * Prefers the still's detection, but never lets the still override what the
     * user framed: if both agree (IoU >= 0.55) the sharper still wins, and when
     * the still sees something else entirely the preview quad is kept.
     */
    private fun pickQuad(fresh: List<Point>?, preview: List<Point>?): List<Point>? {
        if (fresh == null) return preview
        if (preview == null) return fresh
        return if (Quads.iou(fresh, preview) >= 0.55f) fresh else preview
    }

    /** Re-warps the original capture with a new quad, writing both files atomically. */
    private fun recrop(page: PageDraft, rawPath: String, quad: List<Point>): Boolean {
        val upright = Images.decodeUpright(File(rawPath), maxSide = MAX_CAPTURE_SIDE) ?: return false
        val cropped = try {
            Cv.warp(upright, quad)
        } finally {
            upright.recycle()
        }

        val originalTmp = File(page.originalPath + TMP_SUFFIX)
        var optimizedTmp: File? = null
        try {
            Images.saveJpeg(cropped, originalTmp, 92)
            page.optimizedPath?.let { path ->
                val enhanced = Cv.optimize(cropped)
                try {
                    val tmp = File(path + TMP_SUFFIX)
                    saveOptimized(enhanced, tmp)
                    optimizedTmp = tmp
                } finally {
                    enhanced.recycle()
                }
            }

            if (!originalTmp.renameTo(File(page.originalPath))) return false
            optimizedTmp?.let { tmp ->
                if (!tmp.renameTo(File(page.optimizedPath!!))) return false
            }
            return true
        } catch (t: Throwable) {
            return false
        } finally {
            cropped.recycle()
            if (originalTmp.exists()) originalTmp.delete()
            optimizedTmp?.takeIf { it.exists() }?.delete()
        }
    }

    /** Writes the page already rotated, so rotation never has to be applied twice. */
    private suspend fun bakeRotation(page: PageDraft): PageDraft? = withContext(Dispatchers.Default) {
        try {
            val original = Images.decodeUpright(File(page.originalPath), maxSide = MAX_CAPTURE_SIDE)
                ?: return@withContext null
            val rotatedOriginal = Images.rotate(original, page.rotation.toFloat())
            val newOriginal = repository.newTempPageFile()
            Images.saveJpeg(rotatedOriginal, newOriginal, 92)
            if (rotatedOriginal !== original) original.recycle()
            rotatedOriginal.recycle()
            tempFiles.add(newOriginal)

            val newOptimized = page.optimizedPath?.let { optimizedPath ->
                try {
                    val optimized = Images.decodeUpright(File(optimizedPath), maxSide = MAX_CAPTURE_SIDE)
                    if (optimized == null) {
                        null
                    } else {
                        val rotated = Images.rotate(optimized, page.rotation.toFloat())
                        val target = repository.newTempPageFile()
                        saveOptimized(rotated, target)
                        if (rotated !== optimized) optimized.recycle()
                        rotated.recycle()
                        tempFiles.add(target)
                        target.absolutePath
                    }
                } catch (t: Throwable) {
                    null
                }
            }

            page.copy(
                originalPath = newOriginal.absolutePath,
                optimizedPath = newOptimized,
                rotation = 0,
            )
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Writes the tiled watermark into the page files themselves, so the stored
     * JPG, the shared image and the PDF built from them all show the same mark
     * exactly once.
     */
    private fun applyWatermark(page: PageDraft): PageDraft {
        val text = settings.watermarkText.trim()
        if (text.isEmpty()) return page
        return page.copy(
            originalPath = markFile(page.originalPath, text) ?: page.originalPath,
            optimizedPath = page.optimizedPath?.let { markFile(it, text) },
        )
    }

    private fun markFile(path: String, text: String): String? {
        val source = Images.decodeUpright(File(path), maxSide = MAX_CAPTURE_SIDE) ?: return null
        val target = repository.newTempPageFile()
        return try {
            val marked = Watermark.apply(source, text) ?: return null
            Images.saveJpeg(marked, target, 92)
            if (marked !== source) marked.recycle()
            tempFiles.add(target)
            target.absolutePath
        } finally {
            source.recycle()
        }
    }

    /**
     * The page the user chose in the editor is the one that gets stored: the
     * library, the viewer and the shared files all read [PageDraft.originalPath],
     * so the Optimizar version becomes it and the other variant is dropped.
     */
    private fun PageDraft.forSave(chosen: Finish): PageDraft =
        copy(originalPath = activePath(chosen), optimizedPath = null)

    private fun replacePage(page: PageDraft) {
        val index = pages.indexOfFirst { it.id == page.id }
        if (index >= 0) pages[index] = page
    }

    private fun cleanupTemps() {
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
    }

    companion object {
        /** Enough for a clean 2400 px output without holding a 50 MP bitmap. */
        private const val MAX_CAPTURE_SIDE = 2600
        private const val TMP_SUFFIX = ".tmp"

        val FULL_FRAME = listOf(
            Point(0f, 0f),
            Point(1f, 0f),
            Point(1f, 1f),
            Point(0f, 1f),
        )
    }
}





