package com.orion.blaster.core.embedding

import com.orion.blaster.core.localfeature.model.EmbeddingModelSource
import com.orion.blaster.core.model.LocalFeature
import com.orion.blaster.core.model.LocalFeatureDiagnostics
import com.orion.blaster.core.model.LocalFeatureTopClass
import com.orion.blaster.core.modelinput.AudioModelInput
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LocalEmbeddingOutput(
    val embedding: FloatArray,
    val outputTensorShape: List<Int>,
    val topClasses: List<LocalFeatureTopClass> = emptyList(),
)

fun interface LocalEmbeddingInferenceEngine {
    fun infer(input: FloatArray): LocalEmbeddingOutput
}

sealed class LocalEmbeddingGenerationResult {
    data class Success(
        val localFeature: LocalFeature,
        val diagnostics: LocalFeatureDiagnostics,
    ) : LocalEmbeddingGenerationResult()

    data class Failure(
        val reason: String,
    ) : LocalEmbeddingGenerationResult()
}

class LocalEmbeddingModel(
    private val modelSource: EmbeddingModelSource,
    private val featureSchemaVersion: Int = 1,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val engineFactory: (EmbeddingModelSource) -> LocalEmbeddingInferenceEngine = {
        TfliteLocalEmbeddingInferenceEngine(it)
    },
) {
    @Volatile
    private var engine: LocalEmbeddingInferenceEngine? = null

    fun generate(localSongId: String, input: AudioModelInput): LocalEmbeddingGenerationResult {
        val startedAt = clock()
        val embeddingOutput = try {
            getOrCreateEngine().infer(input.values)
        } catch (error: IllegalArgumentException) {
            return LocalEmbeddingGenerationResult.Failure(reason = classifyLoadFailure(error))
        } catch (error: Exception) {
            return LocalEmbeddingGenerationResult.Failure(reason = "inference_failed:${error.message ?: error::class.java.simpleName}")
        }

        val generatedAt = clock()
        val localFeature = LocalFeature(
            embedding = embeddingOutput.embedding,
            modelName = modelSource.artifact.modelName,
            modelVersion = modelSource.artifact.modelVersion,
            featureSchemaVersion = featureSchemaVersion,
            generatedAtMs = generatedAt,
        )
        val diagnostics = LocalFeatureDiagnostics(
            localSongId = localSongId,
            modelName = modelSource.artifact.modelName,
            modelVersion = modelSource.artifact.modelVersion,
            featureSchemaVersion = featureSchemaVersion,
            inputStrategy = input.inputStrategy,
            outputTensorShape = embeddingOutput.outputTensorShape,
            costMs = generatedAt - startedAt,
            topClasses = embeddingOutput.topClasses,
            failureReason = null,
            generatedAtMs = generatedAt,
        )
        return LocalEmbeddingGenerationResult.Success(localFeature, diagnostics)
    }

    private fun getOrCreateEngine(): LocalEmbeddingInferenceEngine {
        val current = engine
        if (current != null) {
            return current
        }
        return synchronized(this) {
            engine ?: engineFactory(modelSource).also { created ->
                engine = created
            }
        }
    }

    private fun classifyLoadFailure(error: IllegalArgumentException): String {
        val message = error.message ?: "unknown"
        return if (message.contains("does not exist", ignoreCase = true)) {
            "model_missing:$message"
        } else {
            "model_load_failed:$message"
        }
    }
}

class TfliteLocalEmbeddingInferenceEngine(
    modelSource: EmbeddingModelSource,
) : LocalEmbeddingInferenceEngine {
    private val interpreter = Interpreter(modelSource.openModelBuffer(), Interpreter.Options())

    override fun infer(input: FloatArray): LocalEmbeddingOutput {
        val modelInput = if (interpreter.getInputTensor(0).shape().size == 1) {
            input
        } else {
            arrayOf(input)
        }

        val outputTensorCount = interpreter.outputTensorCount
        val outputs = mutableMapOf<Int, Any>()
        for (index in 0 until outputTensorCount) {
            outputs[index] = ByteBuffer.allocateDirect(MAX_OUTPUT_BYTES).order(ByteOrder.nativeOrder())
        }
        interpreter.runForMultipleInputsOutputs(arrayOf(modelInput), outputs)

        val embeddingIndex = if (outputTensorCount > 1) 1 else 0
        val embeddingShape = interpreter.getOutputTensor(embeddingIndex).shape().toList()
        val embeddingBuffer = outputs[embeddingIndex] as ByteBuffer
        embeddingBuffer.rewind()
        val floatCount = interpreter.getOutputTensor(embeddingIndex).numBytes() / Float.SIZE_BYTES
        val embedding = FloatArray(floatCount)
        embeddingBuffer.asFloatBuffer().get(embedding)

        return LocalEmbeddingOutput(
            embedding = embedding,
            outputTensorShape = embeddingShape,
            topClasses = emptyList(),
        )
    }

    companion object {
        private const val MAX_OUTPUT_BYTES = 1_048_576
    }
}
