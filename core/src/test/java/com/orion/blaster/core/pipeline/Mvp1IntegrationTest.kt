package com.orion.blaster.core.pipeline

import com.orion.blaster.core.mock.MockCloudMatchGateway
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.result.ResultProvider
import com.orion.blaster.core.store.InMemoryFeatureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Mvp1IntegrationTest {

    @Test
    fun all_mvp1_scenarios_and_manual_outdated_are_queryable() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val pipeline = FeaturePipeline(
            gateway = MockCloudMatchGateway(),
            repository = repository,
            maxRetryCount = 1,
            clock = { System.currentTimeMillis() },
        )
        val provider = ResultProvider(repository)

        val scenarioToExpected = linkedMapOf(
            "RELIABLE" to LifecycleState.RELIABLY_ASSOCIATED,
            "CANDIDATE" to LifecycleState.CANDIDATE_ASSOCIATED,
            "NONE" to LifecycleState.UNASSOCIATED,
            "ERROR" to LifecycleState.FAILED,
            "TIMEOUT" to LifecycleState.FAILED,
            "DEGRADED" to LifecycleState.WAITING_TO_CONTINUE,
        )

        scenarioToExpected.forEach { (scenario, expectedState) ->
            val songId = "song-$scenario"
            val state = pipeline.process(localSong(songId), forceScenario = scenario)
            val result = provider.getResult(songId)

            assertEquals(expectedState, state)
            assertEquals(expectedState, result?.lifecycleState)
        }

        pipeline.markOutdated("song-RELIABLE")
        val outdatedResult = provider.getResult("song-RELIABLE")
        assertNotNull(outdatedResult?.association)
        assertEquals(LifecycleState.OUTDATED, outdatedResult?.lifecycleState)
    }

    private fun localSong(songId: String): LocalSong {
        return LocalSong(
            localSongId = songId,
            title = "title-$songId",
            artist = "artist-$songId",
            album = "album-$songId",
            durationMs = 180000L,
            sourceState = SourceState.AVAILABLE,
        )
    }
}
