package com.orion.blaster.core.gateway

import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.MatchResponse

interface CloudMatchGateway {
    suspend fun matchByBasicInfo(request: BasicInfoMatchRequest): MatchResponse

    suspend fun matchByAudioIdentity(request: AudioIdentityMatchRequest): MatchResponse
}

data class BasicInfoMatchRequest(
    val localSongId: String,
    val basicInfo: BasicSongInfo,
    val forceScenario: String? = null,
)

data class AudioIdentityMatchRequest(
    val localSongId: String,
    val durationMs: Long?,
    val clipPolicy: String,
    val basicInfo: BasicSongInfo,
    val algorithm: String,
    val algorithmVersion: String,
    val payloadEncoding: String,
    val payload: ByteArray,
    val forceScenario: String? = null,
)
