package com.scanfromcam.domain.model

import java.io.File

/** A point in normalized coordinates (0..1) of an upright image. */
data class Point(val x: Float, val y: Float)

enum class DocFormat { IMAGE, PDF }

enum class Finish { ORIGINAL, OPTIMIZED }

data class DocumentMeta(
    val id: String,
    val name: String,
    val format: DocFormat,
    val pageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val pdfPath: String?,
)

/** A flat folder. Documents live either in one folder or in the library root. */
data class FolderMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
)

data class PageMeta(
    val id: String,
    val docId: String,
    val index: Int,
    val originalPath: String,
    val optimizedPath: String?,
    val rotation: Int,
    val quad: List<Point>?,
    val ocrText: String?,
) {
    fun activePath(finish: Finish): String =
        if (finish == Finish.OPTIMIZED && optimizedPath != null) optimizedPath else originalPath

    fun originalFile() = File(originalPath)

    fun optimizedFile(): File? = optimizedPath?.let(::File)
}

/** A page while the scan session is still open (not persisted yet). */
data class PageDraft(
    val id: String,
    /** Upright, uncropped capture; kept so the page can be re-cropped. */
    val rawPath: String?,
    val originalPath: String,
    val optimizedPath: String?,
    val rotation: Int = 0,
    val quad: List<Point>? = null,
    val ocrText: String? = null,
) {
    fun activePath(finish: Finish): String =
        if (finish == Finish.OPTIMIZED && optimizedPath != null) optimizedPath else originalPath
}

data class CapturedPhoto(
    val jpeg: ByteArray,
    val quad: List<Point>?,
)
