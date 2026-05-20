package com.orion.blaster.core.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.orion.blaster.core.model.SourceState

class MediaStoreLocalSongScanner(
    context: Context,
) : LocalSongScanner {
    private val resolver: ContentResolver = context.contentResolver

    override fun scan(): List<ScannedLocalSong> {
        val songs = mutableListOf<ScannedLocalSong>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        resolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val contentUri: Uri = Uri.withAppendedPath(uri, id.toString())
                songs += ScannedLocalSong(
                    localSongId = "media-$id",
                    uri = contentUri.toString(),
                    title = cursor.getString(titleIndex),
                    artist = cursor.getString(artistIndex),
                    album = cursor.getString(albumIndex),
                    durationMs = cursor.getLong(durationIndex),
                    sizeBytes = cursor.getLong(sizeIndex),
                    dateModified = cursor.getLong(dateModifiedIndex),
                    mimeType = cursor.getString(mimeTypeIndex),
                    sourceState = SourceState.AVAILABLE,
                    contentSignature = null,
                )
            }
        }
        return songs
    }
}
