package com.orion.blaster.core.scanner

import com.orion.blaster.core.model.SourceState

interface LocalSongScanner {
    fun scan(): List<ScannedLocalSong>
}

data class ScannedLocalSong(
    val localSongId: String,
    val uri: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val dateModified: Long?,
    val mimeType: String?,
    val sourceState: SourceState,
    val contentSignature: String?,
)

data class TestSongRecord(
    val localSongId: String,
    val uri: String?,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
    val dateModified: Long? = null,
    val mimeType: String? = null,
    val sourceState: SourceState = SourceState.AVAILABLE,
    val contentSignature: String? = null,
)

class TestLocalSongScanner(
    private val records: List<TestSongRecord>,
) : LocalSongScanner {
    override fun scan(): List<ScannedLocalSong> {
        return records.map { record ->
            ScannedLocalSong(
                localSongId = record.localSongId,
                uri = record.uri,
                title = record.title,
                artist = record.artist,
                album = record.album,
                durationMs = record.durationMs,
                sizeBytes = record.sizeBytes,
                dateModified = record.dateModified,
                mimeType = record.mimeType,
                sourceState = record.sourceState,
                contentSignature = record.contentSignature,
            )
        }
    }
}
