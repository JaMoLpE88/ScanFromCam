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
            CREATE TABLE folders (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                locked INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE documents (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                format TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                page_count INTEGER NOT NULL DEFAULT 0,
                pdf_path TEXT,
                folder_id TEXT REFERENCES folders(id) ON DELETE SET NULL,
                locked INTEGER NOT NULL DEFAULT 0
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
        db.execSQL("CREATE INDEX idx_documents_folder ON documents(folder_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Shipped without the lock columns: those come in the v3 step below,
            // so a v1 database upgrading straight to v3 does not get them twice.
            db.execSQL(
                """
                CREATE TABLE folders (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "ALTER TABLE documents ADD COLUMN folder_id TEXT REFERENCES folders(id) ON DELETE SET NULL"
            )
            db.execSQL("CREATE INDEX idx_documents_folder ON documents(folder_id)")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE documents ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE folders ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
        }
    }

    companion object {
        const val VERSION = 3
    }
}
