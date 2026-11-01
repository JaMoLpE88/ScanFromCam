package com.scanfromcam.feature.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanfromcam.core.Strings
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.DocumentMeta
import com.scanfromcam.domain.model.PageMeta
import com.scanfromcam.domain.repository.DocumentRepository
import kotlinx.coroutines.launch
import java.io.File

class ViewerViewModel(
    private val repository: DocumentRepository,
    private val docId: String,
) : ViewModel() {

    var document by mutableStateOf<DocumentMeta?>(null)
        private set

    var pages by mutableStateOf<List<PageMeta>>(emptyList())
        private set

    var error by mutableStateOf<String?>(null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            document = repository.get(docId)
            pages = repository.pages(docId)
        }
    }

    fun rename(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val renamed = runCatching {
                repository.rename(docId, com.scanfromcam.core.sanitizeName(name, prefix = Strings.t.defaultDocumentPrefix))
            }.isSuccess
            if (!renamed) {
                error = Strings.t.renameFailed
                return@launch
            }
            load()
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (runCatching { repository.delete(docId) }.isSuccess) {
                onDone()
            } else {
                error = Strings.t.deleteFailed
            }
        }
    }

    fun pdfFile(): File? = document?.pdfPath?.let { File(it) }

    /** The PDF when the document is a PDF, otherwise every scanned page. */
    fun shareFiles(): List<File> {
        val pdf = pdfFile()
        if (pdf != null && pdf.exists()) return listOf(pdf)
        if (document?.format == DocFormat.PDF) return emptyList()
        return pages.map { File(it.originalPath) }.filter { it.exists() }
    }
}
