package com.orion.blaster.core.store

import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.LocalSongResult
import com.orion.blaster.core.model.MatchResponse

interface FeatureRepository {
    fun saveLocalSong(localSong: LocalSong)

    fun saveBasicInfo(basicSongInfo: BasicSongInfo)

    fun saveMatchResult(
        localSongId: String,
        matchResponse: MatchResponse,
        lifecycleState: LifecycleState,
        retryCount: Int,
        lastReason: String?,
        updatedAtMs: Long,
    )

    fun saveLifecycleState(
        localSongId: String,
        lifecycleState: LifecycleState,
        retryCount: Int,
        lastReason: String?,
        updatedAtMs: Long,
    )

    fun markOutdated(localSongId: String, updatedAtMs: Long)

    fun getResult(localSongId: String): LocalSongResult?

    fun getResults(localSongIds: List<String>): List<LocalSongResult>

    fun getRetryCount(localSongId: String): Int

    fun getLastReason(localSongId: String): String?
}
