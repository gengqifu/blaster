package com.orion.blaster.core.audioqueue

import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.store.FeatureRepository

data class AudioIdentityQueueItem(
    val localSongId: String,
    val uri: String?,
    val durationMs: Long?,
    val contentSignature: String?,
    val basicInfo: BasicSongInfo,
    val currentLifecycleState: LifecycleState,
    val retryCount: Int,
)

class AudioIdentityQueue(
    private val repository: FeatureRepository,
) {
    fun pendingItems(): List<AudioIdentityQueueItem> {
        return repository.getByLifecycleStates(ELIGIBLE_STATES)
            .mapNotNull { result ->
                val song = repository.getLocalSong(result.localSongId) ?: return@mapNotNull null
                val basicInfo = repository.getBasicInfo(result.localSongId) ?: return@mapNotNull null
                AudioIdentityQueueItem(
                    localSongId = result.localSongId,
                    uri = song.uri,
                    durationMs = song.durationMs,
                    contentSignature = song.contentSignature ?: repository.getContentSignature(result.localSongId),
                    basicInfo = basicInfo,
                    currentLifecycleState = result.lifecycleState,
                    retryCount = repository.getRetryCount(result.localSongId),
                )
            }
    }

    companion object {
        val ELIGIBLE_STATES = setOf(
            LifecycleState.CANDIDATE_ASSOCIATED,
            LifecycleState.UNASSOCIATED,
        )
    }
}
