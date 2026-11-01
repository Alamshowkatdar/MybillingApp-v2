package com.factorybill.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    suspend fun imageForAi(ctx: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) { enhancedBitmap(ctx, uri) }

    suspend fun recognize(ctx: Context, uri: Uri): ScanResult {
        return try {
            var initialFailure: Exception? = null
            val original = try {
                recognizeImage(InputImage.fromFilePath(ctx, uri))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                initialFailure = error
                ""
            }
            if (original.isNotBlank() && original.length >= MIN_CONFIDENT_TEXT_LENGTH) {
                ScanResult.Success(original)
            } else {
                val enhanced = try {
                    withContext(Dispatchers.IO) { enhancedBitmap(ctx, uri) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (enhanced == null) {
                    when {
                        original.isNotBlank() -> ScanResult.Success(original)
                        initialFailure != null -> ScanResult.Failure(
                            "Could not open this image (${initialFailure.message ?: "unsupported image format"}). Choose a clear JPG/PNG photo or enter the bill manually."
                        )
                        else -> ScanResult.NoText
                    }
                } else {
                    val fallback = try {
                        recognizeImage(InputImage.fromBitmap(enhanced, 0))
                    } finally {
                        enhanced.recycle()
                    }
                    val best = if (fallback.length > original.length) fallback else original
                    if (best.isBlank()) ScanResult.NoText else ScanResult.Success(best)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ScanResult.Failure("Bill scan failed (${e.message ?: "unsupported or unreadable image"}). Choose a clear photo or enter the bill manually.")
        }
    }

    private suspend fun recognizeImage(image: InputImage): String = suspendCancellableCoroutine { cont ->
        try {
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.text)
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) cont.resumeWith(Result.failure(error))
                }
        } catch (error: Exception) {
            if (cont.isActive) cont.resumeWith(Result.failure(error))
        }
    }

    private fun enhancedBitmap(ctx: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val decoded = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decoded) }
            ?: return null
        val oriented = orientBitmap(ctx, uri, source)
        val contrast = 1.35f
        val shift = 128f * (1f - contrast)
        val filter = ColorMatrixColorFilter(
            ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, shift,
                0f, contrast, 0f, 0f, shift,
                0f, 0f, contrast, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            ))
        )
        return Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888).also { result ->
            Canvas(result).drawBitmap(oriented, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = filter })
            if (oriented !== source) source.recycle()
            oriented.recycle()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_BITMAP_SIDE || height / sample > MAX_BITMAP_SIDE) sample *= 2
        return sample
    }

    private fun orientBitmap(ctx: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = ctx.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private const val MAX_BITMAP_SIDE = 2200
    private const val MIN_CONFIDENT_TEXT_LENGTH = 40
}

/** Scanned bills live in app-private storage (filesDir), never in the gallery. */
object ScanStore {
    fun cameraTarget(ctx: Context): Pair<File, Uri> {
        val dir = File(ctx.cacheDir, "capture").apply { mkdirs() }
        val f = File.createTempFile("bill_", ".jpg", dir)
        return f to FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

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
