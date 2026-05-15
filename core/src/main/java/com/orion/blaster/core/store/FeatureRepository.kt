package com.orion.blaster.core.store

import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.LocalSongResult
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.SourceState

interface FeatureRepository {
    fun saveLocalSong(localSong: LocalSong)

    fun saveBasicInfo(basicSongInfo: BasicSongInfo)

    fun getLocalSong(localSongId: String): LocalSong?

    fun getBasicInfo(localSongId: String): BasicSongInfo?

    fun saveContentSignature(localSongId: String, contentSignature: String?)

    fun getContentSignature(localSongId: String): String?

    fun saveAudioIdentitySummary(summary: AudioIdentitySummary)

    fun getAudioIdentitySummary(localSongId: String): AudioIdentitySummary?

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

    fun markDeletedOrUnavailable(
        localSongId: String,
        sourceState: SourceState,
        updatedAtMs: Long,
        lastReason: String?,
    )

    fun getResult(localSongId: String): LocalSongResult?

    fun getResults(localSongIds: List<String>): List<LocalSongResult>

    fun getByLifecycleStates(states: Set<LifecycleState>): List<LocalSongResult>

    fun getAllLocalSongIds(): Set<String>

    fun getRetryCount(localSongId: String): Int

    fun getLastReason(localSongId: String): String?
}
