package com.sesos.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

object MediaSaver {
    fun mimeOf(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "mkv" -> "video/x-matroska"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    /** Copies [file] into the public gallery/music/downloads folders under "Sesos". */
    fun save(ctx: Context, file: File, kind: Kind): Pair<Uri, String> {
        val mime = mimeOf(file)
        val dir = when (kind) {
            Kind.VIDEO -> Environment.DIRECTORY_MOVIES
            Kind.AUDIO -> Environment.DIRECTORY_MUSIC
            Kind.IMAGE -> Environment.DIRECTORY_PICTURES
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = when (kind) {
                Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                Kind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/Sesos")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IllegalStateException("MediaStore insert failed")
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri to mime
        } else {
            val target = File(File(Environment.getExternalStoragePublicDirectory(dir), "Sesos").apply { mkdirs() }, file.name)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(mime), null)
            return Uri.fromFile(target) to mime
        }
    }
}
