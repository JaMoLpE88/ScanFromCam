package com.scanfromcam.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private fun File.contentUri(context: Context) =
    FileProvider.getUriForFile(context, "${context.packageName}.files", this)

/**
 * Hands [files] to the system share sheet (email, WhatsApp, Drive…). Returns
 * false when there is nothing to share or no app answered the chooser.
 */
fun shareFiles(context: Context, files: List<File>): Boolean {
    val existing = files.filter { it.isFile }
    if (existing.isEmpty()) return false
    val uris = existing.map { it.contentUri(context) }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.type = mimeOf(existing)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return runCatching {
        context.startActivity(Intent.createChooser(intent, Strings.t.shareDocument))
    }.isSuccess
}

/** Opens [file] with whatever app handles [mime]. */
fun openFile(context: Context, file: File, mime: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.contentUri(context), mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

internal fun mimeOf(files: List<File>): String {
    val types = files.map { if (it.extension.equals("pdf", true)) "application/pdf" else "image/jpeg" }
    return types.distinct().singleOrNull() ?: "*/*"
}
