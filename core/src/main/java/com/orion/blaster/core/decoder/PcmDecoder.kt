package com.orion.blaster.core.decoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

data class PcmDecodeInput(
    val uri: String?,
    val mimeType: String?,
    val durationMs: Long?,
)

data class PcmAudio(
    val pcmBytes: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val clipPolicy: ClipPolicy,
)

sealed class PcmDecodeResult {
    data class Success(val audio: PcmAudio) : PcmDecodeResult()
    data class Failure(
        val reason: PcmDecodeFailureReason,
        val message: String,
    ) : PcmDecodeResult()
}

enum class PcmDecodeFailureReason {
    INACCESSIBLE_URI,
    UNSUPPORTED_FORMAT,
    DECODE_ERROR,
}

object PcmDecodeValidator {
    fun validate(input: PcmDecodeInput): PcmDecodeResult.Failure? {
        if (input.uri.isNullOrBlank()) {
            return PcmDecodeResult.Failure(
                reason = PcmDecodeFailureReason.INACCESSIBLE_URI,
                message = "audio uri is missing",
            )
        }
        val mimeType = input.mimeType?.lowercase()
        if (mimeType != null && mimeType !in SUPPORTED_MIME_TYPES) {
            return PcmDecodeResult.Failure(
                reason = PcmDecodeFailureReason.UNSUPPORTED_FORMAT,
                message = "unsupported audio mime type: ${input.mimeType}",
            )
        }
        return null
    }

    private val SUPPORTED_MIME_TYPES = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/aac",
        "audio/mp4",
        "audio/m4a",
        "audio/x-m4a",
        "audio/wav",
        "audio/x-wav",
        "audio/flac",
        "audio/ogg",
    )
}

class PcmDecoder(
    private val context: Context,
    private val clipPolicySelector: ClipPolicySelector = ClipPolicySelector(),
) {
    fun decode(input: PcmDecodeInput): PcmDecodeResult {
        PcmDecodeValidator.validate(input)?.let { return it }

        val clipPolicy = clipPolicySelector.select(input.durationMs)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, Uri.parse(input.uri), null)
            val trackIndex = selectAudioTrack(extractor)
                ?: return PcmDecodeResult.Failure(
                    reason = PcmDecodeFailureReason.UNSUPPORTED_FORMAT,
                    message = "no supported audio track found",
                )
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: return PcmDecodeResult.Failure(
                    reason = PcmDecodeFailureReason.UNSUPPORTED_FORMAT,
                    message = "audio track mime type is missing",
                )
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            decodeSelectedSegments(extractor, codec, inputFormat, clipPolicy)
        } catch (error: SecurityException) {
            PcmDecodeResult.Failure(
                reason = PcmDecodeFailureReason.INACCESSIBLE_URI,
                message = error.message ?: "audio uri is not accessible",
            )
        } catch (error: IllegalArgumentException) {
            PcmDecodeResult.Failure(
                reason = PcmDecodeFailureReason.UNSUPPORTED_FORMAT,
                message = error.message ?: "unsupported audio source",
            )
        } catch (error: Exception) {
            PcmDecodeResult.Failure(
                reason = PcmDecodeFailureReason.DECODE_ERROR,
                message = error.message ?: error::class.java.simpleName,
            )
        } finally {
            runCatching {
                codec?.stop()
            }
            runCatching {
                codec?.release()
            }
            extractor.release()
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return index
        }
        return null
    }

    private fun decodeSelectedSegments(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
        clipPolicy: ClipPolicy,
    ): PcmDecodeResult {
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val output = ByteArrayOutputStream()

        clipPolicy.segments.forEach { segment ->
            extractor.seekTo(segment.startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            drainSegment(
                extractor = extractor,
                codec = codec,
                segmentEndUs = (segment.startMs + segment.durationMs) * 1000L,
                output = output,
            )
        }

        if (output.size() == 0) {
            return PcmDecodeResult.Failure(
                reason = PcmDecodeFailureReason.DECODE_ERROR,
                message = "decoded pcm output is empty",
            )
        }

        return PcmDecodeResult.Success(
            PcmAudio(
                pcmBytes = output.toByteArray(),
                sampleRate = sampleRate,
                channelCount = channelCount,
                clipPolicy = clipPolicy,
            ),
        )
    }

    private fun drainSegment(
        extractor: MediaExtractor,
        codec: MediaCodec,
        segmentEndUs: Long,
        output: ByteArrayOutputStream,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs < 0L || sampleTimeUs > segmentEndUs) {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        val sampleSize = extractor.readSampleData(inputBuffer ?: EMPTY_BUFFER, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                sampleTimeUs,
                                extractor.sampleFlags,
                            )
                            extractor.advance()
                        }
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.get(chunk)
                        output.write(chunk)
                    }
                    outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone -> outputDone = true
            }
        }

        codec.flush()
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
        private val EMPTY_BUFFER = ByteBuffer.allocate(0)
    }
}
