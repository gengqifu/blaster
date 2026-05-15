package com.orion.blaster.core.pipeline

import com.orion.blaster.core.gateway.AudioIdentityMatchRequest
import com.orion.blaster.core.gateway.BasicInfoMatchRequest
import com.orion.blaster.core.gateway.CloudMatchGateway
import com.orion.blaster.core.mock.MockCloudMatchGateway
import com.orion.blaster.core.mock.MockMatchRule
import com.orion.blaster.core.mock.MockScenario
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.result.ResultProvider
import com.orion.blaster.core.scanner.TestLocalSongScanner
import com.orion.blaster.core.scanner.TestSongRecord
import com.orion.blaster.core.store.InMemoryFeatureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mvp2IntegrationTest {

    @Test
    fun fixed_chain_reliable_candidate_none_are_queryable() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = MockCloudMatchGateway(
            rules = listOf(
                MockMatchRule(localSongId = "song-reliable", forceScenario = MockScenario.RELIABLE),
                MockMatchRule(localSongId = "song-candidate", forceScenario = MockScenario.CANDIDATE),
            ),
        )
        val pipeline = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = TestLocalSongScanner(
                records = listOf(
                    record(songId = "song-reliable", signature = "sig-r"),
                    record(songId = "song-candidate", signature = "sig-c"),
                    record(songId = "song-none", signature = "sig-n"),
                ),
            ),
        )
        pipeline.scanAndProcess(ScanSource.TEST_RESOURCES)

        val provider = ResultProvider(repository)
        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, provider.getResult("song-reliable")?.lifecycleState)
        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, provider.getResult("song-candidate")?.lifecycleState)
        assertEquals(LifecycleState.UNASSOCIATED, provider.getResult("song-none")?.lifecycleState)
    }

    @Test
    fun fixed_chain_error_retries_and_ends_failed_with_reason() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = MockCloudMatchGateway()
        val pipeline = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = scanner(songId = "song-error", signature = "sig-e"),
            maxRetryCount = 1,
        )

        pipeline.scanAndProcess(ScanSource.TEST_RESOURCES, forceScenario = "ERROR")

        assertEquals(LifecycleState.FAILED, repository.getResult("song-error")?.lifecycleState)
        assertEquals("error", repository.getLastReason("song-error"))
        assertEquals(2, repository.getRetryCount("song-error"))
    }

    @Test
    fun fixed_chain_unchanged_scan_skips_rematch() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val countingGateway = CountingGateway(MockCloudMatchGateway())
        val pipeline = FeaturePipeline(
            gateway = countingGateway,
            repository = repository,
            testResourceScanner = scanner(songId = "song-stable", signature = "sig-stable"),
        )

        val first = pipeline.scanAndProcess(ScanSource.TEST_RESOURCES, forceScenario = "RELIABLE")
        val second = pipeline.scanAndProcess(ScanSource.TEST_RESOURCES, forceScenario = "CANDIDATE")

        assertEquals(1, first.matchedCount)
        assertEquals(1, countingGateway.basicInfoCallCount)
        assertEquals(1, second.unchangedCount)
        assertEquals(0, second.matchedCount)
        assertEquals(1, countingGateway.basicInfoCallCount)
        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, repository.getResult("song-stable")?.lifecycleState)
    }

    @Test
    fun fixed_chain_signature_change_triggers_rematch() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val countingGateway = CountingGateway(MockCloudMatchGateway())
        val firstPipeline = FeaturePipeline(
            gateway = countingGateway,
            repository = repository,
            testResourceScanner = scanner(songId = "song-change", signature = "sig-1"),
        )
        firstPipeline.scanAndProcess(ScanSource.TEST_RESOURCES, forceScenario = "RELIABLE")

        val secondPipeline = FeaturePipeline(
            gateway = countingGateway,
            repository = repository,
            testResourceScanner = scanner(songId = "song-change", signature = "sig-2"),
        )
        val second = secondPipeline.scanAndProcess(ScanSource.TEST_RESOURCES, forceScenario = "CANDIDATE")

        assertEquals(1, second.changedCount)
        assertEquals(1, second.matchedCount)
        assertTrue(countingGateway.basicInfoCallCount >= 2)
        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, repository.getResult("song-change")?.lifecycleState)
    }

    private fun scanner(songId: String, signature: String): TestLocalSongScanner {
        return TestLocalSongScanner(
            records = listOf(
                record(songId, signature),
            ),
        )
    }

    private fun record(songId: String, signature: String): TestSongRecord {
        return TestSongRecord(
            localSongId = songId,
            uri = "content://media/$songId",
            title = "title-$songId",
            artist = "artist-$songId",
            album = "album-$songId",
            durationMs = 1000L,
            sourceState = SourceState.AVAILABLE,
            contentSignature = signature,
        )
    }

    private class CountingGateway(
        private val delegate: CloudMatchGateway,
    ) : CloudMatchGateway {
        var basicInfoCallCount: Int = 0
            private set

        override suspend fun matchByBasicInfo(request: BasicInfoMatchRequest): MatchResponse {
            basicInfoCallCount += 1
            return delegate.matchByBasicInfo(request)
        }

        override suspend fun matchByAudioIdentity(request: AudioIdentityMatchRequest): MatchResponse {
            return delegate.matchByAudioIdentity(request)
        }
    }
}
