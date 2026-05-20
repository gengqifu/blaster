package com.orion.blaster.core.pipeline

import com.orion.blaster.core.audioidentity.AudioIdentifyInputGenerator
import com.orion.blaster.core.audioidentity.AudioIdentifyInputResult
import com.orion.blaster.core.audioqueue.AudioIdentityQueue
import com.orion.blaster.core.audioqueue.AudioIdentityQueueItem
import com.orion.blaster.core.gateway.AudioIdentityMatchRequest
import com.orion.blaster.core.gateway.NoopCloudMatchGateway
import com.orion.blaster.core.mock.MockCloudMatchGateway
import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalFeature
import com.orion.blaster.core.model.LocalFeatureDiagnostics
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.scheduler.AudioIdentityDeviceState
import com.orion.blaster.core.scheduler.AudioIdentityScheduler
import com.orion.blaster.core.store.FeatureRepository
import com.orion.blaster.core.store.InMemoryFeatureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioIdentityPipelineTest {
    @Test
    fun audio_identity_reliable_writes_identifying_matching_and_final_state() = runBlocking {
        val repository = RecordingRepository()
        seedPendingSong(repository, LifecycleState.UNASSOCIATED)
        val pipeline = pipeline(repository, forceScenario = "RELIABLE")

        val summary = pipeline.processAudioIdentityQueue(
            scheduler = AudioIdentityScheduler(AudioIdentityQueue(repository)),
            forceScenario = "RELIABLE",
            audioCompareEnabled = true,
        )

        assertEquals(1, summary.scheduledCount)
        assertEquals(1, summary.extractedCount)
        assertEquals(1, summary.comparedCount)
        assertEquals(0, summary.compareSkippedCount)
        assertEquals(1, summary.reliableCount)
        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, repository.getResult("song-audio")?.lifecycleState)
        assertNotNull(repository.getAudioIdentitySummary("song-audio"))
        assertTrue(repository.recordedStates.contains(LifecycleState.AUDIO_IDENTIFYING))
        assertTrue(repository.recordedStates.contains(LifecycleState.AUDIO_MATCHING))
        assertTrue(repository.recordedStates.contains(LifecycleState.RELIABLY_ASSOCIATED))
    }

    @Test
    fun audio_identity_candidate_and_none_keep_non_reliable_semantics() = runBlocking {
        val candidateRepository = RecordingRepository()
        seedPendingSong(candidateRepository, LifecycleState.UNASSOCIATED)
        pipeline(candidateRepository, forceScenario = "CANDIDATE").processAudioIdentityQueue(
            scheduler = AudioIdentityScheduler(AudioIdentityQueue(candidateRepository)),
            forceScenario = "CANDIDATE",
            audioCompareEnabled = true,
        )

        val noneRepository = RecordingRepository()
        seedPendingSong(noneRepository, LifecycleState.CANDIDATE_ASSOCIATED)
        pipeline(noneRepository, forceScenario = "NONE").processAudioIdentityQueue(
            scheduler = AudioIdentityScheduler(AudioIdentityQueue(noneRepository)),
            forceScenario = "NONE",
            audioCompareEnabled = true,
        )

        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, candidateRepository.getResult("song-audio")?.lifecycleState)
        assertEquals(LifecycleState.UNASSOCIATED, noneRepository.getResult("song-audio")?.lifecycleState)
    }

    @Test
    fun audio_identity_error_and_timeout_retry_until_failed() = runBlocking {
        listOf("ERROR", "TIMEOUT").forEach { scenario ->
            val repository = RecordingRepository()
            seedPendingSong(repository, LifecycleState.UNASSOCIATED)
            val pipeline = pipeline(repository, forceScenario = scenario, maxRetryCount = 1)

            val summary = pipeline.processAudioIdentityQueue(
                scheduler = AudioIdentityScheduler(AudioIdentityQueue(repository)),
                forceScenario = scenario,
                audioCompareEnabled = true,
            )

            assertEquals(1, summary.failedCount)
            assertEquals(LifecycleState.FAILED, repository.getResult("song-audio")?.lifecycleState)
            assertEquals(2, repository.getRetryCount("song-audio"))
        }
    }

    @Test
    fun audio_identity_degraded_waits_without_technical_retry() = runBlocking {
        val repository = RecordingRepository()
        seedPendingSong(repository, LifecycleState.UNASSOCIATED)
        val pipeline = pipeline(repository, forceScenario = "DEGRADED")

        pipeline.processAudioIdentityQueue(
            scheduler = AudioIdentityScheduler(AudioIdentityQueue(repository)),
            forceScenario = "DEGRADED",
            audioCompareEnabled = true,
        )

        assertEquals(LifecycleState.WAITING_TO_CONTINUE, repository.getResult("song-audio")?.lifecycleState)
        assertEquals(0, repository.getRetryCount("song-audio"))
        assertEquals("degraded", repository.getLastReason("song-audio"))
    }

    @Test
    fun scheduler_guard_waits_without_running_generator() = runBlocking {
        val repository = RecordingRepository()
        seedPendingSong(repository, LifecycleState.UNASSOCIATED)
        val generator = FakeGenerator()
        val pipeline = FeaturePipeline(
            gateway = MockCloudMatchGateway(),
            repository = repository,
            audioIdentifyInputGenerator = generator,
        )

        val summary = pipeline.processAudioIdentityQueue(
            scheduler = AudioIdentityScheduler(
                queue = AudioIdentityQueue(repository),
                deviceStateProvider = { AudioIdentityDeviceState(lowBattery = true) },
            ),
            forceScenario = "RELIABLE",
            audioCompareEnabled = true,
        )

        assertEquals(0, summary.scheduledCount)
        assertEquals(1, summary.waitingCount)
        assertEquals(LifecycleState.WAITING_TO_CONTINUE, repository.getResult("song-audio")?.lifecycleState)
        assertEquals("low_battery", repository.getLastReason("song-audio"))
        assertEquals(0, generator.callCount)
    }

    @Test
    fun compare_disabled_extracts_without_gateway_calling_and_without_failed() = runBlocking {
        val repository = RecordingRepository()
        seedPendingSong(repository, LifecycleState.UNASSOCIATED)
        val generator = FakeGenerator(forceScenario = "RELIABLE")
        val pipeline = FeaturePipeline(
            gateway = NoopCloudMatchGateway(),
            repository = repository,
            audioIdentifyInputGenerator = generator,
        )

        val summary = pipeline.processAudioIdentityQueue(
            scheduler = AudioIdentityScheduler(AudioIdentityQueue(repository)),
            forceScenario = "RELIABLE",
            audioCompareEnabled = false,
        )

        assertEquals(1, summary.scheduledCount)
        assertEquals(1, summary.extractedCount)
        assertEquals(0, summary.comparedCount)
        assertEquals(1, summary.compareSkippedCount)
        assertEquals(0, summary.failedCount)
        assertEquals(LifecycleState.UNASSOCIATED, repository.getResult("song-audio")?.lifecycleState)
        assertEquals("audio_extracted_compare_disabled", repository.getLastReason("song-audio"))
        assertNotNull(repository.getAudioIdentitySummary("song-audio"))
        assertEquals(1, generator.callCount)
    }

    @Test
    fun compare_enabled_with_noop_gateway_records_service_not_configured() = runBlocking {
        val repository = RecordingRepository()
        seedPendingSong(repository, LifecycleState.UNASSOCIATED)
        val generator = FakeGenerator()
        val pipeline = FeaturePipeline(
            gateway = NoopCloudMatchGateway(),
            repository = repository,
            audioIdentifyInputGenerator = generator,
            maxRetryCount = 0,
        )

        val summary = pipeline.processAudioIdentityQueue(
            scheduler = AudioIdentityScheduler(AudioIdentityQueue(repository)),
            audioCompareEnabled = true,
        )

        assertEquals(1, summary.extractedCount)
        assertEquals(1, summary.comparedCount)
        assertEquals(0, summary.compareSkippedCount)
        assertEquals(1, summary.failedCount)
        assertEquals("service_not_configured", repository.getLastReason("song-audio"))
    }

    private fun pipeline(
        repository: FeatureRepository,
        forceScenario: String,
        maxRetryCount: Int = 2,
    ): FeaturePipeline {
        return FeaturePipeline(
            gateway = MockCloudMatchGateway(),
            repository = repository,
            audioIdentifyInputGenerator = FakeGenerator(forceScenario = forceScenario),
            maxRetryCount = maxRetryCount,
            clock = { 100L },
        )
    }

    private fun seedPendingSong(repository: FeatureRepository, state: LifecycleState) {
        repository.saveLocalSong(
            LocalSong(
                localSongId = "song-audio",
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 120000L,
                sourceState = SourceState.AVAILABLE,
                uri = "content://song-audio",
                contentSignature = "sig-audio",
            ),
        )
        repository.saveBasicInfo(
            BasicSongInfo(
                localSongId = "song-audio",
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 120000L,
            ),
        )
        repository.saveMatchResult(
            localSongId = "song-audio",
            matchResponse = MatchResponse(MatchResult.NONE, association = null),
            lifecycleState = state,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )
    }

    private class FakeGenerator(
        private val forceScenario: String? = null,
    ) : AudioIdentifyInputGenerator {
        var callCount = 0
            private set

        override fun generate(
            item: AudioIdentityQueueItem,
            forceScenario: String?,
        ): AudioIdentifyInputResult {
            callCount += 1
            val scenario = forceScenario ?: this.forceScenario
            return AudioIdentifyInputResult.Success(
                request = AudioIdentityMatchRequest(
                    localSongId = item.localSongId,
                    durationMs = item.durationMs,
                    clipPolicy = "normal:middle-60s",
                    algorithm = "chromaprint-compatible",
                    algorithmVersion = "mvp3-test",
                    payloadEncoding = "chromaprint-base64",
                    payload = "AQID".toByteArray(),
                    basicInfo = item.basicInfo,
                    forceScenario = scenario,
                ),
                summary = AudioIdentitySummary(
                    localSongId = item.localSongId,
                    algorithm = "chromaprint-compatible",
                    algorithmVersion = "mvp3-test",
                    clipPolicy = "normal:middle-60s",
                    payloadEncoding = "chromaprint-base64",
                    payloadDigest = "sha256:test",
                    costMs = 1L,
                    lastReason = null,
                    updatedAtMs = 100L,
                ),
            )
        }
    }

    private class RecordingRepository : FeatureRepository {
        private val delegate = InMemoryFeatureRepository()
        val recordedStates = mutableListOf<LifecycleState>()

        override fun saveLocalSong(localSong: LocalSong) = delegate.saveLocalSong(localSong)
        override fun saveBasicInfo(basicSongInfo: BasicSongInfo) = delegate.saveBasicInfo(basicSongInfo)
        override fun getLocalSong(localSongId: String) = delegate.getLocalSong(localSongId)
        override fun getBasicInfo(localSongId: String) = delegate.getBasicInfo(localSongId)
        override fun saveContentSignature(localSongId: String, contentSignature: String?) =
            delegate.saveContentSignature(localSongId, contentSignature)

        override fun getContentSignature(localSongId: String) = delegate.getContentSignature(localSongId)
        override fun saveAudioIdentitySummary(summary: AudioIdentitySummary) =
            delegate.saveAudioIdentitySummary(summary)

        override fun getAudioIdentitySummary(localSongId: String) = delegate.getAudioIdentitySummary(localSongId)
        override fun saveLocalFeature(localSongId: String, localFeature: LocalFeature, updatedAtMs: Long) =
            delegate.saveLocalFeature(localSongId, localFeature, updatedAtMs)

        override fun getLocalFeature(localSongId: String) = delegate.getLocalFeature(localSongId)
        override fun saveLocalFeatureDiagnostics(localSongId: String, diagnostics: LocalFeatureDiagnostics) =
            delegate.saveLocalFeatureDiagnostics(localSongId, diagnostics)

        override fun getLocalFeatureDiagnostics(localSongId: String) =
            delegate.getLocalFeatureDiagnostics(localSongId)

        override fun markLocalFeatureOutdatedIfVersionChanged(
            localSongId: String,
            currentModelVersion: String,
            currentFeatureSchemaVersion: Int,
            updatedAtMs: Long,
            lastReason: String?,
        ) = delegate.markLocalFeatureOutdatedIfVersionChanged(
            localSongId = localSongId,
            currentModelVersion = currentModelVersion,
            currentFeatureSchemaVersion = currentFeatureSchemaVersion,
            updatedAtMs = updatedAtMs,
            lastReason = lastReason,
        )

        override fun saveMatchResult(
            localSongId: String,
            matchResponse: MatchResponse,
            lifecycleState: LifecycleState,
            retryCount: Int,
            lastReason: String?,
            updatedAtMs: Long,
        ) {
            recordedStates += lifecycleState
            delegate.saveMatchResult(localSongId, matchResponse, lifecycleState, retryCount, lastReason, updatedAtMs)
        }

        override fun saveLifecycleState(
            localSongId: String,
            lifecycleState: LifecycleState,
            retryCount: Int,
            lastReason: String?,
            updatedAtMs: Long,
        ) {
            recordedStates += lifecycleState
            delegate.saveLifecycleState(localSongId, lifecycleState, retryCount, lastReason, updatedAtMs)
        }

        override fun markOutdated(localSongId: String, updatedAtMs: Long) =
            delegate.markOutdated(localSongId, updatedAtMs)

        override fun markDeletedOrUnavailable(
            localSongId: String,
            sourceState: SourceState,
            updatedAtMs: Long,
            lastReason: String?,
        ) = delegate.markDeletedOrUnavailable(localSongId, sourceState, updatedAtMs, lastReason)

        override fun getResult(localSongId: String) = delegate.getResult(localSongId)
        override fun getResults(localSongIds: List<String>) = delegate.getResults(localSongIds)
        override fun getByLifecycleStates(states: Set<LifecycleState>) = delegate.getByLifecycleStates(states)
        override fun getAllLocalSongIds() = delegate.getAllLocalSongIds()
        override fun getRetryCount(localSongId: String) = delegate.getRetryCount(localSongId)
        override fun getLastReason(localSongId: String) = delegate.getLastReason(localSongId)
    }
}
