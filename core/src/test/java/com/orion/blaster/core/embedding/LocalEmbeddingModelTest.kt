package com.orion.blaster.core.embedding

import com.orion.blaster.core.localfeature.model.EmbeddingModelSource
import com.orion.blaster.core.localfeature.model.ModelArtifactInfo
import com.orion.blaster.core.model.LocalFeatureTopClass
import com.orion.blaster.core.modelinput.AudioModelInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.MappedByteBuffer

class LocalEmbeddingModelTest {
    @Test
    fun generate_success_returns_local_feature_and_diagnostics() {
        val model = LocalEmbeddingModel(
            modelSource = fakeSource(),
            featureSchemaVersion = 3,
            clock = { 100L },
            engineFactory = {
                LocalEmbeddingInferenceEngine {
                    LocalEmbeddingOutput(
                        embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
                        outputTensorShape = listOf(1, 1024),
                        topClasses = listOf(LocalFeatureTopClass("Music", 0.88f)),
                    )
                }
            },
        )

        val result = model.generate(
            localSongId = "song-1",
            input = AudioModelInput(
                localSongId = "song-1",
                values = FloatArray(15600) { 0.01f },
                inputStrategy = "pcm16le-mono-pad-or-center-crop-15600",
                sourceSampleRate = 16000,
                sourceChannelCount = 1,
            ),
        )

        require(result is LocalEmbeddingGenerationResult.Success)
        assertEquals("YAMNet", result.localFeature.modelName)
        assertEquals("tfhub-lite-1", result.localFeature.modelVersion)
        assertEquals(3, result.localFeature.featureSchemaVersion)
        assertEquals(3, result.localFeature.embedding.size)
        assertEquals(listOf(1, 1024), result.diagnostics.outputTensorShape)
        assertTrue(result.diagnostics.topClasses.isNotEmpty())
    }

    @Test
    fun generate_failure_when_model_is_missing() {
        val model = LocalEmbeddingModel(
            modelSource = fakeSource(),
            engineFactory = {
                throw IllegalArgumentException("Model file does not exist: /missing/yamnet.tflite")
            },
        )

        val result = model.generate(
            localSongId = "song-2",
            input = fakeInput("song-2"),
        )

        require(result is LocalEmbeddingGenerationResult.Failure)
        assertTrue(result.reason.startsWith("model_missing:"))
    }

    @Test
    fun generate_failure_when_model_load_fails() {
        val model = LocalEmbeddingModel(
            modelSource = fakeSource(),
            engineFactory = {
                throw IllegalArgumentException("unsupported model format")
            },
        )

        val result = model.generate(
            localSongId = "song-3",
            input = fakeInput("song-3"),
        )

        require(result is LocalEmbeddingGenerationResult.Failure)
        assertTrue(result.reason.startsWith("model_load_failed:"))
    }

    @Test
    fun generate_failure_when_inference_fails() {
        val model = LocalEmbeddingModel(
            modelSource = fakeSource(),
            engineFactory = {
                LocalEmbeddingInferenceEngine {
                    throw IllegalStateException("interpreter crashed")
                }
            },
        )

        val result = model.generate(
            localSongId = "song-4",
            input = fakeInput("song-4"),
        )

        require(result is LocalEmbeddingGenerationResult.Failure)
        assertTrue(result.reason.startsWith("inference_failed:"))
    }

    private fun fakeInput(localSongId: String) = AudioModelInput(
        localSongId = localSongId,
        values = FloatArray(15600),
        inputStrategy = "test",
        sourceSampleRate = 16000,
        sourceChannelCount = 1,
    )

    private fun fakeSource() = object : EmbeddingModelSource {
        override val artifact = ModelArtifactInfo(
            modelName = "YAMNet",
            modelVersion = "tfhub-lite-1",
            fileName = "yamnet.tflite",
            sourceUrl = "https://tfhub.dev/google/lite-model/yamnet/tflite/1?lite-format=tflite",
            sha256 = "141fba1cdaae842c816f28edc4937e8b4f0af4c8df21862ccc6b52dc567993c3",
            licenseSummary = "Apache-2.0",
        )

        override fun openModelBuffer(): MappedByteBuffer {
            throw UnsupportedOperationException("not needed for test")
        }
    }
}
