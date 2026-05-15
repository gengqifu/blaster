package com.orion.blaster.core.scheduler

import com.orion.blaster.core.audioqueue.AudioIdentityQueue
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.store.InMemoryFeatureRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioIdentitySchedulerTest {
    @Test
    fun runnable_batch_is_returned_when_device_allows_high_cost_work() {
        val repository = repositoryWithPendingSong("song-1")
        val scheduler = AudioIdentityScheduler(AudioIdentityQueue(repository))

        val schedule = scheduler.schedule()

        assertEquals(1, schedule.runnableItems.size)
        assertEquals(0, schedule.waitingItems.size)
        assertEquals(null, schedule.waitingReason)
    }

    @Test
    fun high_cost_disabled_pauses_pending_items() {
        val repository = repositoryWithPendingSong("song-1")
        val scheduler = AudioIdentityScheduler(
            queue = AudioIdentityQueue(repository),
            deviceStateProvider = { AudioIdentityDeviceState(highCostEnabled = false) },
        )

        val schedule = scheduler.schedule()

        assertEquals(0, schedule.runnableItems.size)
        assertEquals(1, schedule.waitingItems.size)
        assertEquals("high_cost_disabled", schedule.waitingReason)
    }

    @Test
    fun playback_low_battery_thermal_busy_and_permission_have_stable_reasons() {
        assertEquals("playback_active", AudioIdentityDeviceState(isPlaying = true).blockingReason())
        assertEquals("low_battery", AudioIdentityDeviceState(lowBattery = true).blockingReason())
        assertEquals("high_temperature", AudioIdentityDeviceState(highTemperature = true).blockingReason())
        assertEquals("foreground_busy", AudioIdentityDeviceState(foregroundBusy = true).blockingReason())
        assertEquals("media_permission_unavailable", AudioIdentityDeviceState(mediaPermissionAvailable = false).blockingReason())
    }

    private fun repositoryWithPendingSong(localSongId: String): InMemoryFeatureRepository {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(
            LocalSong(
                localSongId = localSongId,
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 1000L,
                sourceState = SourceState.AVAILABLE,
                uri = "content://$localSongId",
            ),
        )
        repository.saveBasicInfo(
            BasicSongInfo(
                localSongId = localSongId,
                title = "title",
                artist = "artist",
                album = null,
                durationMs = 1000L,
            ),
        )
        repository.saveMatchResult(
            localSongId = localSongId,
            matchResponse = MatchResponse(MatchResult.NONE, association = null),
            lifecycleState = LifecycleState.UNASSOCIATED,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )
        return repository
    }
}
