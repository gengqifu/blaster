package com.orion.blaster.core.scheduler

import com.orion.blaster.core.audioqueue.AudioIdentityQueue
import com.orion.blaster.core.audioqueue.AudioIdentityQueueItem

data class AudioIdentityDeviceState(
    val highCostEnabled: Boolean = true,
    val isPlaying: Boolean = false,
    val lowBattery: Boolean = false,
    val highTemperature: Boolean = false,
    val foregroundBusy: Boolean = false,
    val mediaPermissionAvailable: Boolean = true,
) {
    fun blockingReason(): String? = when {
        !highCostEnabled -> "high_cost_disabled"
        isPlaying -> "playback_active"
        lowBattery -> "low_battery"
        highTemperature -> "high_temperature"
        foregroundBusy -> "foreground_busy"
        !mediaPermissionAvailable -> "media_permission_unavailable"
        else -> null
    }
}

data class AudioIdentitySchedule(
    val runnableItems: List<AudioIdentityQueueItem>,
    val waitingItems: List<AudioIdentityQueueItem>,
    val waitingReason: String?,
)

class AudioIdentityScheduler(
    private val queue: AudioIdentityQueue,
    private val deviceStateProvider: () -> AudioIdentityDeviceState = { AudioIdentityDeviceState() },
) {
    fun schedule(maxBatchSize: Int = Int.MAX_VALUE): AudioIdentitySchedule {
        val pending = queue.pendingItems()
        val waitingReason = deviceStateProvider().blockingReason()
        return if (waitingReason != null) {
            AudioIdentitySchedule(
                runnableItems = emptyList(),
                waitingItems = pending,
                waitingReason = waitingReason,
            )
        } else {
            AudioIdentitySchedule(
                runnableItems = pending.take(maxBatchSize),
                waitingItems = emptyList(),
                waitingReason = null,
            )
        }
    }
}
