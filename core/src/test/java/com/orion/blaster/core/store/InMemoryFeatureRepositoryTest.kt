package com.orion.blaster.core.store

import com.orion.blaster.core.model.AssociationStage
import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.model.CloudAssociation
import com.orion.blaster.core.model.CloudCandidate
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalFeature
import com.orion.blaster.core.model.LocalFeatureDiagnostics
import com.orion.blaster.core.model.LocalFeatureTopClass
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.SourceState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun save_and_get_content_signature() {
        repository.saveContentSignature(localSongId = "song-signature", contentSignature = "abc123")
        assertEquals("abc123", repository.getContentSignature("song-signature"))
    }

    @Test
    fun save_and_get_local_song_and_basic_info() {
        val localSong = com.orion.blaster.core.model.LocalSong(
            localSongId = "song-local",
            title = "title",
            artist = "artist",
            album = null,
            durationMs = 100L,
            sourceState = SourceState.AVAILABLE,
            uri = "content://song-local",
            contentSignature = "sig-local",
        )
        val basicInfo = com.orion.blaster.core.model.BasicSongInfo(
            localSongId = "song-local",
            title = "title",
            artist = "artist",
            album = null,
            durationMs = 100L,
        )

        repository.saveLocalSong(localSong)
        repository.saveBasicInfo(basicInfo)

        assertEquals(localSong, repository.getLocalSong("song-local"))
        assertEquals(basicInfo, repository.getBasicInfo("song-local"))
    }

    @Test
    fun save_and_get_audio_identity_summary() {
        val summary = AudioIdentitySummary(
            localSongId = "song-audio",
            algorithm = "chromaprint-compatible",
            algorithmVersion = "mvp3-mock",
            clipPolicy = "middle:30s",
            payloadEncoding = "base64",
            payloadDigest = "sha256:abc",
            costMs = 42L,
            lastReason = null,
            updatedAtMs = 1000L,
        )

        repository.saveAudioIdentitySummary(summary)

        assertEquals(summary, repository.getAudioIdentitySummary("song-audio"))
        assertNull(repository.getAudioIdentitySummary("missing"))
    }

    @Test
    fun mark_deleted_or_unavailable_updates_state_and_keeps_reason() {
        repository.saveMatchResult(
            localSongId = "song-6",
            matchResponse = noneResponse(),
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = 0,
            lastReason = "none",
            updatedAtMs = 1L,
        )

        repository.markDeletedOrUnavailable(
            localSongId = "song-6",
            sourceState = SourceState.DELETED,
            updatedAtMs = 2L,
            lastReason = "removed_from_scan",
        )
        assertEquals(LifecycleState.SKIPPED, repository.getResult("song-6")?.lifecycleState)
        assertEquals("removed_from_scan", repository.getLastReason("song-6"))

        repository.markDeletedOrUnavailable(
            localSongId = "song-6",
            sourceState = SourceState.UNAVAILABLE,
            updatedAtMs = 3L,
            lastReason = "permission_denied",
        )
        assertEquals(LifecycleState.WAITING_TO_CONTINUE, repository.getResult("song-6")?.lifecycleState)
        assertEquals("permission_denied", repository.getLastReason("song-6"))
    }

    @Test
    fun get_by_lifecycle_states_returns_filtered_results() {
        repository.saveMatchResult(
            localSongId = "song-candidate",
            matchResponse = candidateResponse("song-candidate"),
            lifecycleState = LifecycleState.CANDIDATE_ASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 10L,
        )
        repository.saveMatchResult(
            localSongId = "song-unassociated",
            matchResponse = noneResponse(),
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 11L,
        )
        repository.saveMatchResult(
            localSongId = "song-reliable",
            matchResponse = reliableResponse("song-reliable"),
            lifecycleState = LifecycleState.RELIABLY_ASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 12L,
        )

        val filtered = repository.getByLifecycleStates(
            setOf(LifecycleState.CANDIDATE_ASSOCIATED, LifecycleState.UNASSOCIATED),
        )

        val ids = filtered.map { it.localSongId }.toSet()
        assertEquals(2, ids.size)
        assertTrue(ids.contains("song-candidate"))
        assertTrue(ids.contains("song-unassociated"))
    }

    @Test
    fun save_and_get_local_feature_and_diagnostics() {
        seedUnassociated("song-feature")
        val feature = LocalFeature(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            modelName = "YAMNet",
            modelVersion = "tfhub-lite-1",
            featureSchemaVersion = 1,
            generatedAtMs = 100L,
        )
        val diagnostics = LocalFeatureDiagnostics(
            localSongId = "song-feature",
            modelName = "YAMNet",
            modelVersion = "tfhub-lite-1",
            featureSchemaVersion = 1,
            inputStrategy = "mono-16k-0.975s",
            outputTensorShape = listOf(1, 1024),
            costMs = 45L,
            topClasses = listOf(LocalFeatureTopClass("Music", 0.93f)),
            failureReason = null,
            generatedAtMs = 100L,
        )

        repository.saveLocalFeature("song-feature", feature, updatedAtMs = 101L)
        repository.saveLocalFeatureDiagnostics("song-feature", diagnostics)

        val storedFeature = repository.getLocalFeature("song-feature")
        assertNotNull(storedFeature)
        assertArrayEquals(feature.embedding, storedFeature?.embedding ?: floatArrayOf(), 0.0001f)
        assertEquals(feature.modelName, storedFeature?.modelName)
        assertEquals(feature.modelVersion, storedFeature?.modelVersion)
        assertEquals(feature.featureSchemaVersion, storedFeature?.featureSchemaVersion)
        assertEquals(LifecycleState.LOCAL_FEATURE_READY, repository.getResult("song-feature")?.lifecycleState)
        assertEquals(diagnostics, repository.getLocalFeatureDiagnostics("song-feature"))
    }

    @Test
    fun mark_local_feature_outdated_when_model_or_schema_changes() {
        seedUnassociated("song-feature-version")
        repository.saveLocalFeature(
            localSongId = "song-feature-version",
            localFeature = LocalFeature(
                embedding = floatArrayOf(0.1f, 0.2f),
                modelName = "YAMNet",
                modelVersion = "v1",
                featureSchemaVersion = 1,
                generatedAtMs = 1L,
            ),
            updatedAtMs = 2L,
        )

        val unchanged = repository.markLocalFeatureOutdatedIfVersionChanged(
            localSongId = "song-feature-version",
            currentModelVersion = "v1",
            currentFeatureSchemaVersion = 1,
            updatedAtMs = 3L,
            lastReason = "unchanged",
        )
        assertFalse(unchanged)
        assertEquals(LifecycleState.LOCAL_FEATURE_READY, repository.getResult("song-feature-version")?.lifecycleState)

        val changed = repository.markLocalFeatureOutdatedIfVersionChanged(
            localSongId = "song-feature-version",
            currentModelVersion = "v2",
            currentFeatureSchemaVersion = 1,
            updatedAtMs = 4L,
            lastReason = "model_version_changed",
        )
        assertTrue(changed)
        assertEquals(LifecycleState.OUTDATED, repository.getResult("song-feature-version")?.lifecycleState)
        assertNull(repository.getLocalFeature("song-feature-version"))
        assertEquals("model_version_changed", repository.getLastReason("song-feature-version"))
    }

    @Test
    fun get_all_local_song_ids_returns_stable_set() {
        repository.saveLocalSong(
            com.orion.blaster.core.model.LocalSong(
                localSongId = "song-a",
                title = null,
                artist = null,
                album = null,
                durationMs = null,
                sourceState = SourceState.AVAILABLE,
            ),
        )
        repository.saveLocalSong(
            com.orion.blaster.core.model.LocalSong(
                localSongId = "song-b",
                title = null,
                artist = null,
                album = null,
                durationMs = null,
                sourceState = SourceState.UNAVAILABLE,
            ),
        )

        val ids = repository.getAllLocalSongIds()
        assertEquals(2, ids.size)
        assertTrue(ids.contains("song-a"))
        assertTrue(ids.contains("song-b"))
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

    private fun seedUnassociated(localSongId: String) {
        repository.saveLocalSong(
            com.orion.blaster.core.model.LocalSong(
                localSongId = localSongId,
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 1000L,
                sourceState = SourceState.AVAILABLE,
            ),
        )
        repository.saveBasicInfo(
            com.orion.blaster.core.model.BasicSongInfo(
                localSongId = localSongId,
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 1000L,
            ),
        )
        repository.saveMatchResult(
            localSongId = localSongId,
            matchResponse = noneResponse(),
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )
    }
}
