package com.orion.blaster.core.pipeline

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
import com.orion.blaster.core.store.FeatureRepository

class FeaturePipeline(
    private val gateway: CloudMatchGateway,
    private val repository: FeatureRepository,
    private val mediaStoreScanner: LocalSongScanner? = null,
    private val testResourceScanner: LocalSongScanner? = null,
    private val basicInfoExtractor: BasicInfoExtractor = BasicInfoExtractor(),
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
