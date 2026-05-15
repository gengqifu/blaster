package com.orion.blaster.core.metadata

import com.orion.blaster.core.model.BasicInfoSource
import com.orion.blaster.core.model.QualityFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicInfoExtractorTest {
    private val extractor = BasicInfoExtractor()

    @Test
    fun media_store_fields_are_preferred_when_available() {
        val info = extractor.extract(
            BasicInfoExtractionInput(
                localSongId = "song-1",
                mediaStore = MetadataCandidate(
                    title = "  Hello  ",
                    artist = "Adele",
                    album = "25",
                    durationMs = 295000L,
                ),
                metadataRetriever = MetadataCandidate(
                    title = "Other",
                    artist = "Other",
                    album = "Other",
                    durationMs = 111L,
                ),
            ),
        )

        assertEquals("Hello", info.title)
        assertEquals("Adele", info.artist)
        assertEquals("25", info.album)
        assertEquals(295000L, info.durationMs)
        assertEquals(BasicInfoSource.MEDIA_STORE, info.source)
    }

    @Test
    fun metadata_retriever_is_used_when_media_store_is_missing() {
        val info = extractor.extract(
            BasicInfoExtractionInput(
                localSongId = "song-2",
                mediaStore = MetadataCandidate(),
                metadataRetriever = MetadataCandidate(
                    title = "Track",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 120000L,
                ),
            ),
        )

        assertEquals(BasicInfoSource.METADATA_RETRIEVER, info.source)
        assertEquals("Track", info.title)
    }

    @Test
    fun filename_fallback_is_used_and_flagged() {
        val info = extractor.extract(
            BasicInfoExtractionInput(
                localSongId = "song-3",
                fileName = "Coldplay - Yellow.mp3",
            ),
        )

        assertEquals(BasicInfoSource.FILENAME_FALLBACK, info.source)
        assertEquals("Yellow", info.title)
        assertEquals("Coldplay", info.artist)
        assertTrue(info.qualityFlags.contains(QualityFlag.FALLBACK_USED))
        assertTrue(info.qualityFlags.contains(QualityFlag.MISSING_FIELD))
    }

    @Test
    fun garbled_and_conflicting_metadata_are_flagged() {
        val info = extractor.extract(
            BasicInfoExtractionInput(
                localSongId = "song-4",
                mediaStore = MetadataCandidate(
                    title = "Hi\uFFFD",
                    artist = "Artist A",
                ),
                metadataRetriever = MetadataCandidate(
                    title = "Hello",
                    artist = "Artist B",
                ),
            ),
        )

        assertTrue(info.qualityFlags.contains(QualityFlag.GARBLED_TEXT))
        assertTrue(info.qualityFlags.contains(QualityFlag.CONFLICTING_METADATA))
        assertEquals("Hello", info.title)
    }
}
