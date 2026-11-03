package com.scanfromcam.domain.repository

import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.DocumentMeta
import com.scanfromcam.domain.model.FolderMeta
import com.scanfromcam.domain.model.PageMeta
import com.scanfromcam.domain.model.PageDraft
import com.scanfromcam.domain.model.Point
import java.io.File

interface DocumentRepository {

    suspend fun create(name: String, format: DocFormat, folderId: String? = null): DocumentMeta

    /** Root documents when [folderId] is null, otherwise just that folder's. */
    suspend fun list(folderId: String? = null): List<DocumentMeta>

    suspend fun get(id: String): DocumentMeta?

    suspend fun pages(docId: String): List<PageMeta>

    suspend fun folders(): List<FolderMeta>

    /** Every document on the device, inside folders or not. */
    suspend fun totalCount(): Int

    /** Documents per folder, keyed by folder id; folders empty are absent. */
    suspend fun folderCounts(): Map<String, Int>

    suspend fun createFolder(name: String): FolderMeta

    suspend fun renameFolder(folderId: String, name: String)

    /** Removes the folder and every document inside it. */
    suspend fun deleteFolder(folderId: String)

    /** Changes the folder of [ids]; a null folder means the root. */
    suspend fun move(ids: Collection<String>, folderId: String?)

    /** Duplicates [ids] (rows and files) into [folderId]. */
    suspend fun copy(ids: Collection<String>, folderId: String?)

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
