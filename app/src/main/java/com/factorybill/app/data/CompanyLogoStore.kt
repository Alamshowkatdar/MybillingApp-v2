package com.factorybill.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

object CompanyLogoStore {
    private const val MAX_FILE_BYTES = 12L * 1024 * 1024
    private const val MAX_IMAGE_PIXELS = 40_000_000L

    fun import(context: Context, uri: Uri): String {
        val directory = File(context.filesDir, "company-logos").apply { mkdirs() }
        val destination = File(directory, "${UUID.randomUUID()}.img")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("The selected logo could not be opened.")
            input.use { source ->
                destination.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) throw IOException("Choose a logo image smaller than 12 MB.")
                        target.write(buffer, 0, read)
                    }
                }
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destination.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Choose a supported image file.")
            if (options.outWidth.toLong() * options.outHeight > MAX_IMAGE_PIXELS) {
                throw IOException("This image is too large to use as a logo.")
            }
            return destination.absolutePath
        } catch (error: Exception) {
            destination.delete()
            if (error is IOException) throw error
            throw IOException("Could not save this logo: ${error.message ?: "unknown error"}", error)
        }
    }

    fun decode(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSide || bounds.outHeight / sampleSize > maxSide) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }

    fun delete(context: Context, path: String?) {
        if (path == null) return
        val directory = File(context.filesDir, "company-logos").canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == directory) file.delete()
    }
}
