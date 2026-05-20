package com.orion.blaster.core.modelinput

import com.orion.blaster.core.decoder.PcmDecodeFailureReason
import com.orion.blaster.core.decoder.PcmDecodeInput
import com.orion.blaster.core.decoder.PcmDecodeResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class AudioModelInputRequest(
    val localSongId: String,
    val uri: String?,
    val mimeType: String?,
    val durationMs: Long?,
)

data class AudioModelInput(
    val localSongId: String,
    val values: FloatArray,
    val inputStrategy: String,
    val sourceSampleRate: Int,
    val sourceChannelCount: Int,
)

sealed class AudioModelInputResult {
    data class Success(val input: AudioModelInput) : AudioModelInputResult()
    data class Failure(val reason: String) : AudioModelInputResult()
}

fun interface PcmAudioProvider {
    fun decode(input: PcmDecodeInput): PcmDecodeResult
}

interface AudioModelInputGenerator {
    fun generate(request: AudioModelInputRequest): AudioModelInputResult
}

class DefaultAudioModelInputGenerator(
    private val pcmAudioProvider: PcmAudioProvider,
    private val targetSampleCount: Int = DEFAULT_TARGET_SAMPLE_COUNT,
) : AudioModelInputGenerator {
    override fun generate(request: AudioModelInputRequest): AudioModelInputResult {
        val decoded = pcmAudioProvider.decode(
            PcmDecodeInput(
                uri = request.uri,
                mimeType = request.mimeType,
                durationMs = request.durationMs,
            ),
        )
        return when (decoded) {
            is PcmDecodeResult.Failure -> AudioModelInputResult.Failure(
                reason = mapDecodeFailureReason(decoded),
            )

            is PcmDecodeResult.Success -> {
                val mono = pcm16ToMonoFloat(
                    pcmBytes = decoded.audio.pcmBytes,
                    channelCount = decoded.audio.channelCount,
                )
                val fixed = fitToTargetSampleCount(mono, targetSampleCount)
                AudioModelInputResult.Success(
                    AudioModelInput(
                        localSongId = request.localSongId,
                        values = fixed,
                        inputStrategy = "pcm16le-mono-pad-or-center-crop-$targetSampleCount",
                        sourceSampleRate = decoded.audio.sampleRate,
                        sourceChannelCount = decoded.audio.channelCount,
                    ),
                )
            }
        }
    }

    private fun mapDecodeFailureReason(failure: PcmDecodeResult.Failure): String {
        val prefix = when (failure.reason) {
            PcmDecodeFailureReason.INACCESSIBLE_URI -> "inaccessible_uri"
            PcmDecodeFailureReason.UNSUPPORTED_FORMAT -> "unsupported_format"
            PcmDecodeFailureReason.DECODE_ERROR -> "decode_error"
        }
        return "$prefix:${failure.message}"
    }

    private fun pcm16ToMonoFloat(pcmBytes: ByteArray, channelCount: Int): FloatArray {
        if (pcmBytes.isEmpty()) {
            return FloatArray(0)
        }
        val safeChannelCount = max(channelCount, 1)
        val sampleCount = pcmBytes.size / 2
        val frames = sampleCount / safeChannelCount
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val mono = FloatArray(frames)
        for (frameIndex in 0 until frames) {
            var sum = 0f
            for (channelIndex in 0 until safeChannelCount) {
                val sample = buffer.short.toFloat() / Short.MAX_VALUE.toFloat()
                sum += sample
            }
            mono[frameIndex] = (sum / safeChannelCount).coerceIn(-1f, 1f)
        }
        return mono
    }

    private fun fitToTargetSampleCount(source: FloatArray, target: Int): FloatArray {
        if (source.size == target) return source
        if (source.size > target) {
            val start = (source.size - target) / 2
            return source.copyOfRange(start, start + target)
        }

        val output = FloatArray(target)
        val copyCount = min(source.size, target)
        source.copyInto(output, endIndex = copyCount)
        return output
    }

    companion object {
        const val DEFAULT_TARGET_SAMPLE_COUNT = 15_600
    }
}
