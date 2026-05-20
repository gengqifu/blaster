package com.orion.blaster.core.featurequeue

import com.orion.blaster.core.featuretoggle.LocalFeatureToggle
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.store.FeatureRepository

data class LocalFeatureQueueItem(
    val localSongId: String,
    val currentLifecycleState: LifecycleState,
    val retryCount: Int,
)

class LocalFeatureQueue(
    private val repository: FeatureRepository,
    private val toggleProvider: () -> LocalFeatureToggle = { LocalFeatureToggle() },
) {
    fun pendingItems(): List<LocalFeatureQueueItem> {
        val toggle = toggleProvider()
        if (!toggle.enabled) {
            return emptyList()
        }

        return repository.getByLifecycleStates(eligibleStates(toggle))
            .map { result ->
                LocalFeatureQueueItem(
                    localSongId = result.localSongId,
                    currentLifecycleState = result.lifecycleState,
                    retryCount = repository.getRetryCount(result.localSongId),
                )
            }
    }

    private fun eligibleStates(toggle: LocalFeatureToggle): Set<LifecycleState> {
        return if (toggle.includeCandidateAssociated) {
            setOf(LifecycleState.UNASSOCIATED, LifecycleState.CANDIDATE_ASSOCIATED)
        } else {
            setOf(LifecycleState.UNASSOCIATED)
        }
    }
}
