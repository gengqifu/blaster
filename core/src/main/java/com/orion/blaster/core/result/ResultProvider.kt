package com.orion.blaster.core.result

import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSongResult
import com.orion.blaster.core.store.FeatureRepository

class ResultProvider(
    private val repository: FeatureRepository,
) {
    fun getResult(localSongId: String): LocalSongResult? = repository.getResult(localSongId)

    fun getResults(localSongIds: List<String>): List<LocalSongResult> = repository.getResults(localSongIds)

    fun isReliablyAssociated(localSongId: String): Boolean {
        val result = repository.getResult(localSongId) ?: return false
        return result.lifecycleState == LifecycleState.RELIABLY_ASSOCIATED
    }

    fun isProcessing(localSongId: String): Boolean {
        val state = repository.getResult(localSongId)?.lifecycleState ?: return false
        return state == LifecycleState.DISCOVERED ||
            state == LifecycleState.BASIC_INFO_READY ||
            state == LifecycleState.BASIC_MATCHING ||
            state == LifecycleState.AUDIO_IDENTIFYING ||
            state == LifecycleState.AUDIO_MATCHING ||
            state == LifecycleState.WAITING_TO_CONTINUE
    }

    fun isFailed(localSongId: String): Boolean {
        return repository.getResult(localSongId)?.lifecycleState == LifecycleState.FAILED
    }

    fun isSkipped(localSongId: String): Boolean {
        return repository.getResult(localSongId)?.lifecycleState == LifecycleState.SKIPPED
    }

    fun isOutdated(localSongId: String): Boolean {
        return repository.getResult(localSongId)?.lifecycleState == LifecycleState.OUTDATED
    }
}
