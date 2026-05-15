package com.orion.blaster.core.scanner

import com.orion.blaster.core.model.SourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestLocalSongScannerTest {
    @Test
    fun scan_returns_stable_song_list_from_test_records() {
        val scanner = TestLocalSongScanner(
            records = listOf(
                TestSongRecord(
                    localSongId = "song-1",
                    uri = "content://media/1",
                    title = "Hello",
                    artist = "Adele",
                    album = "25",
                    durationMs = 295000L,
                    sizeBytes = 1234L,
                    dateModified = 10L,
                    mimeType = "audio/mpeg",
                    sourceState = SourceState.AVAILABLE,
                    contentSignature = "sig-1",
                ),
            ),
        )

        val songs = scanner.scan()

        assertEquals(1, songs.size)
        assertEquals("song-1", songs[0].localSongId)
        assertEquals("content://media/1", songs[0].uri)
        assertEquals("sig-1", songs[0].contentSignature)
        assertEquals(SourceState.AVAILABLE, songs[0].sourceState)
    }

    @Test
    fun scan_keeps_nullable_fields_for_inaccessible_song_records() {
        val scanner = TestLocalSongScanner(
            records = listOf(
                TestSongRecord(
                    localSongId = "song-2",
                    uri = null,
                    sourceState = SourceState.UNAVAILABLE,
                ),
            ),
        )

        val song = scanner.scan().single()

        assertNull(song.uri)
        assertNull(song.title)
        assertNull(song.artist)
        assertNull(song.contentSignature)
        assertEquals(SourceState.UNAVAILABLE, song.sourceState)
    }
}
