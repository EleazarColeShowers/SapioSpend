package com.el.sapiospend.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Keeps receipt photos where the app can still read them tomorrow.
 *
 * The picker hands back a content:// URI whose read permission is granted to this
 * process and does not survive a reboot, so the image is copied into the app's own files
 * directory and only the path is stored. Copying also means deleting the photo from the
 * gallery does not blank a receipt attached to a ₦2m payment.
 *
 * filesDir rather than cacheDir: the system is free to reclaim the cache under storage
 * pressure, and a receipt that vanishes when the phone fills up is worse than no receipt
 * feature at all. Nothing here is shareable — the FileProvider exposes only the exports
 * directory — so a receipt cannot leak to another app by accident.
 */
class ReceiptStore(context: Context) {

    private val appContext = context.applicationContext
    private val directory: File get() = File(appContext.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Copies the picked image in, downscaled, and returns its path — or null if the URI
     * could not be read, which is the ordinary outcome when the user picks a file the
     * provider has since dropped.
     */
    suspend fun save(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeDownsampled(uri) ?: return@runCatching null
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { out ->
                // JPEG at 85 rather than the original bytes: a modern phone camera
                // produces 4-6MB per shot, and a wedding with forty receipts would put
                // a quarter of a gigabyte in the app's storage for images nobody will
                // ever view above phone-screen size.
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
    }

    /** Removes a stored receipt. Silent when the file is already gone. */
    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        // Confined to the receipts directory: a path from a corrupted row must not be
        // able to talk this into deleting something else in the app's storage.
        path?.let { File(it) }
            ?.takeIf { it.parentFile?.absolutePath == directory.absolutePath }
            ?.delete()
        Unit
    }

    /** Loads a stored receipt for display, or null when the file has gone missing. */
    suspend fun load(path: String, maxPixels: Int = DISPLAY_MAX_PIXELS): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                BitmapFactory.decodeFile(
                    path,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPixels)
                    }
                )
            }.getOrNull()
        }

    fun exists(path: String?): Boolean = path != null && File(path).exists()

    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, STORED_MAX_PIXELS)
        return appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    /**
     * The power-of-two subsample that brings an image under [maxPixels] on its longest
     * edge. Powers of two because that is the only thing inSampleSize honours — anything
     * else is rounded down to one, and the full-size bitmap lands in memory anyway.
     */
    private fun sampleSizeFor(width: Int, height: Int, maxPixels: Int): Int {
        var longest = maxOf(width, height)
        var sample = 1
        while (longest / 2 >= maxPixels) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val DIRECTORY = "receipts"
        const val QUALITY = 85

        /** Long edge of what gets stored — enough to read an amount off a printed receipt. */
        const val STORED_MAX_PIXELS = 1600

        /** Long edge of what gets decoded for the screen; the stored file stays full size. */
        const val DISPLAY_MAX_PIXELS = 1000
    }
}
