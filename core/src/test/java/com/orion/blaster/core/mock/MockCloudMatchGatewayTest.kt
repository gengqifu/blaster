package com.orion.blaster.core.mock

import com.orion.blaster.core.gateway.BasicInfoMatchRequest
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.MatchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MockCloudMatchGatewayTest {

    @Test
    fun first_match_rule_wins() = runBlocking {
        val gateway = MockCloudMatchGateway(
            rules = listOf(
                MockMatchRule(titleContains = "hello", forceScenario = MockScenario.CANDIDATE),
                MockMatchRule(titleContains = "hello", forceScenario = MockScenario.RELIABLE),
            ),
        )

        val response = gateway.matchByBasicInfo(request(title = "hello world"))

        assertEquals(MatchResult.CANDIDATE, response.result)
    }

    @Test
    fun all_rule_conditions_must_match() = runBlocking {
        val gateway = MockCloudMatchGateway(
            rules = listOf(
                MockMatchRule(
                    localSongId = "song-1",
                    titleContains = "hello",
                    artistContains = "adele",
                    forceScenario = MockScenario.RELIABLE,
                ),
            ),
        )

        val matched = gateway.matchByBasicInfo(
            request(localSongId = "song-1", title = "hello", artist = "adele"),
        )
        val notMatched = gateway.matchByBasicInfo(
            request(localSongId = "song-1", title = "hello", artist = "someone else"),
        )

        assertEquals(MatchResult.RELIABLE, matched.result)
        assertEquals(MatchResult.NONE, notMatched.result)
    }

    @Test
    fun returns_none_when_no_rule_matches() = runBlocking {
        val gateway = MockCloudMatchGateway(
            rules = listOf(
                MockMatchRule(titleContains = "target", forceScenario = MockScenario.RELIABLE),
            ),
        )

        val response = gateway.matchByBasicInfo(request(title = "other"))

        assertEquals(MatchResult.NONE, response.result)
    }

    @Test
    fun force_scenario_covers_all_six_mvp1_scenarios() = runBlocking {
        val gateway = MockCloudMatchGateway()

        val reliable = gateway.matchByBasicInfo(request(forceScenario = "RELIABLE"))
        val candidate = gateway.matchByBasicInfo(request(forceScenario = "CANDIDATE"))
        val none = gateway.matchByBasicInfo(request(forceScenario = "NONE"))
        val error = gateway.matchByBasicInfo(request(forceScenario = "ERROR"))
        val timeout = gateway.matchByBasicInfo(request(forceScenario = "TIMEOUT"))
        val degraded = gateway.matchByBasicInfo(request(forceScenario = "DEGRADED"))

        assertEquals(MatchResult.RELIABLE, reliable.result)
        assertNotNull(reliable.association)

        assertEquals(MatchResult.CANDIDATE, candidate.result)
        assertEquals(1, candidate.candidates.size)

        assertEquals(MatchResult.NONE, none.result)

        assertEquals(MatchResult.ERROR, error.result)
        assertEquals("error", error.rejectReason)

        assertEquals(MatchResult.ERROR, timeout.result)
        assertEquals("timeout", timeout.rejectReason)

        assertEquals(MatchResult.ERROR, degraded.result)
        assertEquals("degraded", degraded.rejectReason)
    }

    @Test
    fun mock_gateway_never_returns_outdated() = runBlocking {
        val gateway = MockCloudMatchGateway()

        val response = gateway.matchByBasicInfo(request(forceScenario = "OUTDATED"))

        assertEquals(MatchResult.NONE, response.result)
        assertNull(response.association)
        assertNull(response.rejectReason)
    }

    private fun request(
        localSongId: String = "song-1",
        title: String? = "hello",
        artist: String? = "adele",
        forceScenario: String? = null,
    ): BasicInfoMatchRequest {
        return BasicInfoMatchRequest(
            localSongId = localSongId,
            basicInfo = BasicSongInfo(
                localSongId = localSongId,
                title = title,
                artist = artist,
                album = null,
                durationMs = 120000L,
            ),
            forceScenario = forceScenario,
        )
    }
}
