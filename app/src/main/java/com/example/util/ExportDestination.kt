package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resolves where an exported MP4 is physically written.
 *
 *  1. If the user picked a destination folder through the Storage Access Framework, we create the
 *     document inside that tree and write to it through its content:// Uri (real SAF writes — no
 *     fake "/sdcard/..." strings).
 *  2. Otherwise we write to the public Movies collection via [MediaStore] (the correct mechanism
 *     for the app's target SDK), falling back to the classic external Movies directory only on
 *     pre-Android-10 devices.
 */
object ExportDestination {

    class Result(
        val pfd: ParcelFileDescriptor,
        /** Uri (content://) the file was written to; kept for verification / finalize. */
        val uri: Uri,
        /** Finalize/commit steps the caller must run after a successful export. */
        val needsPendingFinalize: Boolean,
        /** Human / MediaStore-friendly destination string shown to the user on success. */
        val displayName: String
    )

    fun create(
        context: Context,
        chosenFolderTreeUri: String?,
        baseName: String = "ReactionStudio"
    ): Result {
        val resolver = context.contentResolver
        val safeName = "${baseName}_${timestamp()}.mp4"

        return if (!chosenFolderTreeUri.isNullOrBlank()) {
            createInChosenFolder(context, Uri.parse(chosenFolderTreeUri), safeName)
        } else {
            createDefault(context, safeName)
        }
    }

    private fun createInChosenFolder(
        context: Context,
        treeUri: Uri,
        fileName: String
    ): Result {
        val resolver = context.contentResolver
        // Take a persistable grant on the tree so we can write beyond the current process.
        runCatching {
            resolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId),
            "video/mp4",
            fileName
        ) ?: throw IllegalStateException("Could not create a file in the chosen folder.")

        val pfd = try {
            resolver.openFileDescriptor(docUri, "w")!!
        } catch (e: Exception) {
            runCatching { resolver.delete(docUri, null, null) }
            throw e
        }
        return Result(pfd, docUri, needsPendingFinalize = false, displayName = fileName)
    }

    private fun createDefault(context: Context, fileName: String): Result {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Modern scoped storage: insert a pending file into the public Movies collection.
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/ReactionStudio"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create the MP4 in Movies.")
            val pfd = try {
                resolver.openFileDescriptor(uri, "w")!!
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            return Result(
                pfd, uri,
                needsPendingFinalize = true,
                displayName = "Movies/ReactionStudio/$fileName"
            )
        }

        // Pre-Android-10: legacy public Movies directory (requires WRITE_EXTERNAL_STORAGE).
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "ReactionStudio"
        )
        if (!dir.exists()) {
            if (!dir.mkdirs()) throw IllegalStateException("Could not create the Movies export folder.")
        }
        val file = File(dir, fileName)
        val pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_TRUNCATE
        )
        val uri = Uri.fromFile(file)
        return Result(pfd, uri, needsPendingFinalize = false, displayName = file.absolutePath)
    }

    /**
     * Verifies the written output is a real, non-empty file. Must be called after the muxer has
     * finalized and the pending flag has been cleared (so the file is queryable). Returns true only
     * when the file physically exists and reports a non-zero size.
     */
    fun verifyNonEmpty(context: Context, result: Result): Boolean {
        // Legacy file:// destination: check the real file.
        if (result.uri.scheme == "file") {
            return runCatching {
                val f = File(result.uri.path ?: return false)
                f.exists() && f.length() > 0
            }.getOrDefault(false)
        }
        // content:// destination: query the size through a reader.
        return try {
            context.contentResolver.openAssetFileDescriptor(result.uri, "r")?.use { afd ->
                afd.length > 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** After a successful default-Movies export, removes the IS_PENDING marker so the file is final. */
    fun finalizePending(context: Context, result: Result) {
        if (!result.needsPendingFinalize || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        context.contentResolver.update(result.uri, values, null, null)
    }

    /** Closes and deletes a (possibly partial/failed) output file. Best-effort. */
    fun deleteOutput(context: Context, result: Result?) {
        result ?: return
        runCatching { result.pfd.close() }
        runCatching { context.contentResolver.delete(result.uri, null, null) }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
