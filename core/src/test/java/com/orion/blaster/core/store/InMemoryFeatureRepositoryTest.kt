package com.orion.blaster.core.store

import com.orion.blaster.core.model.AssociationStage
import com.orion.blaster.core.model.CloudAssociation
import com.orion.blaster.core.model.CloudCandidate
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryFeatureRepositoryTest {
    private val repository = InMemoryFeatureRepository()

    @Test
    fun write_and_query_single_and_batch_results() {
        repository.saveMatchResult(
            localSongId = "song-1",
            matchResponse = reliableResponse("song-1"),
            lifecycleState = LifecycleState.RELIABLY_ASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 100L,
        )

        repository.saveMatchResult(
            localSongId = "song-2",
            matchResponse = noneResponse(),
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = 1,
            lastReason = "none",
            updatedAtMs = 200L,
        )

        val single = repository.getResult("song-1")
        val batch = repository.getResults(listOf("song-1", "song-2", "song-missing"))

        assertNotNull(single)
        assertEquals(2, batch.size)
        assertEquals("song-1", single?.localSongId)
        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, single?.lifecycleState)
    }

    @Test
    fun candidate_does_not_override_existing_reliable_association() {
        repository.saveMatchResult(
            localSongId = "song-3",
            matchResponse = reliableResponse("song-3"),
            lifecycleState = LifecycleState.RELIABLY_ASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 100L,
        )

        repository.saveMatchResult(
            localSongId = "song-3",
            matchResponse = candidateResponse("song-3"),
            lifecycleState = LifecycleState.CANDIDATE_ASSOCIATED,
            retryCount = 1,
            lastReason = "candidate",
            updatedAtMs = 200L,
        )

        val result = repository.getResult("song-3")
        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, result?.lifecycleState)
        assertNotNull(result?.association)
        assertEquals(1, repository.getRetryCount("song-3"))
        assertEquals("candidate", repository.getLastReason("song-3"))
    }

    @Test
    fun mark_outdated_keeps_previous_diagnostics_and_association() {
        repository.saveMatchResult(
            localSongId = "song-4",
            matchResponse = reliableResponse("song-4"),
            lifecycleState = LifecycleState.RELIABLY_ASSOCIATED,
            retryCount = 2,
            lastReason = "network_recovered",
            updatedAtMs = 100L,
        )

        repository.markOutdated(localSongId = "song-4", updatedAtMs = 999L)

        val result = repository.getResult("song-4")
        assertEquals(LifecycleState.OUTDATED, result?.lifecycleState)
        assertNotNull(result?.association)
        assertEquals("network_recovered", repository.getLastReason("song-4"))
        assertEquals(2, repository.getRetryCount("song-4"))
        assertEquals(999L, result?.updatedAtMs)
    }

    @Test
    fun lifecycle_state_update_keeps_existing_association() {
        repository.saveMatchResult(
            localSongId = "song-5",
            matchResponse = reliableResponse("song-5"),
            lifecycleState = LifecycleState.RELIABLY_ASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 100L,
        )

        repository.saveLifecycleState(
            localSongId = "song-5",
            lifecycleState = LifecycleState.WAITING_TO_CONTINUE,
            retryCount = 1,
            lastReason = "degraded",
            updatedAtMs = 120L,
        )

        val result = repository.getResult("song-5")
        assertEquals(LifecycleState.WAITING_TO_CONTINUE, result?.lifecycleState)
        assertNotNull(result?.association)
        assertEquals("degraded", repository.getLastReason("song-5"))
    }

    private fun reliableResponse(localSongId: String): MatchResponse {
        return MatchResponse(
            result = MatchResult.RELIABLE,
            association = CloudAssociation(
                cloudSongId = "cloud-$localSongId",
                stage = AssociationStage.BASIC_INFO,
                isReliable = true,
            ),
        )
    }

    private fun candidateResponse(localSongId: String): MatchResponse {
        return MatchResponse(
            result = MatchResult.CANDIDATE,
            association = null,
            candidates = listOf(
                CloudCandidate(
                    cloudSongId = "candidate-$localSongId",
                    reason = "low_confidence",
                    score = 0.5f,
                ),
            ),
        )
    }

    private fun noneResponse(): MatchResponse {
        return MatchResponse(
            result = MatchResult.NONE,
            association = null,
        )
    }
}
