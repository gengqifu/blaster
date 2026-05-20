package com.orion.blaster.core.pipeline

import com.orion.blaster.core.embedding.LocalEmbeddingGenerationResult
import com.orion.blaster.core.embedding.LocalEmbeddingModel
import com.orion.blaster.core.featurequeue.LocalFeatureQueue
import com.orion.blaster.core.localfeature.model.EmbeddingModelSource
import com.orion.blaster.core.localfeature.model.ModelArtifactInfo
import com.orion.blaster.core.mock.MockCloudMatchGateway
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalFeature
import com.orion.blaster.core.model.LocalFeatureDiagnostics
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.modelinput.AudioModelInput
import com.orion.blaster.core.modelinput.AudioModelInputGenerator
import com.orion.blaster.core.modelinput.AudioModelInputRequest
import com.orion.blaster.core.modelinput.AudioModelInputResult
import com.orion.blaster.core.scheduler.LocalFeatureDeviceState
import com.orion.blaster.core.scheduler.LocalFeatureScheduler
import com.orion.blaster.core.store.InMemoryFeatureRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.MappedByteBuffer

class LocalFeaturePipelineTest {
    @Test
    fun unassociated_song_reaches_local_feature_ready() {
        val repository = repositoryWithState("song-1", LifecycleState.UNASSOCIATED)
        val pipeline = pipeline(
            repository = repository,
            inputGenerator = FakeInputGenerator.success(),
            embeddingModel = FakeEmbeddingModel.success(),
        )

        val summary = pipeline.processLocalFeatureQueue(
            scheduler = LocalFeatureScheduler(LocalFeatureQueue(repository)),
        )

        assertEquals(1, summary.scheduledCount)
        assertEquals(1, summary.readyCount)
        assertEquals(LifecycleState.LOCAL_FEATURE_READY, repository.getResult("song-1")?.lifecycleState)
        assertTrue(repository.getLocalFeature("song-1")?.embedding?.isNotEmpty() == true)
    }

    @Test
    fun candidate_is_not_queued_by_default() {
        val repository = repositoryWithState("song-2", LifecycleState.CANDIDATE_ASSOCIATED)
        val pipeline = pipeline(
            repository = repository,
            inputGenerator = FakeInputGenerator.success(),
            embeddingModel = FakeEmbeddingModel.success(),
        )

        val summary = pipeline.processLocalFeatureQueue(
            scheduler = LocalFeatureScheduler(LocalFeatureQueue(repository)),
        )

        assertEquals(0, summary.scheduledCount)
        assertEquals(LifecycleState.CANDIDATE_ASSOCIATED, repository.getResult("song-2")?.lifecycleState)
    }

    @Test
    fun low_battery_moves_item_to_waiting() {
        val repository = repositoryWithState("song-3", LifecycleState.UNASSOCIATED)
        val pipeline = pipeline(
            repository = repository,
            inputGenerator = FakeInputGenerator.success(),
            embeddingModel = FakeEmbeddingModel.success(),
        )

        val summary = pipeline.processLocalFeatureQueue(
            scheduler = LocalFeatureScheduler(
                queue = LocalFeatureQueue(repository),
                deviceStateProvider = { LocalFeatureDeviceState(lowBattery = true) },
            ),
        )

        assertEquals(1, summary.waitingCount)
        assertEquals(LifecycleState.WAITING_TO_CONTINUE, repository.getResult("song-3")?.lifecycleState)
        assertEquals("low_battery", repository.getLastReason("song-3"))
    }

    @Test
    fun model_missing_is_waiting_without_retry_increment() {
        val repository = repositoryWithState("song-4", LifecycleState.UNASSOCIATED)
        val pipeline = pipeline(
            repository = repository,
            inputGenerator = FakeInputGenerator.success(),
            embeddingModel = FakeEmbeddingModel.failure("model_missing:file not found"),
        )

        pipeline.processLocalFeatureQueue(
            scheduler = LocalFeatureScheduler(LocalFeatureQueue(repository)),
        )

        assertEquals(LifecycleState.WAITING_TO_CONTINUE, repository.getResult("song-4")?.lifecycleState)
        assertEquals(0, repository.getRetryCount("song-4"))
    }

    @Test
    fun technical_failure_retries_then_fails() {
        val repository = repositoryWithState("song-5", LifecycleState.UNASSOCIATED)
        val pipeline = pipeline(
            repository = repository,
            inputGenerator = FakeInputGenerator.success(),
            embeddingModel = FakeEmbeddingModel.failure("inference_failed:delegate crashed"),
            maxRetryCount = 1,
        )

        pipeline.processLocalFeatureQueue(LocalFeatureScheduler(LocalFeatureQueue(repository)))
        assertEquals(LifecycleState.WAITING_TO_CONTINUE, repository.getResult("song-5")?.lifecycleState)
        assertEquals(1, repository.getRetryCount("song-5"))

        repository.saveLifecycleState(
            localSongId = "song-5",
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = repository.getRetryCount("song-5"),
            lastReason = null,
            updatedAtMs = 2L,
        )
        pipeline.processLocalFeatureQueue(LocalFeatureScheduler(LocalFeatureQueue(repository)))

        assertEquals(LifecycleState.FAILED, repository.getResult("song-5")?.lifecycleState)
        assertEquals(2, repository.getRetryCount("song-5"))
    }

    private fun pipeline(
        repository: InMemoryFeatureRepository,
        inputGenerator: AudioModelInputGenerator,
        embeddingModel: LocalEmbeddingModel,
        maxRetryCount: Int = 2,
    ): FeaturePipeline {
        return FeaturePipeline(
            gateway = MockCloudMatchGateway(),
            repository = repository,
            audioModelInputGenerator = inputGenerator,
            localEmbeddingModel = embeddingModel,
            maxRetryCount = maxRetryCount,
            clock = { 100L },
        )
    }

    private fun repositoryWithState(localSongId: String, state: LifecycleState): InMemoryFeatureRepository {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(
            LocalSong(
                localSongId = localSongId,
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 180000L,
                sourceState = SourceState.AVAILABLE,
                uri = "content://$localSongId",
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
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )
        return repository
    }

    private class FakeInputGenerator(
        private val resultProvider: (AudioModelInputRequest) -> AudioModelInputResult,
    ) : AudioModelInputGenerator {
        override fun generate(request: AudioModelInputRequest): AudioModelInputResult = resultProvider(request)

        companion object {
            fun success() = FakeInputGenerator {
                AudioModelInputResult.Success(
                    AudioModelInput(
                        localSongId = it.localSongId,
                        values = FloatArray(15600) { 0.02f },
                        inputStrategy = "test",
                        sourceSampleRate = 16000,
                        sourceChannelCount = 1,
                    ),
                )
            }
        }
    }

    private class FakeEmbeddingModel(
        private val resultProvider: (String, AudioModelInput) -> LocalEmbeddingGenerationResult,
    ) : LocalEmbeddingModel(
        modelSource = DummyModelSource(),
        engineFactory = { throw UnsupportedOperationException("not used") },
    ) {
        override fun generate(localSongId: String, input: AudioModelInput): LocalEmbeddingGenerationResult {
            return resultProvider(localSongId, input)
        }

        companion object {
            fun success() = FakeEmbeddingModel { localSongId, _ ->
                LocalEmbeddingGenerationResult.Success(
                    localFeature = LocalFeature(
                        embedding = floatArrayOf(0.1f, 0.2f),
                        modelName = "YAMNet",
                        modelVersion = "tfhub-lite-1",
                        featureSchemaVersion = 1,
                        generatedAtMs = 100L,
                    ),
                    diagnostics = LocalFeatureDiagnostics(
                        localSongId = localSongId,
                        modelName = "YAMNet",
                        modelVersion = "tfhub-lite-1",
                        featureSchemaVersion = 1,
                        inputStrategy = "test",
                        outputTensorShape = listOf(1, 1024),
                        costMs = 3L,
                        failureReason = null,
                        generatedAtMs = 100L,
                    ),
                )
            }

            fun failure(reason: String) = FakeEmbeddingModel { _, _ ->
                LocalEmbeddingGenerationResult.Failure(reason)
            }
        }
    }

    private class DummyModelSource : EmbeddingModelSource {
        override val artifact = ModelArtifactInfo(
            modelName = "YAMNet",
            modelVersion = "tfhub-lite-1",
            fileName = "yamnet.tflite",
            sourceUrl = "https://tfhub.dev/google/lite-model/yamnet/tflite/1?lite-format=tflite",
            sha256 = "141fba1cdaae842c816f28edc4937e8b4f0af4c8df21862ccc6b52dc567993c3",
            licenseSummary = "Apache-2.0",
        )

        override fun openModelBuffer(): MappedByteBuffer {
            throw UnsupportedOperationException("not needed in FakeEmbeddingModel")
        }
    }
}
