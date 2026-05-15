package com.orion.blaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchModelsTest {
    @Test
    fun reliable_maps_to_reliable_association() {
        val lifecycleState = MatchResponse(result = MatchResult.RELIABLE, association = null).toLifecycleState()
        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, lifecycleState)
    }

    @Test
    fun candidate_maps_to_candidate_association_and_not_reliable() {
        val lifecycleState = MatchResponse(result = MatchResult.CANDIDATE, association = null).toLifecycleState()
        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, lifecycleState)

        val result = LocalSongResult(
            localSongId = "song-1",
            lifecycleState = lifecycleState,
            association = null,
            updatedAtMs = 1L,
        )
        assertFalse(result.isReliablyAssociated())
    }

    @Test
    fun none_and_error_are_distinct_semantics() {
        val noneState = MatchResponse(result = MatchResult.NONE, association = null).toLifecycleState()
        val errorState = MatchResponse(result = MatchResult.ERROR, association = null, rejectReason = "network").toLifecycleState()

        assertEquals(LifecycleState.UNASSOCIATED, noneState)
        assertEquals(LifecycleState.FAILED, errorState)
    }

    @Test
    fun degraded_maps_to_waiting_to_continue() {
        val state = MatchResponse(
            result = MatchResult.ERROR,
            association = null,
            rejectReason = "degraded",
        ).toLifecycleState()

        assertEquals(LifecycleState.WAITING_TO_CONTINUE, state)
    }

    @Test
    fun outdated_and_waiting_to_continue_are_distinct() {
        assertTrue(LifecycleState.OUTDATED != LifecycleState.WAITING_TO_CONTINUE)
    }

    @Test
    fun local_song_extended_fields_are_optional_for_mvp1_compatibility() {
        val song = LocalSong(
            localSongId = "song-compat",
            title = "title",
            artist = "artist",
            album = "album",
            durationMs = 123L,
            sourceState = SourceState.AVAILABLE,
        )

        assertNull(song.uri)
        assertNull(song.sizeBytes)
        assertNull(song.dateModified)
        assertNull(song.mimeType)
        assertNull(song.contentSignature)
    }

    @Test
    fun basic_song_info_has_default_source_and_quality_flags() {
        val info = BasicSongInfo(
            localSongId = "song-1",
            title = "title",
            artist = "artist",
            album = "album",
            durationMs = 1000L,
        )

        assertEquals(BasicInfoSource.TEST_CONSTRUCTED, info.source)
        assertTrue(info.qualityFlags.isEmpty())
    }

    @Test
    fun quality_flags_and_source_can_be_assigned_explicitly() {
        val info = BasicSongInfo(
            localSongId = "song-2",
            title = null,
            artist = null,
            album = null,
            durationMs = null,
            source = BasicInfoSource.FILENAME_FALLBACK,
            qualityFlags = setOf(QualityFlag.MISSING_FIELD, QualityFlag.FALLBACK_USED),
        )

        assertEquals(BasicInfoSource.FILENAME_FALLBACK, info.source)
        assertTrue(info.qualityFlags.contains(QualityFlag.MISSING_FIELD))
        assertTrue(info.qualityFlags.contains(QualityFlag.FALLBACK_USED))
    }
}
