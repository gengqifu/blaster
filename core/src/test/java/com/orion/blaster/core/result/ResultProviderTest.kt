package com.orion.blaster.core.result

import com.orion.blaster.core.model.AssociationStage
import com.orion.blaster.core.model.CloudAssociation
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalFeature
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.store.InMemoryFeatureRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultProviderTest {

    @Test
    fun candidate_is_not_marked_as_reliably_associated() {
        val repository = InMemoryFeatureRepository()
        repository.saveMatchResult(
            localSongId = "song-1",
            matchResponse = MatchResponse(result = MatchResult.CANDIDATE, association = null),
            lifecycleState = LifecycleState.CANDIDATE_ASSOCIATED,
            retryCount = 0,
            lastReason = "candidate",
            updatedAtMs = 1L,
        )

        val provider = ResultProvider(repository)

        assertFalse(provider.isReliablyAssociated("song-1"))
    }

    @Test
    fun failed_skipped_outdated_can_read_reason_and_state() {
        val repository = InMemoryFeatureRepository()
        repository.saveMatchResult(
            localSongId = "song-failed",
            matchResponse = MatchResponse(result = MatchResult.ERROR, association = null, rejectReason = "timeout"),
            lifecycleState = LifecycleState.FAILED,
            retryCount = 2,
            lastReason = "timeout",
            updatedAtMs = 1L,
        )
        repository.saveLifecycleState(
            localSongId = "song-skipped",
            lifecycleState = LifecycleState.SKIPPED,
            retryCount = 0,
            lastReason = "policy_disabled",
            updatedAtMs = 2L,
        )
        repository.saveMatchResult(
            localSongId = "song-outdated",
            matchResponse = MatchResponse(
                result = MatchResult.RELIABLE,
                association = CloudAssociation(
                    cloudSongId = "cloud-song-outdated",
                    stage = AssociationStage.BASIC_INFO,
                    isReliable = true,
                ),
            ),
            lifecycleState = LifecycleState.RELIABLY_ASSOCIATED,
            retryCount = 0,
            lastReason = "ok",
            updatedAtMs = 3L,
        )
        repository.markOutdated("song-outdated", 4L)

        val provider = ResultProvider(repository)

        assertTrue(provider.isFailed("song-failed"))
        assertTrue(provider.isSkipped("song-skipped"))
        assertTrue(provider.isOutdated("song-outdated"))
        assertEquals("timeout", provider.getResult("song-failed")?.lastReason)
        assertEquals("policy_disabled", provider.getResult("song-skipped")?.lastReason)
        assertEquals("ok", provider.getResult("song-outdated")?.lastReason)
    }

    @Test
    fun processing_state_is_exposed() {
        val repository = InMemoryFeatureRepository()
        repository.saveLifecycleState(
            localSongId = "song-processing",
            lifecycleState = LifecycleState.BASIC_MATCHING,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )

        val provider = ResultProvider(repository)

        assertTrue(provider.isProcessing("song-processing"))
    }

    @Test
    fun local_feature_ready_exposes_local_feature_but_not_reliable_association() {
        val repository = InMemoryFeatureRepository()
        repository.saveMatchResult(
            localSongId = "song-local-feature",
            matchResponse = MatchResponse(result = MatchResult.NONE, association = null),
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )
        repository.saveLocalFeature(
            localSongId = "song-local-feature",
            localFeature = LocalFeature(
                embedding = floatArrayOf(0.1f, 0.2f),
                modelName = "YAMNet",
                modelVersion = "tfhub-lite-1",
                featureSchemaVersion = 1,
                generatedAtMs = 2L,
            ),
            updatedAtMs = 2L,
        )

        val provider = ResultProvider(repository)
        val result = provider.getResult("song-local-feature")

        assertEquals(LifecycleState.LOCAL_FEATURE_READY, result?.lifecycleState)
        assertFalse(provider.isReliablyAssociated("song-local-feature"))
        assertTrue(result?.localFeature?.embedding?.isNotEmpty() == true)
    }
}
