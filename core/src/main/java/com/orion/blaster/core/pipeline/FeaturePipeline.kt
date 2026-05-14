package com.orion.blaster.core.pipeline

import com.orion.blaster.core.gateway.BasicInfoMatchRequest
import com.orion.blaster.core.gateway.CloudMatchGateway
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.toLifecycleState
import com.orion.blaster.core.store.FeatureRepository

class FeaturePipeline(
    private val gateway: CloudMatchGateway,
    private val repository: FeatureRepository,
    private val maxRetryCount: Int = 2,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun process(localSong: LocalSong, forceScenario: String? = null): LifecycleState {
        repository.saveLocalSong(localSong)

        val basicInfo = BasicSongInfo(
            localSongId = localSong.localSongId,
            title = localSong.title,
            artist = localSong.artist,
            album = localSong.album,
            durationMs = localSong.durationMs,
        )
        repository.saveBasicInfo(basicInfo)
        repository.saveLifecycleState(
            localSongId = localSong.localSongId,
            lifecycleState = LifecycleState.BASIC_INFO_READY,
            retryCount = repository.getRetryCount(localSong.localSongId),
            lastReason = null,
            updatedAtMs = clock(),
        )

        var attempt = 0
        while (true) {
            repository.saveLifecycleState(
                localSongId = localSong.localSongId,
                lifecycleState = LifecycleState.BASIC_MATCHING,
                retryCount = attempt,
                lastReason = null,
                updatedAtMs = clock(),
            )

            val response = gateway.matchByBasicInfo(
                BasicInfoMatchRequest(
                    localSongId = localSong.localSongId,
                    basicInfo = basicInfo,
                    forceScenario = forceScenario,
                ),
            )

            if (response.result != MatchResult.ERROR) {
                val state = response.toLifecycleState()
                repository.saveMatchResult(
                    localSongId = localSong.localSongId,
                    matchResponse = response,
                    lifecycleState = state,
                    retryCount = attempt,
                    lastReason = response.rejectReason,
                    updatedAtMs = clock(),
                )
                return state
            }

            val reason = response.rejectReason?.lowercase()
            if (reason == "degraded") {
                repository.saveMatchResult(
                    localSongId = localSong.localSongId,
                    matchResponse = response,
                    lifecycleState = LifecycleState.WAITING_TO_CONTINUE,
                    retryCount = attempt,
                    lastReason = response.rejectReason,
                    updatedAtMs = clock(),
                )
                return LifecycleState.WAITING_TO_CONTINUE
            }

            attempt += 1
            if (attempt > maxRetryCount) {
                repository.saveMatchResult(
                    localSongId = localSong.localSongId,
                    matchResponse = response,
                    lifecycleState = LifecycleState.FAILED,
                    retryCount = attempt,
                    lastReason = response.rejectReason ?: "error",
                    updatedAtMs = clock(),
                )
                return LifecycleState.FAILED
            }
        }
    }

    fun markOutdated(localSongId: String) {
        repository.markOutdated(localSongId, updatedAtMs = clock())
    }
}
