package com.orion.blaster.core.modelinput

import com.orion.blaster.core.decoder.ClipPolicy
import com.orion.blaster.core.decoder.ClipSegment
import com.orion.blaster.core.decoder.PcmAudio
import com.orion.blaster.core.decoder.PcmDecodeFailureReason
import com.orion.blaster.core.decoder.PcmDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioModelInputGeneratorTest {
    @Test
    fun generate_success_produces_fixed_length_input() {
        val generator = DefaultAudioModelInputGenerator(
            pcmAudioProvider = PcmAudioProvider {
                PcmDecodeResult.Success(
                    PcmAudio(
                        pcmBytes = pcmFromShorts(shortArrayOf(1000, 1000, 500, 500)),
                        sampleRate = 44100,
                        channelCount = 2,
                        clipPolicy = ClipPolicy(
                            name = "test",
                            description = "test",
                            segments = listOf(ClipSegment(0, 1000)),
                        ),
                    ),
                )
            },
            targetSampleCount = 8,
        )

        val result = generator.generate(
            AudioModelInputRequest(
                localSongId = "song-1",
                uri = "content://song-1",
                mimeType = "audio/mpeg",
                durationMs = 1000L,
            ),
        )

        require(result is AudioModelInputResult.Success)
        assertEquals("song-1", result.input.localSongId)
        assertEquals(8, result.input.values.size)
        assertEquals("pcm16le-mono-pad-or-center-crop-8", result.input.inputStrategy)
        assertTrue(result.input.values.any { it != 0f })
    }

    @Test
    fun generate_failure_propagates_decode_reason() {
        val generator = DefaultAudioModelInputGenerator(
            pcmAudioProvider = PcmAudioProvider {
                PcmDecodeResult.Failure(
                    reason = PcmDecodeFailureReason.UNSUPPORTED_FORMAT,
                    message = "unsupported",
                )
            },
        )

        val result = generator.generate(
            AudioModelInputRequest(
                localSongId = "song-2",
                uri = "content://song-2",
                mimeType = "audio/unknown",
                durationMs = 1000L,
            ),
        )

        require(result is AudioModelInputResult.Failure)
        assertEquals("unsupported_format:unsupported", result.reason)
    }

    private fun pcmFromShorts(values: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putShort(it) }
        return buffer.array()
    }
}
