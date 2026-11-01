package com.factorybill.app.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

sealed interface ScanResult {
    data class Success(val text: String) : ScanResult
    data object NoText : ScanResult
    data class Failure(val message: String) : ScanResult
}

/** On-device OCR (bundled ML Kit Latin model, works offline). Handwriting accuracy is limited. */
object OcrScanner {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(ctx: Context, uri: Uri): ScanResult {
        if (!ScanStore.isReadableImage(ctx, uri))
            return ScanResult.Failure("This file could not be opened as an image. Choose a JPG or PNG photo.")
        return suspendCancellableCoroutine { cont ->
            try {
                recognizer.process(InputImage.fromFilePath(ctx, uri))
                    .addOnSuccessListener { t ->
                        if (cont.isActive) cont.resume(if (t.text.isBlank()) ScanResult.NoText else ScanResult.Success(t.text))
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resume(ScanResult.Failure("Text recognition failed (${e.message ?: "unknown error"}). Retry, or enter the bill manually."))
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(ScanResult.Failure("Could not read the image (${e.message ?: "unknown error"}). Retry, or enter the bill manually."))
            }
        }
    }
}

/** Scanned bills live in app-private storage (filesDir), never in the gallery. */
object ScanStore {
    fun cameraTarget(ctx: Context): Pair<File, Uri> {
        val dir = File(ctx.cacheDir, "capture").apply { mkdirs() }
        val f = File.createTempFile("bill_", ".jpg", dir)
        return f to FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    fun isReadableImage(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openInputStream(uri)?.use {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(it, null, o)
            o.outWidth > 0 && o.outHeight > 0
        } ?: false
    } catch (e: Exception) { false }

    /** Copy the original image into private storage. Returns the path, or null on failure. */
    fun saveOriginal(ctx: Context, uri: Uri): String? = try {
        val dir = File(ctx.filesDir, "scans").apply { mkdirs() }
        val out = File(dir, UUID.randomUUID().toString() + ".jpg")
        ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        if (out.length() > 0) out.absolutePath else null
    } catch (e: Exception) { null }

    /** Call when the user abandons a scan so no orphan images are left behind. */
    fun discard(path: String?) { if (path != null) File(path).delete() }
}
