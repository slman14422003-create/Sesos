package com.sesos.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

data class SavedFile(
    val uri: Uri,
    val name: String,
    val kind: Kind,
    val mime: String,
    val size: Long,
    val added: Long
) {
    val title: String
        get() = name.substringBeforeLast('.').replace(Regex("\\s*\\[[A-Za-z0-9_-]{6,}\\]$"), "")
}

object Library {
    fun load(ctx: Context): List<SavedFile> {
        val out = ArrayList<SavedFile>()
        val sources = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Kind.AUDIO,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Kind.VIDEO,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Kind.IMAGE
        )
        for ((base, kind) in sources) {
            try { query(ctx, base, kind, out) } catch (_: Throwable) {}
        }
        return out.sortedByDescending { it.added }
    }

    @Suppress("DEPRECATION")
    private fun query(ctx: Context, base: Uri, kind: Kind, out: MutableList<SavedFile>) {
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val column = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        ctx.contentResolver.query(
            base, proj, "$column LIKE ?", arrayOf("%/Sesos/%"), MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext()) {
                out.add(
                    SavedFile(
                        uri = ContentUris.withAppendedId(base, c.getLong(iId)),
                        name = c.getString(iName) ?: "",
                        kind = kind,
                        mime = c.getString(iMime) ?: "*/*",
                        size = c.getLong(iSize),
                        added = c.getLong(iAdded)
                    )
                )
            }
        }
    }

    fun delete(ctx: Context, f: SavedFile): Boolean =
        try { ctx.contentResolver.delete(f.uri, null, null) > 0 } catch (_: Throwable) { false }
}
