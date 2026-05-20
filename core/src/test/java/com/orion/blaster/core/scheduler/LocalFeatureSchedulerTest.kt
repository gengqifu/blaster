package com.orion.blaster.core.scheduler

import com.orion.blaster.core.featurequeue.LocalFeatureQueue
import com.orion.blaster.core.featuretoggle.LocalFeatureToggle
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.store.InMemoryFeatureRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalFeatureSchedulerTest {
    @Test
    fun runnable_batch_is_returned_when_device_allows_work() {
        val repository = repositoryWithPendingSong("song-1", LifecycleState.UNASSOCIATED)
        val queue = LocalFeatureQueue(repository)
        val scheduler = LocalFeatureScheduler(queue)

        val schedule = scheduler.schedule()

        assertEquals(1, schedule.runnableItems.size)
        assertEquals(0, schedule.waitingItems.size)
        assertEquals(null, schedule.waitingReason)
    }

    @Test
    fun model_unavailable_and_playback_have_stable_reasons() {
        assertEquals("model_unavailable", LocalFeatureDeviceState(modelAvailable = false).blockingReason())
        assertEquals("playback_active", LocalFeatureDeviceState(isPlaying = true).blockingReason())
        assertEquals("low_battery", LocalFeatureDeviceState(lowBattery = true).blockingReason())
        assertEquals("high_temperature", LocalFeatureDeviceState(highTemperature = true).blockingReason())
        assertEquals("foreground_busy", LocalFeatureDeviceState(foregroundBusy = true).blockingReason())
        assertEquals("high_cost_disabled", LocalFeatureDeviceState(enabled = false).blockingReason())
    }

    @Test
    fun candidate_is_included_only_when_toggle_allows() {
        val repository = repositoryWithPendingSong("song-candidate", LifecycleState.CANDIDATE_ASSOCIATED)
        val defaultScheduler = LocalFeatureScheduler(LocalFeatureQueue(repository))
        assertEquals(0, defaultScheduler.schedule().runnableItems.size)

        val enabledScheduler = LocalFeatureScheduler(
            LocalFeatureQueue(
                repository,
                toggleProvider = { LocalFeatureToggle(enabled = true, includeCandidateAssociated = true) },
            ),
        )
        assertEquals(1, enabledScheduler.schedule().runnableItems.size)
    }

    private fun repositoryWithPendingSong(localSongId: String, state: LifecycleState): InMemoryFeatureRepository {
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
            lifecycleState = state,
            retryCount = 0,
            lastReason = null,
            updatedAtMs = 1L,
        )
        return repository
    }
}
