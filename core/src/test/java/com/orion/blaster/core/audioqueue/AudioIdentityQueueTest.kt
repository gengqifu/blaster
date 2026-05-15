package com.orion.blaster.core.audioqueue

import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.store.InMemoryFeatureRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioIdentityQueueTest {
    private val repository = InMemoryFeatureRepository()
    private val queue = AudioIdentityQueue(repository)

    @Test
    fun candidate_and_unassociated_songs_enter_audio_identity_queue() {
        saveQueueCandidate("song-candidate", LifecycleState.CANDIDATE_ASSOCIATED)
        saveQueueCandidate("song-unassociated", LifecycleState.UNASSOCIATED)

        val items = queue.pendingItems()

        val ids = items.map { it.localSongId }.toSet()
        assertEquals(setOf("song-candidate", "song-unassociated"), ids)
        val candidate = items.first { it.localSongId == "song-candidate" }
        assertEquals("content://song-candidate", candidate.uri)
        assertEquals(180000L, candidate.durationMs)
        assertEquals("sig-song-candidate", candidate.contentSignature)
        assertEquals("song-candidate", candidate.basicInfo.localSongId)
        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, candidate.currentLifecycleState)
        assertEquals(1, candidate.retryCount)
    }

    @Test
    fun terminal_and_waiting_states_do_not_enter_audio_identity_queue() {
        listOf(
            LifecycleState.RELIABLY_ASSOCIATED,
            LifecycleState.FAILED,
            LifecycleState.SKIPPED,
            LifecycleState.WAITING_TO_CONTINUE,
            LifecycleState.OUTDATED,
            LifecycleState.LOCAL_FEATURE_READY,
        ).forEachIndexed { index, state ->
            saveQueueCandidate("song-$index", state)
        }

        assertTrue(queue.pendingItems().isEmpty())
    }

    @Test
    fun item_is_skipped_when_required_song_or_basic_info_is_missing() {
        repository.saveMatchResult(
            localSongId = "missing-song",
            matchResponse = MatchResponse(MatchResult.NONE, association = null),
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )
        repository.saveLocalSong(
            LocalSong(
                localSongId = "missing-basic-info",
                title = null,
                artist = null,
                album = null,
                durationMs = null,
                sourceState = SourceState.AVAILABLE,
            ),
        )
        repository.saveMatchResult(
            localSongId = "missing-basic-info",
            matchResponse = MatchResponse(MatchResult.NONE, association = null),
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )

        assertTrue(queue.pendingItems().isEmpty())
    }

    private fun saveQueueCandidate(localSongId: String, state: LifecycleState) {
        repository.saveLocalSong(
            LocalSong(
                localSongId = localSongId,
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 180000L,
                sourceState = SourceState.AVAILABLE,
                uri = "content://$localSongId",
                contentSignature = "sig-$localSongId",
            ),
        )
        repository.saveBasicInfo(
            BasicSongInfo(
                localSongId = localSongId,
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 180000L,
            ),
        )
        repository.saveMatchResult(
            localSongId = localSongId,
            matchResponse = MatchResponse(MatchResult.NONE, association = null),
            lifecycleState = state,
            retryCount = 1,
            lastReason = null,
            updatedAtMs = 1L,
        )
    }
}
