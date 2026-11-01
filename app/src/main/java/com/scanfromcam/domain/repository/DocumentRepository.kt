package com.scanfromcam.domain.repository

import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.DocumentMeta
import com.scanfromcam.domain.model.PageMeta
import com.scanfromcam.domain.model.PageDraft
import com.scanfromcam.domain.model.Point
import java.io.File

interface DocumentRepository {

    suspend fun create(name: String, format: DocFormat): DocumentMeta

    suspend fun list(): List<DocumentMeta>

    suspend fun get(id: String): DocumentMeta?

    suspend fun pages(docId: String): List<PageMeta>

    suspend fun addPage(
        docId: String,
        draft: PageDraft,
    ): PageMeta

    suspend fun updatePage(page: PageMeta)

    suspend fun setPdfPath(docId: String, pdfFile: File?)

    suspend fun rename(docId: String, name: String)

    suspend fun delete(docId: String)

    fun newTempPageFile(): File

    fun documentDir(docId: String): File
}
