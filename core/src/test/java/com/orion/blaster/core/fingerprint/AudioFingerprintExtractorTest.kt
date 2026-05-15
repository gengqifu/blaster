package com.orion.blaster.core.fingerprint

import com.orion.blaster.core.decoder.ClipPolicy
import com.orion.blaster.core.decoder.ClipSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFingerprintExtractorTest {
    @Test
    fun extractor_returns_chromaprint_metadata_and_payload_digest() {
        val extractor = AudioFingerprintExtractor(
            bridge = FakeBridge(payload = "AQID", version = "1.6.0"),
            clock = sequenceClock(100L, 142L),
        )

        val result = extractor.extract(input())

        val fingerprint = (result as AudioFingerprintResult.Success).fingerprint
        assertEquals("chromaprint-compatible", fingerprint.algorithm)
        assertEquals("1.6.0", fingerprint.algorithmVersion)
        assertEquals("normal:middle-60s:start=60000ms,duration=60000ms", fingerprint.clipPolicy)
        assertEquals("chromaprint-base64", fingerprint.payloadEncoding)
        assertEquals("AQID", fingerprint.payload.toString(Charsets.UTF_8))
        assertTrue(fingerprint.payloadDigest.startsWith("sha256:"))
        assertEquals(42L, fingerprint.costMs)
    }

    @Test
    fun extractor_rejects_empty_pcm() {
        val extractor = AudioFingerprintExtractor(bridge = FakeBridge())

        val result = extractor.extract(input(pcmBytes = byteArrayOf()))

        assertEquals(AudioFingerprintResult.Failure("pcm bytes are empty"), result)
    }

    @Test
    fun extractor_rejects_empty_native_payload() {
        val extractor = AudioFingerprintExtractor(bridge = FakeBridge(payload = ""))

        val result = extractor.extract(input())

        assertEquals(AudioFingerprintResult.Failure("chromaprint payload is empty"), result)
    }

    private fun input(pcmBytes: ByteArray = byteArrayOf(1, 0, 2, 0)): AudioFingerprintInput {
        return AudioFingerprintInput(
            pcmBytes = pcmBytes,
            sampleRate = 44_100,
            channelCount = 2,
            clipPolicy = ClipPolicy(
                name = "normal:middle-60s",
                description = "normal:middle-60s:start=60000ms,duration=60000ms",
                segments = listOf(ClipSegment(60_000L, 60_000L)),
            ),
        )
    }

    private fun sequenceClock(vararg values: Long): () -> Long {
        var index = 0
        return {
            val value = values[index.coerceAtMost(values.lastIndex)]
            index += 1
            value
        }
    }

    private class FakeBridge(
        private val payload: String = "AQID",
        private val version: String = "1.6.0",
    ) : ChromaprintBridge {
        override fun fingerprintPcm16(pcmBytes: ByteArray, sampleRate: Int, channelCount: Int): String {
            return payload
        }

        override fun version(): String = version
    }
}
