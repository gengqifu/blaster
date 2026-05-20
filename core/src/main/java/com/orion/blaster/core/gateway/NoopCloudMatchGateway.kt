package com.orion.blaster.core.gateway

import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult

class NoopCloudMatchGateway(
    private val reason: String = "service_not_configured",
) : CloudMatchGateway {
    override suspend fun matchByBasicInfo(request: BasicInfoMatchRequest): MatchResponse {
        return MatchResponse(
            result = MatchResult.ERROR,
            association = null,
            rejectReason = reason,
        )
    }

    override suspend fun matchByAudioIdentity(request: AudioIdentityMatchRequest): MatchResponse {
        return MatchResponse(
            result = MatchResult.ERROR,
            association = null,
            rejectReason = reason,
        )
    }
}
