package com.orion.blaster.core.pipeline

import com.orion.blaster.core.gateway.AudioIdentityMatchRequest
import com.orion.blaster.core.gateway.BasicInfoMatchRequest
import com.orion.blaster.core.gateway.CloudMatchGateway
import com.orion.blaster.core.model.AssociationStage
import com.orion.blaster.core.model.CloudAssociation
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.scanner.ScannedLocalSong
import com.orion.blaster.core.scanner.TestLocalSongScanner
import com.orion.blaster.core.store.InMemoryFeatureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.ArrayDeque

class FeaturePipelineTest {

    @Test
    fun reliable_flows_to_reliably_associated() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(reliableResponse("song-1")))
        val pipeline = FeaturePipeline(gateway, repository, maxRetryCount = 2, clock = { 100L })

        val state = pipeline.process(song("song-1"))

        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, state)
        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, repository.getResult("song-1")?.lifecycleState)
    }

    @Test
    fun candidate_flows_to_candidate_associated() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(candidateResponse()))
        val pipeline = FeaturePipeline(gateway, repository)

        val state = pipeline.process(song("song-2"))

        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, state)
        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, repository.getResult("song-2")?.lifecycleState)
    }

    @Test
    fun none_flows_to_unassociated_without_retry() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(noneResponse()))
        val pipeline = FeaturePipeline(gateway, repository)

        val state = pipeline.process(song("song-3"))

        assertEquals(LifecycleState.UNASSOCIATED, state)
        assertEquals(0, repository.getRetryCount("song-3"))
    }

    @Test
    fun timeout_retries_and_then_failed() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(timeoutResponse(), timeoutResponse(), timeoutResponse()))
        val pipeline = FeaturePipeline(gateway, repository, maxRetryCount = 2)

        val state = pipeline.process(song("song-4"))

        assertEquals(LifecycleState.FAILED, state)
        assertEquals(LifecycleState.FAILED, repository.getResult("song-4")?.lifecycleState)
        assertEquals(3, repository.getRetryCount("song-4"))
        assertEquals("timeout", repository.getLastReason("song-4"))
    }

    @Test
    fun degraded_flows_to_waiting_to_continue_without_retry() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(degradedResponse()))
        val pipeline = FeaturePipeline(gateway, repository, maxRetryCount = 2)

        val state = pipeline.process(song("song-5"))

        assertEquals(LifecycleState.WAITING_TO_CONTINUE, state)
        assertEquals(0, repository.getRetryCount("song-5"))
    }

    @Test
    fun mark_outdated_sets_outdated_state() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(reliableResponse("song-6")))
        val pipeline = FeaturePipeline(gateway, repository, clock = { 999L })

        pipeline.process(song("song-6"))
        pipeline.markOutdated("song-6")

        val result = repository.getResult("song-6")
        assertEquals(LifecycleState.OUTDATED, result?.lifecycleState)
        assertNotNull(result?.association)
    }

    @Test
    fun scan_and_process_new_song_triggers_match_and_stores_result() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(reliableResponse("song-new")))
        val scanner = TestLocalSongScanner(
            records = listOf(
                com.orion.blaster.core.scanner.TestSongRecord(
                    localSongId = "song-new",
                    uri = "content://media/new",
                    title = "Title",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 1000L,
                    sourceState = SourceState.AVAILABLE,
                    contentSignature = "sig-new",
                ),
            ),
        )
        val pipeline = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = scanner,
        )

        val summary = pipeline.scanAndProcess(ScanSource.TEST_RESOURCES)

        assertEquals(1, summary.newCount)
        assertEquals(1, summary.matchedCount)
        assertEquals(LifecycleState.RELIABLY_ASSOCIATED, repository.getResult("song-new")?.lifecycleState)
    }

    @Test
    fun scan_and_process_signature_change_reprocesses_song() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(reliableResponse("song-change"), candidateResponse()))
        val firstScanner = TestLocalSongScanner(
            records = listOf(
                com.orion.blaster.core.scanner.TestSongRecord(
                    localSongId = "song-change",
                    uri = "content://media/change",
                    title = "Title",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 1000L,
                    sourceState = SourceState.AVAILABLE,
                    contentSignature = "sig-1",
                ),
            ),
        )
        val pipeline = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = firstScanner,
        )
        pipeline.scanAndProcess(ScanSource.TEST_RESOURCES)

        val secondScanner = object : com.orion.blaster.core.scanner.LocalSongScanner {
            override fun scan(): List<ScannedLocalSong> = listOf(
                ScannedLocalSong(
                    localSongId = "song-change",
                    uri = "content://media/change",
                    title = "Title",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 1000L,
                    sizeBytes = null,
                    dateModified = null,
                    mimeType = null,
                    sourceState = SourceState.AVAILABLE,
                    contentSignature = "sig-2",
                ),
            )
        }
        val pipelineSecond = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = secondScanner,
        )
        val summary = pipelineSecond.scanAndProcess(ScanSource.TEST_RESOURCES)

        assertEquals(1, summary.changedCount)
        assertEquals(1, summary.matchedCount)
        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, repository.getResult("song-change")?.lifecycleState)
    }

    @Test
    fun scan_and_process_unchanged_song_skips_match() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(reliableResponse("song-same")))
        val scanner = TestLocalSongScanner(
            records = listOf(
                com.orion.blaster.core.scanner.TestSongRecord(
                    localSongId = "song-same",
                    uri = "content://media/same",
                    title = "Title",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 1000L,
                    sourceState = SourceState.AVAILABLE,
                    contentSignature = "sig-stable",
                ),
            ),
        )
        val pipeline = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = scanner,
        )
        pipeline.scanAndProcess(ScanSource.TEST_RESOURCES)
        val summary = pipeline.scanAndProcess(ScanSource.TEST_RESOURCES)

        assertEquals(1, summary.unchangedCount)
        assertEquals(0, summary.matchedCount)
    }

    @Test
    fun scan_and_process_marks_missing_previous_song_as_deleted() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(listOf(reliableResponse("song-keep")))
        val firstScanner = TestLocalSongScanner(
            records = listOf(
                com.orion.blaster.core.scanner.TestSongRecord(
                    localSongId = "song-delete",
                    uri = "content://media/delete",
                    title = "Delete Me",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 1000L,
                    sourceState = SourceState.AVAILABLE,
                    contentSignature = "sig-del",
                ),
            ),
        )
        val firstPipeline = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = firstScanner,
        )
        firstPipeline.scanAndProcess(ScanSource.TEST_RESOURCES)

        val secondScanner = TestLocalSongScanner(records = emptyList())
        val secondPipeline = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = secondScanner,
        )
        val summary = secondPipeline.scanAndProcess(ScanSource.TEST_RESOURCES)

        assertEquals(1, summary.deletedCount)
        assertEquals(LifecycleState.SKIPPED, repository.getResult("song-delete")?.lifecycleState)
    }

    @Test
    fun scan_and_process_unavailable_song_skips_match_and_sets_waiting_state() = runBlocking {
        val repository = InMemoryFeatureRepository()
        val gateway = QueueGateway(emptyList())
        val scanner = TestLocalSongScanner(
            records = listOf(
                com.orion.blaster.core.scanner.TestSongRecord(
                    localSongId = "song-unavailable",
                    uri = null,
                    sourceState = SourceState.UNAVAILABLE,
                    contentSignature = null,
                ),
            ),
        )
        val pipeline = FeaturePipeline(
            gateway = gateway,
            repository = repository,
            testResourceScanner = scanner,
        )

        val summary = pipeline.scanAndProcess(ScanSource.TEST_RESOURCES)

        assertEquals(1, summary.unavailableCount)
        assertEquals(0, summary.matchedCount)
        assertEquals(LifecycleState.WAITING_TO_CONTINUE, repository.getResult("song-unavailable")?.lifecycleState)
        assertNull(repository.getResult("song-unavailable")?.association)
    }

    private class QueueGateway(responses: List<MatchResponse>) : CloudMatchGateway {
        private val queue = ArrayDeque(responses)

        override suspend fun matchByBasicInfo(request: BasicInfoMatchRequest): MatchResponse {
            return if (queue.isEmpty()) {
                fallbackNoneResponse()
            } else {
                queue.removeFirst()
            }
        }

        override suspend fun matchByAudioIdentity(request: AudioIdentityMatchRequest): MatchResponse {
            return fallbackNoneResponse()
        }
    }

    private fun song(id: String): LocalSong {
        return LocalSong(
            localSongId = id,
            title = "title-$id",
            artist = "artist-$id",
            album = "album-$id",
            durationMs = 1000L,
            sourceState = SourceState.AVAILABLE,
        )
    }

    private fun reliableResponse(id: String): MatchResponse = MatchResponse(
        result = MatchResult.RELIABLE,
        association = CloudAssociation(
            cloudSongId = "cloud-$id",
            stage = AssociationStage.BASIC_INFO,
            isReliable = true,
        ),
    )

    private fun candidateResponse(): MatchResponse = MatchResponse(
        result = MatchResult.CANDIDATE,
        association = null,
    )

    private fun noneResponse(): MatchResponse = MatchResponse(
        result = MatchResult.NONE,
        association = null,
    )

    private fun timeoutResponse(): MatchResponse = MatchResponse(
        result = MatchResult.ERROR,
        association = null,
        rejectReason = "timeout",
    )

    private fun degradedResponse(): MatchResponse = MatchResponse(
        result = MatchResult.ERROR,
        association = null,
        rejectReason = "degraded",
    )

    companion object {
        private fun fallbackNoneResponse(): MatchResponse = MatchResponse(
            result = MatchResult.NONE,
            association = null,
        )
    }
}
