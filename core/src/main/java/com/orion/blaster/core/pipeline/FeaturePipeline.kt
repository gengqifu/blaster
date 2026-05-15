package com.orion.blaster.core.pipeline

import com.orion.blaster.core.audioidentity.AudioIdentifyInputGenerator
import com.orion.blaster.core.audioidentity.AudioIdentifyInputResult
import com.orion.blaster.core.gateway.BasicInfoMatchRequest
import com.orion.blaster.core.gateway.CloudMatchGateway
import com.orion.blaster.core.metadata.BasicInfoExtractionInput
import com.orion.blaster.core.metadata.BasicInfoExtractor
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.BasicInfoSource
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.MatchResult
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.model.toLifecycleState
import com.orion.blaster.core.scanner.LocalSongScanner
import com.orion.blaster.core.scheduler.AudioIdentityScheduler
import com.orion.blaster.core.store.FeatureRepository

class FeaturePipeline(
    private val gateway: CloudMatchGateway,
    private val repository: FeatureRepository,
    private val mediaStoreScanner: LocalSongScanner? = null,
    private val testResourceScanner: LocalSongScanner? = null,
    private val basicInfoExtractor: BasicInfoExtractor = BasicInfoExtractor(),
    private val audioIdentifyInputGenerator: AudioIdentifyInputGenerator? = null,
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
            source = BasicInfoSource.TEST_CONSTRUCTED,
        )
        repository.saveBasicInfo(basicInfo)
        if (localSong.contentSignature != null) {
            repository.saveContentSignature(localSong.localSongId, localSong.contentSignature)
        }
        return runBasicMatch(localSong.localSongId, basicInfo, forceScenario)
    }

    suspend fun scanAndProcess(
        source: ScanSource,
        forceScenario: String? = null,
    ): ScanProcessSummary {
        val scanner = when (source) {
            ScanSource.MEDIA_STORE -> mediaStoreScanner
            ScanSource.TEST_RESOURCES -> testResourceScanner
        } ?: throw IllegalStateException("Scanner for source $source is not configured")

        val scannedSongs = scanner.scan()
        val previousIds = repository.getAllLocalSongIds()
        val currentIds = scannedSongs.map { it.localSongId }.toSet()

        var newCount = 0
        var changedCount = 0
        var unchangedCount = 0
        var unavailableCount = 0
        var matchedCount = 0
        var skippedCount = 0

        scannedSongs.forEach { scanned ->
            val localSong = LocalSong(
                localSongId = scanned.localSongId,
                title = scanned.title,
                artist = scanned.artist,
                album = scanned.album,
                durationMs = scanned.durationMs,
                sourceState = scanned.sourceState,
                uri = scanned.uri,
                sizeBytes = scanned.sizeBytes,
                dateModified = scanned.dateModified,
                mimeType = scanned.mimeType,
                contentSignature = scanned.contentSignature,
            )
            repository.saveLocalSong(localSong)

            if (scanned.sourceState == SourceState.UNAVAILABLE) {
                unavailableCount += 1
                skippedCount += 1
                repository.markDeletedOrUnavailable(
                    localSongId = scanned.localSongId,
                    sourceState = SourceState.UNAVAILABLE,
                    updatedAtMs = clock(),
                    lastReason = "unavailable",
                )
                return@forEach
            }
            if (scanned.sourceState == SourceState.DELETED) {
                skippedCount += 1
                repository.markDeletedOrUnavailable(
                    localSongId = scanned.localSongId,
                    sourceState = SourceState.DELETED,
                    updatedAtMs = clock(),
                    lastReason = "deleted",
                )
                return@forEach
            }

            val previousSignature = repository.getContentSignature(scanned.localSongId)
            val currentSignature = scanned.contentSignature
            val isNewSong = !previousIds.contains(scanned.localSongId) || previousSignature == null
            val isChanged = !isNewSong && previousSignature != currentSignature
            val isUnchanged = !isNewSong && !isChanged

            when {
                isNewSong -> newCount += 1
                isChanged -> changedCount += 1
                else -> unchangedCount += 1
            }

            repository.saveContentSignature(scanned.localSongId, currentSignature)

            if (isUnchanged) {
                skippedCount += 1
                return@forEach
            }

            val basicInfo = basicInfoExtractor.extract(
                BasicInfoExtractionInput(
                    localSongId = scanned.localSongId,
                    mediaStore = com.orion.blaster.core.metadata.MetadataCandidate(
                        title = scanned.title,
                        artist = scanned.artist,
                        album = scanned.album,
                        durationMs = scanned.durationMs,
                    ),
                    metadataRetriever = null,
                    fileName = scanned.uri,
                ),
            )
            repository.saveBasicInfo(basicInfo)
            runBasicMatch(scanned.localSongId, basicInfo, forceScenario)
            matchedCount += 1
        }

        val deletedIds = previousIds - currentIds
        deletedIds.forEach { missingId ->
            repository.markDeletedOrUnavailable(
                localSongId = missingId,
                sourceState = SourceState.DELETED,
                updatedAtMs = clock(),
                lastReason = "missing_in_scan",
            )
        }

        return ScanProcessSummary(
            scannedCount = scannedSongs.size,
            newCount = newCount,
            changedCount = changedCount,
            unchangedCount = unchangedCount,
            deletedCount = deletedIds.size,
            unavailableCount = unavailableCount,
            matchedCount = matchedCount,
            skippedCount = skippedCount + deletedIds.size,
        )
    }

    suspend fun processAudioIdentityQueue(
        scheduler: AudioIdentityScheduler,
        forceScenario: String? = null,
        maxBatchSize: Int = Int.MAX_VALUE,
    ): AudioIdentityProcessSummary {
        val schedule = scheduler.schedule(maxBatchSize)
        if (schedule.waitingReason != null) {
            schedule.waitingItems.forEach { item ->
                repository.saveLifecycleState(
                    localSongId = item.localSongId,
                    lifecycleState = LifecycleState.WAITING_TO_CONTINUE,
                    retryCount = repository.getRetryCount(item.localSongId),
                    lastReason = schedule.waitingReason,
                    updatedAtMs = clock(),
                )
            }
            return AudioIdentityProcessSummary(
                scheduledCount = 0,
                waitingCount = schedule.waitingItems.size,
                failedCount = 0,
                reliableCount = 0,
                candidateCount = 0,
                noneCount = 0,
            )
        }

        val generator = audioIdentifyInputGenerator
            ?: throw IllegalStateException("AudioIdentifyInputGenerator is not configured")

        var failedCount = 0
        var reliableCount = 0
        var candidateCount = 0
        var noneCount = 0

        schedule.runnableItems.forEach { item ->
            val finalState = processAudioIdentityItem(item, generator, forceScenario)
            when (finalState) {
                LifecycleState.RELIABLY_ASSOCIATED -> reliableCount += 1
                LifecycleState.CANDIDATE_ASSOCIATED -> candidateCount += 1
                LifecycleState.UNASSOCIATED -> noneCount += 1
                LifecycleState.FAILED, LifecycleState.SKIPPED -> failedCount += 1
                else -> Unit
            }
        }

        return AudioIdentityProcessSummary(
            scheduledCount = schedule.runnableItems.size,
            waitingCount = 0,
            failedCount = failedCount,
            reliableCount = reliableCount,
            candidateCount = candidateCount,
            noneCount = noneCount,
        )
    }

    private suspend fun processAudioIdentityItem(
        item: com.orion.blaster.core.audioqueue.AudioIdentityQueueItem,
        generator: AudioIdentifyInputGenerator,
        forceScenario: String?,
    ): LifecycleState {
        repository.saveLifecycleState(
            localSongId = item.localSongId,
            lifecycleState = LifecycleState.AUDIO_IDENTIFYING,
            retryCount = repository.getRetryCount(item.localSongId),
            lastReason = null,
            updatedAtMs = clock(),
        )

        return when (val generated = generator.generate(item, forceScenario)) {
            is AudioIdentifyInputResult.Failure -> handleAudioIdentityGenerationFailure(item.localSongId, generated)
            is AudioIdentifyInputResult.Success -> {
                repository.saveAudioIdentitySummary(generated.summary)
                runAudioIdentityMatch(generated.request)
            }
        }
    }

    private fun handleAudioIdentityGenerationFailure(
        localSongId: String,
        failure: AudioIdentifyInputResult.Failure,
    ): LifecycleState {
        val retryCount = if (failure.retryable) {
            repository.getRetryCount(localSongId) + 1
        } else {
            repository.getRetryCount(localSongId)
        }
        val state = if (failure.retryable && retryCount <= maxRetryCount) {
            LifecycleState.WAITING_TO_CONTINUE
        } else {
            failure.terminalState
        }
        repository.saveLifecycleState(
            localSongId = localSongId,
            lifecycleState = state,
            retryCount = retryCount,
            lastReason = failure.reason,
            updatedAtMs = clock(),
        )
        return state
    }

    private suspend fun runAudioIdentityMatch(
        request: com.orion.blaster.core.gateway.AudioIdentityMatchRequest,
    ): LifecycleState {
        var attempt = repository.getRetryCount(request.localSongId)
        while (true) {
            repository.saveLifecycleState(
                localSongId = request.localSongId,
                lifecycleState = LifecycleState.AUDIO_MATCHING,
                retryCount = attempt,
                lastReason = null,
                updatedAtMs = clock(),
            )

            val response = gateway.matchByAudioIdentity(request)
            if (response.result != MatchResult.ERROR) {
                val state = response.toLifecycleState()
                repository.saveMatchResult(
                    localSongId = request.localSongId,
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
                    localSongId = request.localSongId,
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
                    localSongId = request.localSongId,
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

    private suspend fun runBasicMatch(
        localSongId: String,
        basicInfo: BasicSongInfo,
        forceScenario: String?,
    ): LifecycleState {
        repository.saveLifecycleState(
            localSongId = localSongId,
            lifecycleState = LifecycleState.BASIC_INFO_READY,
            retryCount = repository.getRetryCount(localSongId),
            lastReason = null,
            updatedAtMs = clock(),
        )
        var attempt = 0
        while (true) {
            repository.saveLifecycleState(
                localSongId = localSongId,
                lifecycleState = LifecycleState.BASIC_MATCHING,
                retryCount = attempt,
                lastReason = null,
                updatedAtMs = clock(),
            )

            val response = gateway.matchByBasicInfo(
                BasicInfoMatchRequest(
                    localSongId = localSongId,
                    basicInfo = basicInfo,
                    forceScenario = forceScenario,
                ),
            )

            if (response.result != MatchResult.ERROR) {
                val state = response.toLifecycleState()
                repository.saveMatchResult(
                    localSongId = localSongId,
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
                    localSongId = localSongId,
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
                    localSongId = localSongId,
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

enum class ScanSource {
    MEDIA_STORE,
    TEST_RESOURCES,
}

data class ScanProcessSummary(
    val scannedCount: Int,
    val newCount: Int,
    val changedCount: Int,
    val unchangedCount: Int,
    val deletedCount: Int,
    val unavailableCount: Int,
    val matchedCount: Int,
    val skippedCount: Int,
)

data class AudioIdentityProcessSummary(
    val scheduledCount: Int,
    val waitingCount: Int,
    val failedCount: Int,
    val reliableCount: Int,
    val candidateCount: Int,
    val noneCount: Int,
)
