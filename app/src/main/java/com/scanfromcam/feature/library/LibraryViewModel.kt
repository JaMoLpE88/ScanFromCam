package com.scanfromcam.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanfromcam.core.Strings
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.DocumentMeta
import com.scanfromcam.domain.model.FolderMeta
import com.scanfromcam.domain.repository.DocumentRepository
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(private val repository: DocumentRepository) : ViewModel() {

    /** Documents cut or copied, waiting for a paste target. */
    data class Clipboard(val ids: Set<String>, val move: Boolean)

    var documents by mutableStateOf<List<DocumentMeta>>(emptyList())
        private set

    var thumbnails by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var folders by mutableStateOf<List<FolderMeta>>(emptyList())
        private set

    /** Every document on the device: the library counter shows this even inside a folder. */
    var totalCount by mutableStateOf(0)
        private set

    var folderCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Folder on screen; null is the library root. */
    var folderId by mutableStateOf<String?>(null)
        private set

    var folderName by mutableStateOf<String?>(null)
        private set

    /** Ids picked with a long press; empty means the list is not in selection mode. */
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set

    var clipboard by mutableStateOf<Clipboard?>(null)
        private set

    val selecting: Boolean
        get() = selection.isNotEmpty()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            try {
                folders = repository.folders()
                totalCount = repository.totalCount()
                folderCounts = repository.folderCounts()
                val list = repository.list(folderId)
                documents = list
                thumbnails = list.take(30).mapNotNull { document ->
                    repository.pages(document.id).firstOrNull()?.let { document.id to it.originalPath }
                }.toMap()
                selection = selection intersect list.map { it.id }.toSet()
                error = null
            } catch (t: Throwable) {
                error = Strings.t.libraryError
            } finally {
                loading = false
            }
        }
    }

    fun open(folder: FolderMeta) {
        folderId = folder.id
        folderName = folder.name
        selection = emptySet()
        refresh()
    }

    fun up() {
        folderId = null
        folderName = null
        selection = emptySet()
        refresh()
    }

    fun toggle(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun clearSelection() {
        selection = emptySet()
    }

    fun showError(message: String) {
        error = message
    }

    fun createFolder(rawName: String) {
        val name = rawName.trim().take(60)
        if (name.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.createFolder(name) }
                .onFailure { error = Strings.t.folderCreateFailed }
            refresh()
        }
    }

    fun renameFolder(folder: FolderMeta, rawName: String) {
        val name = rawName.trim().take(60)
        if (name.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.renameFolder(folder.id, name) }
                .onFailure { error = Strings.t.renameFailed }
            if (folderId == folder.id) folderName = name
            refresh()
        }
    }

    fun deleteFolder(folder: FolderMeta) {
        viewModelScope.launch {
            runCatching { repository.deleteFolder(folder.id) }
                .onFailure { error = Strings.t.folderDeleteFailed }
            if (folderId == folder.id) {
                folderId = null
                folderName = null
            }
            refresh()
        }
    }

    fun cut() {
        if (selection.isEmpty()) return
        clipboard = Clipboard(selection, move = true)
        selection = emptySet()
    }

    fun copySelection() {
        if (selection.isEmpty()) return
        clipboard = Clipboard(selection, move = false)
        selection = emptySet()
    }

    fun clearClipboard() {
        clipboard = null
    }

    /** Drops the clipboard into the folder on screen (or the root). */
    fun paste() {
        val clip = clipboard ?: return
        val target = folderId
        viewModelScope.launch {
            val pasted = runCatching {
                if (clip.move) {
                    repository.move(clip.ids, target)
                } else {
                    repository.copy(clip.ids, target)
                }
            }.isSuccess
            if (pasted) clipboard = null else error = Strings.t.pasteFailed
            refresh()
        }
    }

    /** The files each document hands to the share sheet: its PDF, or one JPG per page. */
    suspend fun shareFilesOf(ids: Set<String>): List<File> = ids.flatMap { id ->
        val document = repository.get(id) ?: return@flatMap emptyList()
        val pdf = document.pdfPath?.let(::File)?.takeIf { it.isFile }
        when {
            pdf != null -> listOf(pdf)
            document.format == DocFormat.PDF -> emptyList()
            else -> repository.pages(id).mapNotNull { page ->
                File(page.originalPath).takeIf { it.isFile }
            }
        }
    }

    fun delete(ids: Set<String>, onDone: () -> Unit = {}) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val failed = ids.filter { runCatching { repository.delete(it) }.isFailure }
            if (failed.isNotEmpty()) {
                error = Strings.t.deleteFailed
                refresh()
                return@launch
            }
            documents = documents.filterNot { it.id in ids }
            thumbnails = thumbnails - ids
            selection = selection - ids
            clipboard = clipboard?.takeIf { it.ids.none { id -> id in ids } }
            onDone()
        }
    }
}
