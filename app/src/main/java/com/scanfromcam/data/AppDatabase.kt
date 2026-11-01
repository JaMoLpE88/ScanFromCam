package com.scanfromcam.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context, name: String? = "scanfromcam.db") :
    SQLiteOpenHelper(context, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE documents (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                format TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                page_count INTEGER NOT NULL DEFAULT 0,
                pdf_path TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pages (
                id TEXT PRIMARY KEY NOT NULL,
                doc_id TEXT NOT NULL,
                idx INTEGER NOT NULL,
                original_path TEXT NOT NULL,
                optimized_path TEXT,
                rotation INTEGER NOT NULL DEFAULT 0,
                quad TEXT,
                ocr_text TEXT,
                FOREIGN KEY(doc_id) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_pages_doc ON pages(doc_id, idx)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        const val VERSION = 1
    }
}
