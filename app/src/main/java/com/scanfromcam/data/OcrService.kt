package com.scanfromcam.data

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrLine(val text: String, val box: Rect)

data class OcrResult(val text: String, val lines: List<OcrLine>)

/**
 * On-device text recognition. One recognizer per process is enough.
 */
class OcrService(
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
) {

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    val lines = buildList {
                        for (block in text.textBlocks) {
                            for (line in block.lines) {
                                val box = line.boundingBox ?: continue
                                val value = line.text.trim()
                                if (value.isNotEmpty()) add(OcrLine(value, box))
                            }
                        }
                    }.sortedWith(compareBy({ it.box.top }, { it.box.left }))

                    continuation.resume(
                        OcrResult(
                            text = lines.joinToString("\n") { it.text }.ifEmpty { text.text.trim() },
                            lines = lines,
                        )
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }
    }

    fun close() {
        recognizer.close()
    }
}
