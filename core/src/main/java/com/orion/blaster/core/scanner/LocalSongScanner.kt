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
