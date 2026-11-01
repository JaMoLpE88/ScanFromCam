package com.scanfromcam.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanfromcam.core.Strings
import com.scanfromcam.domain.model.DocumentMeta
import com.scanfromcam.domain.repository.DocumentRepository
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: DocumentRepository) : ViewModel() {

    var documents by mutableStateOf<List<DocumentMeta>>(emptyList())
        private set

    var thumbnails by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            try {
                val list = repository.list()
                documents = list
                thumbnails = list.take(30).mapNotNull { document ->
                    repository.pages(document.id).firstOrNull()?.let { document.id to it.originalPath }
                }.toMap()
                error = null
            } catch (t: Throwable) {
                error = Strings.t.libraryError
            } finally {
                loading = false
            }
        }
    }

}

