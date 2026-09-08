package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

object MediaHelper {
    fun getFileName(context: Context, uri: Uri): String {
        var name = "Media_${System.currentTimeMillis().toString().takeLast(4)}"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val str = cursor.getString(nameIndex)
                    if (!str.isNullOrBlank()) name = str
                }
            }
        } catch (e: Exception) {
            name = uri.lastPathSegment ?: name
        }
        return name
    }

    /**
     * Returns the actual duration of the video at [uri], or `null` when it cannot be read.
     * This intentionally does NOT invent a fallback duration: callers must decide how to
     * handle an unreadable duration explicitly rather than silently fabricating 3 minutes.
     */
    fun getVideoDurationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
