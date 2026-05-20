package com.orion.blaster.core.localfeature.model

import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ModelArtifactInfo(
    val modelName: String,
    val modelVersion: String,
    val fileName: String,
    val sourceUrl: String,
    val sha256: String,
    val licenseSummary: String,
    val associatedFiles: List<String> = emptyList(),
)

interface EmbeddingModelSource {
    val artifact: ModelArtifactInfo

    fun openModelBuffer(): MappedByteBuffer
}

class FileEmbeddingModelSource(
    override val artifact: ModelArtifactInfo,
    private val modelFile: File,
) : EmbeddingModelSource {
    override fun openModelBuffer(): MappedByteBuffer {
        require(modelFile.isFile) { "Model file does not exist: ${modelFile.absolutePath}" }
        FileInputStream(modelFile).use { stream ->
            return stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        }
    }
}

data class YamnetValidationSummary(
    val modelName: String,
    val modelVersion: String,
    val inputShape: List<Int>,
    val outputShapes: List<List<Int>>,
    val selectedEmbeddingOutputIndex: Int,
    val embeddingVectorCount: Int,
)

class YamnetModelValidator(
    private val modelSource: EmbeddingModelSource,
) {
    companion object {
        private const val MAX_OUTPUT_BYTES = 1_048_576
    }

    fun validateOnce(): YamnetValidationSummary {
        Interpreter(modelSource.openModelBuffer(), Interpreter.Options()).use { interpreter ->
            val inputShape = interpreter.getInputTensor(0).shape()
            val inputSamples = when (inputShape.size) {
                1 -> inputShape[0]
                2 -> {
                    require(inputShape[0] == 1) {
                        "Unexpected YAMNet batch size: ${inputShape.contentToString()}"
                    }
                    inputShape[1]
                }
                else -> throw IllegalArgumentException(
                    "Unexpected YAMNet input rank: ${inputShape.contentToString()}",
                )
            }
            val input = when (inputShape.size) {
                1 -> FloatArray(inputSamples)
                else -> arrayOf(FloatArray(inputSamples))
            }

            val initialOutputShapes = (0 until interpreter.outputTensorCount).map { index ->
                interpreter.getOutputTensor(index).shape().toList()
            }
            require(initialOutputShapes.size >= 2) {
                "Unexpected YAMNet output tensor count: ${initialOutputShapes.size}"
            }

            val outputs = mutableMapOf<Int, Any>()
            initialOutputShapes.forEachIndexed { index, _ ->
                outputs[index] = ByteBuffer.allocateDirect(MAX_OUTPUT_BYTES)
                    .order(ByteOrder.nativeOrder())
            }

            interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)

            val outputShapes = (0 until interpreter.outputTensorCount).map { index ->
                interpreter.getOutputTensor(index).shape().toList()
            }
            val embeddingOutputIndex = 1
            val embeddingShape = outputShapes[embeddingOutputIndex]
            val embeddingVectorCount = embeddingShape.fold(1) { acc, value -> acc * value }
            require(embeddingVectorCount > 0) {
                "YAMNet embedding output is empty: $embeddingShape"
            }

            return YamnetValidationSummary(
                modelName = modelSource.artifact.modelName,
                modelVersion = modelSource.artifact.modelVersion,
                inputShape = inputShape.toList(),
                outputShapes = outputShapes,
                selectedEmbeddingOutputIndex = embeddingOutputIndex,
                embeddingVectorCount = embeddingVectorCount,
            )
        }
    }
}
