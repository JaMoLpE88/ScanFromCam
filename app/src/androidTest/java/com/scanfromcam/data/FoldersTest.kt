package com.scanfromcam.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.PageDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Folders, moving and copying measured against a throwaway database. */
@RunWith(AndroidJUnit4::class)
class FoldersTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun foldersMoveAndCopyKeepRowsAndFiles() = runBlocking {
        val repository = LocalDocumentRepository(context, "folders-${UUID.randomUUID()}.db")
        val source = File.createTempFile("page", ".jpg", context.cacheDir).apply { writeBytes(ByteArray(64)) }
        val document = repository.create("Factura", DocFormat.IMAGE)
        repository.addPage(
            document.id,
            PageDraft(id = "draft", rawPath = null, originalPath = source.absolutePath, optimizedPath = null),
        )

        val folder = repository.createFolder("Facturas")
        assertEquals(listOf("Facturas"), repository.folders().map { it.name })
        assertEquals(1, repository.list().size)
        assertEquals(1, repository.totalCount())
        assertEquals(0, repository.folderCounts()[folder.id] ?: 0)
        assertTrue(repository.list(folder.id).isEmpty())

        repository.move(listOf(document.id), folder.id)
        assertTrue(repository.list().isEmpty())
        assertEquals(listOf(document.id), repository.list(folder.id).map { it.id })
        assertEquals(1, repository.folderCounts()[folder.id])

        repository.copy(listOf(document.id), null)
        val copy = repository.list().single()
        assertNotEquals(document.id, copy.id)
        assertEquals("Factura", copy.name)
        assertEquals(2, repository.totalCount())
        val originalPage = repository.pages(document.id).single()
        val copiedPage = repository.pages(copy.id).single()
        assertTrue(File(copiedPage.originalPath).isFile)
        assertNotEquals(originalPage.originalPath, copiedPage.originalPath)

        repository.renameFolder(folder.id, "Facturas 2026")
        assertEquals("Facturas 2026", repository.folders().single().name)

        // Deleting a folder takes its documents and files with it.
        repository.deleteFolder(folder.id)
        assertTrue(repository.folders().isEmpty())
        assertEquals(1, repository.totalCount())
        assertTrue(repository.list(folder.id).isEmpty())
        assertTrue(!File(originalPage.originalPath).exists())
        assertTrue(File(copiedPage.originalPath).isFile)
    }
}
