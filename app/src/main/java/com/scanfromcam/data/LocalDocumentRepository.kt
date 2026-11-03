package com.scanfromcam.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.DocumentMeta
import com.scanfromcam.domain.model.FolderMeta
import com.scanfromcam.domain.model.PageDraft
import com.scanfromcam.domain.model.PageMeta
import com.scanfromcam.domain.model.Point
import com.scanfromcam.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LocalDocumentRepository(context: Context, databaseName: String? = "scanfromcam.db") : DocumentRepository {

    private val appContext = context.applicationContext
    private val db = AppDatabase(appContext, databaseName).apply { writableDatabase }

    private val documentsRoot: File
        get() = File(appContext.filesDir, "documents").apply { mkdirs() }

    private val tempRoot: File
        get() = File(appContext.cacheDir, "scan").apply { mkdirs() }

    override fun documentDir(docId: String): File = File(documentsRoot, docId)

    override fun newTempPageFile(): File =
        File(tempRoot, "page_${UUID.randomUUID()}.jpg")

    override suspend fun create(name: String, format: DocFormat, folderId: String?): DocumentMeta =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val values = ContentValues().apply {
                put("id", id)
                put("name", name)
                put("format", format.name)
                put("created_at", now)
                put("updated_at", now)
                put("page_count", 0)
                if (folderId == null) putNull("folder_id") else put("folder_id", folderId)
            }
            db.writableDatabase.insertOrThrow("documents", null, values)
            documentDir(id).mkdirs()
            DocumentMeta(id, name, format, 0, now, now, null)
        }

    override suspend fun list(folderId: String?): List<DocumentMeta> = withContext(Dispatchers.IO) {
        val (where, args) =
            if (folderId == null) "folder_id IS NULL" to null else "folder_id = ?" to arrayOf(folderId)
        db.readableDatabase.query(
            "documents", null, where, args, null, null, "updated_at DESC"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toDocument()) } }
    }

    override suspend fun folders(): List<FolderMeta> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "folders", null, null, null, null, null, "name COLLATE NOCASE ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        FolderMeta(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        )
                    )
                }
            }
        }
    }

    override suspend fun totalCount(): Int = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM documents", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    override suspend fun folderCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT folder_id, COUNT(*) FROM documents WHERE folder_id IS NOT NULL GROUP BY folder_id",
            null,
        ).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1)) }
        }
    }

    override suspend fun createFolder(name: String): FolderMeta = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.writableDatabase.insertOrThrow(
            "folders",
            null,
            ContentValues().apply {
                put("id", id)
                put("name", name)
                put("created_at", now)
            },
        )
        FolderMeta(id, name, now)
    }

    override suspend fun renameFolder(folderId: String, name: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "folders",
            ContentValues().apply { put("name", name) },
            "id = ?",
            arrayOf(folderId),
        )
        Unit
    }

    override suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        val ids = db.readableDatabase.query(
            "documents", arrayOf("id"), "folder_id = ?", arrayOf(folderId), null, null, null
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        // The folder takes its content with it: rows and files.
        for (id in ids) delete(id)
        db.writableDatabase.delete("folders", "id = ?", arrayOf(folderId))
        Unit
    }

    override suspend fun move(ids: Collection<String>, folderId: String?) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val values = ContentValues().apply {
            if (folderId == null) putNull("folder_id") else put("folder_id", folderId)
            put("updated_at", System.currentTimeMillis())
        }
        db.writableDatabase.update(
            "documents",
            values,
            "id IN (${ids.joinToString(",") { "?" }})",
            ids.toTypedArray(),
        )
        Unit
    }

    override suspend fun copy(ids: Collection<String>, folderId: String?) = withContext(Dispatchers.IO) {
        for (id in ids) {
            val document = get(id) ?: continue
            val pages = pages(id)
            val newId = UUID.randomUUID().toString()
            val targetDir = documentDir(newId).apply { mkdirs() }
            val now = System.currentTimeMillis()

            // A copy shares no file with its original: if a file cannot be duplicated
            // the whole copy is dropped instead of leaving a half-broken document behind.
            try {
                val copies = pages.map { page -> page to copiedPage(page, targetDir) }
                val copiedPdf = document.pdfPath
                    ?.let(::File)
                    ?.takeIf { it.isFile }
                    ?.let { copyInto(it, targetDir) }

                val values = ContentValues().apply {
                    put("id", newId)
                    put("name", document.name)
                    put("format", document.format.name)
                    put("created_at", now)
                    put("updated_at", now)
                    put("page_count", pages.size)
                    put("pdf_path", copiedPdf?.absolutePath)
                    if (folderId == null) putNull("folder_id") else put("folder_id", folderId)
                }
                db.writableDatabase.insertOrThrow("documents", null, values)

                for ((page, copied) in copies) {
                    db.writableDatabase.insertOrThrow(
                        "pages",
                        null,
                        ContentValues().apply {
                            put("id", UUID.randomUUID().toString())
                            put("doc_id", newId)
                            put("idx", page.index)
                            put("original_path", copied.original)
                            put("optimized_path", copied.optimized)
                            put("rotation", page.rotation)
                            put("quad", page.quad?.let(::encodeQuad))
                            put("ocr_text", page.ocrText)
                        },
                    )
                }
            } catch (t: Throwable) {
                targetDir.deleteRecursively()
                throw t
            }
        }
        Unit
    }

    private class CopiedPage(val original: String, val optimized: String?)

    private fun copiedPage(page: PageMeta, targetDir: File): CopiedPage = CopiedPage(
        original = copyInto(File(page.originalPath), targetDir).absolutePath,
        optimized = page.optimizedPath?.let(::File)?.takeIf { it.isFile }?.let { copyInto(it, targetDir) }?.absolutePath,
    )

    /** Keeps the file name, so page_0001.jpg stays page_0001.jpg inside the new folder. */
    private fun copyInto(source: File, targetDir: File): File {
        val target = File(targetDir, source.name)
        source.copyTo(target, overwrite = true)
        return target
    }

    override suspend fun get(id: String): DocumentMeta? = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "documents", null, "id = ?", arrayOf(id), null, null, null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toDocument() else null }
    }

    override suspend fun pages(docId: String): List<PageMeta> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "pages", null, "doc_id = ?", arrayOf(docId), null, null, "idx ASC"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toPage()) } }
    }

    override suspend fun addPage(docId: String, draft: PageDraft): PageMeta =
        withContext(Dispatchers.IO) {
            val targetDir = documentDir(docId).apply { mkdirs() }
            val index = (db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM pages WHERE doc_id = ?", arrayOf(docId)
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }) + 1

            val original = File(targetDir, "page_%04d.jpg".format(index))
            File(draft.originalPath).copyTo(original, overwrite = true)

            val optimized = draft.optimizedPath?.let { source ->
                val dest = File(targetDir, "page_%04d_opt.jpg".format(index))
                File(source).copyTo(dest, overwrite = true)
                dest
            }

            val pageId = UUID.randomUUID().toString()
            val values = ContentValues().apply {
                put("id", pageId)
                put("doc_id", docId)
                put("idx", index)
                put("original_path", original.absolutePath)
                put("optimized_path", optimized?.absolutePath)
                put("rotation", draft.rotation)
                put("quad", draft.quad?.let(::encodeQuad))
                put("ocr_text", draft.ocrText)
            }
            db.writableDatabase.insertOrThrow("pages", null, values)
            db.writableDatabase.execSQL(
                "UPDATE documents SET page_count = ?, updated_at = ? WHERE id = ?",
                arrayOf<Any>(index, System.currentTimeMillis(), docId)
            )
            PageMeta(
                id = pageId,
                docId = docId,
                index = index,
                originalPath = original.absolutePath,
                optimizedPath = optimized?.absolutePath,
                rotation = draft.rotation,
                quad = draft.quad,
                ocrText = draft.ocrText,
            )
        }

    override suspend fun updatePage(page: PageMeta) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("idx", page.index)
            put("optimized_path", page.optimizedPath)
            put("rotation", page.rotation)
            put("quad", page.quad?.let(::encodeQuad))
            put("ocr_text", page.ocrText)
        }
        db.writableDatabase.update("pages", values, "id = ?", arrayOf(page.id))
        db.writableDatabase.execSQL(
            "UPDATE documents SET updated_at = ? WHERE id = ?",
            arrayOf<Any>(System.currentTimeMillis(), page.docId)
        )
    }

    override suspend fun setPdfPath(docId: String, pdfFile: File?) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("pdf_path", pdfFile?.absolutePath)
            put("updated_at", System.currentTimeMillis())
        }
        db.writableDatabase.update("documents", values, "id = ?", arrayOf(docId))
        Unit
    }

    override suspend fun rename(docId: String, name: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", name)
            put("updated_at", System.currentTimeMillis())
        }
        db.writableDatabase.update("documents", values, "id = ?", arrayOf(docId))
        Unit
    }

    override suspend fun delete(docId: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("documents", "id = ?", arrayOf(docId))
        val dir = documentDir(docId)
        dir.deleteRecursively()
        if (dir.exists()) {
            throw java.io.IOException("No se pudo borrar el contenido de ${dir.name}")
        }
        Unit
    }

    private fun Cursor.toDocument(): DocumentMeta = DocumentMeta(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        format = DocFormat.valueOf(getString(getColumnIndexOrThrow("format"))),
        pageCount = getInt(getColumnIndexOrThrow("page_count")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        pdfPath = getString(getColumnIndexOrThrow("pdf_path")),
    )

    private fun Cursor.toPage(): PageMeta = PageMeta(
        id = getString(getColumnIndexOrThrow("id")),
        docId = getString(getColumnIndexOrThrow("doc_id")),
        index = getInt(getColumnIndexOrThrow("idx")),
        originalPath = getString(getColumnIndexOrThrow("original_path")),
        optimizedPath = getString(getColumnIndexOrThrow("optimized_path")),
        rotation = getInt(getColumnIndexOrThrow("rotation")),
        quad = getString(getColumnIndexOrThrow("quad"))?.let(::decodeQuad),
        ocrText = getString(getColumnIndexOrThrow("ocr_text")),
    )

    companion object {
        fun encodeQuad(points: List<Point>): String =
            points.joinToString(";") { "${it.x},${it.y}" }

        fun decodeQuad(raw: String): List<Point> = raw.split(';').mapNotNull { chunk ->
            val parts = chunk.split(',')
            if (parts.size != 2) return@mapNotNull null
            val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
            Point(x, y)
        }
    }
}



