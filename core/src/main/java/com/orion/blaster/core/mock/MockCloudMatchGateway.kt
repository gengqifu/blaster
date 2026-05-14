package com.orion.blaster.core.mock

import com.orion.blaster.core.gateway.AudioIdentityMatchRequest
import com.orion.blaster.core.gateway.BasicInfoMatchRequest
import com.orion.blaster.core.gateway.CloudMatchGateway
import com.orion.blaster.core.model.AssociationStage
import com.orion.blaster.core.model.CloudAssociation
import com.orion.blaster.core.model.CloudCandidate
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult

enum class MockScenario {
    RELIABLE,
    CANDIDATE,
    NONE,
    ERROR,
    TIMEOUT,
    DEGRADED,
}

data class MockMatchRule(
    val localSongId: String? = null,
    val titleContains: String? = null,
    val artistContains: String? = null,
    val forceScenario: MockScenario,
)

class MockCloudMatchGateway(
    private val rules: List<MockMatchRule> = emptyList(),
) : CloudMatchGateway {

    override suspend fun matchByBasicInfo(request: BasicInfoMatchRequest): MatchResponse {
        val scenario = parseScenario(request.forceScenario)
            ?: resolveFromRules(request)
            ?: MockScenario.NONE
        return responseForScenario(request.localSongId, scenario, AssociationStage.BASIC_INFO)
    }

    override suspend fun matchByAudioIdentity(request: AudioIdentityMatchRequest): MatchResponse {
        val scenario = parseScenario(request.forceScenario) ?: MockScenario.NONE
        return responseForScenario(request.localSongId, scenario, AssociationStage.AUDIO_IDENTITY)
    }

    private fun resolveFromRules(request: BasicInfoMatchRequest): MockScenario? {
        return rules.firstOrNull { rule ->
            matchesRule(rule, request)
        }?.forceScenario
    }

    private fun matchesRule(rule: MockMatchRule, request: BasicInfoMatchRequest): Boolean {
        val title = request.basicInfo.title.orEmpty()
        val artist = request.basicInfo.artist.orEmpty()

        if (rule.localSongId != null && rule.localSongId != request.localSongId) return false
        if (rule.titleContains != null && !title.contains(rule.titleContains, ignoreCase = true)) return false
        if (rule.artistContains != null && !artist.contains(rule.artistContains, ignoreCase = true)) return false
        return true
    }

    private fun parseScenario(raw: String?): MockScenario? {
        if (raw.isNullOrBlank()) return null
        return runCatching { MockScenario.valueOf(raw.trim().uppercase()) }.getOrNull()
    }

    private fun responseForScenario(
        localSongId: String,
        scenario: MockScenario,
        stage: AssociationStage,
    ): MatchResponse = when (scenario) {
        MockScenario.RELIABLE -> MatchResponse(
            result = MatchResult.RELIABLE,
            association = CloudAssociation(
                cloudSongId = "cloud-$localSongId",
                stage = stage,
                isReliable = true,
            ),
        )

        MockScenario.CANDIDATE -> MatchResponse(
            result = MatchResult.CANDIDATE,
            association = null,
            candidates = listOf(
                CloudCandidate(
                    cloudSongId = "candidate-$localSongId",
                    reason = "low_confidence",
                    score = 0.55f,
                ),
            ),
        )

        MockScenario.NONE -> MatchResponse(
            result = MatchResult.NONE,
            association = null,
        )

        MockScenario.ERROR -> MatchResponse(
            result = MatchResult.ERROR,
            association = null,
            rejectReason = "error",
        )

        MockScenario.TIMEOUT -> MatchResponse(
            result = MatchResult.ERROR,
            association = null,
            rejectReason = "timeout",
        )

        MockScenario.DEGRADED -> MatchResponse(
            result = MatchResult.ERROR,
            association = null,
            rejectReason = "degraded",
        )
    }
}
