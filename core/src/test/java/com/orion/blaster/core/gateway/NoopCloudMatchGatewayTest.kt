package com.orion.blaster.core.gateway

import com.orion.blaster.core.model.BasicInfoSource
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.MatchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoopCloudMatchGatewayTest {
    @Test
    fun returns_service_not_configured_for_basic_info_and_audio_identity() = runBlocking {
        val gateway = NoopCloudMatchGateway()
        val basic = gateway.matchByBasicInfo(
            BasicInfoMatchRequest(
                localSongId = "song-1",
                basicInfo = BasicSongInfo(
                    localSongId = "song-1",
                    title = "t",
                    artist = "a",
                    album = "b",
                    durationMs = 1000L,
                    source = BasicInfoSource.MEDIA_STORE,
                ),
            ),
        )
        val audio = gateway.matchByAudioIdentity(
            AudioIdentityMatchRequest(
                localSongId = "song-1",
                durationMs = 1000L,
                clipPolicy = "middle-60s",
                basicInfo = BasicSongInfo(
                    localSongId = "song-1",
                    title = "t",
                    artist = "a",
                    album = "b",
                    durationMs = 1000L,
                    source = BasicInfoSource.MEDIA_STORE,
                ),
                algorithm = "x",
                algorithmVersion = "1",
                payloadEncoding = "base64",
                payload = byteArrayOf(1),
            ),
        )

        assertEquals(MatchResult.ERROR, basic.result)
        assertEquals("service_not_configured", basic.rejectReason)
        assertNull(basic.association)

        assertEquals(MatchResult.ERROR, audio.result)
        assertEquals("service_not_configured", audio.rejectReason)
        assertNull(audio.association)
    }
}
