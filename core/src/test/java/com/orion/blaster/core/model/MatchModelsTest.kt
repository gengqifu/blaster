package com.orion.blaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
