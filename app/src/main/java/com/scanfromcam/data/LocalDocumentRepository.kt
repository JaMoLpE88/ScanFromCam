package com.scanfromcam.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.domain.model.DocumentMeta
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

    override suspend fun create(name: String, format: DocFormat): DocumentMeta =
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
            }
            db.writableDatabase.insertOrThrow("documents", null, values)
            documentDir(id).mkdirs()
            DocumentMeta(id, name, format, 0, now, now, null)
        }

    override suspend fun list(): List<DocumentMeta> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "documents", null, null, null, null, null, "updated_at DESC"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toDocument()) } }
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



