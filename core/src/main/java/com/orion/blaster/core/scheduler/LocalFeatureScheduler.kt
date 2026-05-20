package com.orion.blaster.core.scheduler

import com.orion.blaster.core.featurequeue.LocalFeatureQueue
import com.orion.blaster.core.featurequeue.LocalFeatureQueueItem

data class LocalFeatureDeviceState(
    val enabled: Boolean = true,
    val modelAvailable: Boolean = true,
    val isPlaying: Boolean = false,
    val lowBattery: Boolean = false,
    val highTemperature: Boolean = false,
    val foregroundBusy: Boolean = false,
) {
    fun blockingReason(): String? = when {
        !enabled -> "high_cost_disabled"
        !modelAvailable -> "model_unavailable"
        isPlaying -> "playback_active"
        lowBattery -> "low_battery"
        highTemperature -> "high_temperature"
        foregroundBusy -> "foreground_busy"
        else -> null
    }
}

data class LocalFeatureSchedule(
    val runnableItems: List<LocalFeatureQueueItem>,
    val waitingItems: List<LocalFeatureQueueItem>,
    val waitingReason: String?,
)

class LocalFeatureScheduler(
    private val queue: LocalFeatureQueue,
    private val deviceStateProvider: () -> LocalFeatureDeviceState = { LocalFeatureDeviceState() },
) {
    fun schedule(maxBatchSize: Int = Int.MAX_VALUE): LocalFeatureSchedule {
        val pending = queue.pendingItems()
        val waitingReason = deviceStateProvider().blockingReason()
        return if (waitingReason != null) {
            LocalFeatureSchedule(
                runnableItems = emptyList(),
                waitingItems = pending,
                waitingReason = waitingReason,
            )
        } else {
            LocalFeatureSchedule(
                runnableItems = pending.take(maxBatchSize),
                waitingItems = emptyList(),
                waitingReason = null,
            )
        }
    }
}
