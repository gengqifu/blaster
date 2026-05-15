package com.orion.blaster.core.gateway

import com.orion.blaster.core.model.BasicSongInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioIdentityMatchRequestTest {
    @Test
    fun audio_identity_request_exposes_stable_mvp3_outer_fields() {
        val basicInfo = BasicSongInfo(
            localSongId = "song-1",
            title = "title",
            artist = "artist",
            album = "album",
            durationMs = 123000L,
        )
        val payload = byteArrayOf(1, 2, 3)

        val request = AudioIdentityMatchRequest(
            localSongId = "song-1",
            durationMs = 123000L,
            clipPolicy = "middle:30s",
            algorithm = "chromaprint-compatible",
            algorithmVersion = "mvp3-mock",
            payloadEncoding = "bytes",
            payload = payload,
            basicInfo = basicInfo,
            forceScenario = "RELIABLE",
        )

        assertEquals("song-1", request.localSongId)
        assertEquals(123000L, request.durationMs)
        assertEquals("middle:30s", request.clipPolicy)
        assertEquals("chromaprint-compatible", request.algorithm)
        assertEquals("mvp3-mock", request.algorithmVersion)
        assertEquals("bytes", request.payloadEncoding)
        assertArrayEquals(payload, request.payload)
        assertEquals(basicInfo, request.basicInfo)
        assertEquals("RELIABLE", request.forceScenario)
    }
}
