package com.orion.blaster.core.featurequeue

import com.orion.blaster.core.featuretoggle.LocalFeatureToggle
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

class LocalFeatureQueueTest {
    private val repository = InMemoryFeatureRepository()

    @Test
    fun only_unassociated_enters_queue_by_default() {
        saveQueueCandidate("song-unassociated", LifecycleState.UNASSOCIATED)
        saveQueueCandidate("song-candidate", LifecycleState.CANDIDATE_ASSOCIATED)
        saveQueueCandidate("song-reliable", LifecycleState.RELIABLY_ASSOCIATED)

        val queue = LocalFeatureQueue(repository)
        val ids = queue.pendingItems().map { it.localSongId }

        assertEquals(listOf("song-unassociated"), ids)
    }

    @Test
    fun candidate_can_enter_when_toggle_enabled() {
        saveQueueCandidate("song-unassociated", LifecycleState.UNASSOCIATED)
        saveQueueCandidate("song-candidate", LifecycleState.CANDIDATE_ASSOCIATED)

        val queue = LocalFeatureQueue(
            repository = repository,
            toggleProvider = { LocalFeatureToggle(enabled = true, includeCandidateAssociated = true) },
        )
        val ids = queue.pendingItems().map { it.localSongId }.toSet()

        assertEquals(setOf("song-unassociated", "song-candidate"), ids)
    }

    @Test
    fun queue_is_empty_when_toggle_disabled() {
        saveQueueCandidate("song-unassociated", LifecycleState.UNASSOCIATED)

        val queue = LocalFeatureQueue(
            repository = repository,
            toggleProvider = { LocalFeatureToggle(enabled = false) },
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
