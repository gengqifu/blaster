package com.orion.blaster.core.store

import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.model.LifecycleState
import com.orion.blaster.core.model.LocalFeature
import com.orion.blaster.core.model.LocalFeatureDiagnostics
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.LocalSongResult
import com.orion.blaster.core.model.MatchResponse
import com.orion.blaster.core.model.SourceState
import java.util.concurrent.ConcurrentHashMap

class InMemoryFeatureRepository : FeatureRepository {
    private val songs = ConcurrentHashMap<String, LocalSong>()
    private val basicInfos = ConcurrentHashMap<String, BasicSongInfo>()
    private val contentSignatures = ConcurrentHashMap<String, String?>()
    private val audioIdentitySummaries = ConcurrentHashMap<String, AudioIdentitySummary>()
    private val localFeatureDiagnostics = ConcurrentHashMap<String, LocalFeatureDiagnostics>()
    private val states = ConcurrentHashMap<String, StoredState>()

    override fun saveLocalSong(localSong: LocalSong) {
        songs[localSong.localSongId] = localSong
    }

    override fun saveBasicInfo(basicSongInfo: BasicSongInfo) {
        basicInfos[basicSongInfo.localSongId] = basicSongInfo
    }

    override fun getLocalSong(localSongId: String): LocalSong? = songs[localSongId]

    override fun getBasicInfo(localSongId: String): BasicSongInfo? = basicInfos[localSongId]

    override fun saveContentSignature(localSongId: String, contentSignature: String?) {
        contentSignatures[localSongId] = contentSignature
    }

    override fun getContentSignature(localSongId: String): String? = contentSignatures[localSongId]

    override fun saveAudioIdentitySummary(summary: AudioIdentitySummary) {
        audioIdentitySummaries[summary.localSongId] = summary
    }

    override fun getAudioIdentitySummary(localSongId: String): AudioIdentitySummary? {
        return audioIdentitySummaries[localSongId]
    }

    override fun saveLocalFeature(localSongId: String, localFeature: LocalFeature, updatedAtMs: Long) {
        val previous = states[localSongId]
        val association = previous?.result?.association
        val candidates = previous?.result?.candidates.orEmpty()
        val retryCount = previous?.retryCount ?: 0
        val lifecycleState = if (previous?.result?.lifecycleState == LifecycleState.RELIABLY_ASSOCIATED) {
            LifecycleState.RELIABLY_ASSOCIATED
        } else {
            LifecycleState.LOCAL_FEATURE_READY
        }

        states[localSongId] = StoredState(
            result = LocalSongResult(
                localSongId = localSongId,
                lifecycleState = lifecycleState,
                association = association,
                candidates = candidates,
                localFeature = localFeature,
                lastReason = previous?.lastReason,
                updatedAtMs = updatedAtMs,
            ),
            retryCount = retryCount,
            lastReason = previous?.lastReason,
            updatedAtMs = updatedAtMs,
        )
    }

    override fun getLocalFeature(localSongId: String): LocalFeature? = states[localSongId]?.result?.localFeature

    override fun saveLocalFeatureDiagnostics(localSongId: String, diagnostics: LocalFeatureDiagnostics) {
        localFeatureDiagnostics[localSongId] = diagnostics
    }

    override fun getLocalFeatureDiagnostics(localSongId: String): LocalFeatureDiagnostics? {
        return localFeatureDiagnostics[localSongId]
    }

    override fun markLocalFeatureOutdatedIfVersionChanged(
        localSongId: String,
        currentModelVersion: String,
        currentFeatureSchemaVersion: Int,
        updatedAtMs: Long,
        lastReason: String?,
    ): Boolean {
        val existingFeature = getLocalFeature(localSongId) ?: return false
        val changed = existingFeature.modelVersion != currentModelVersion ||
            existingFeature.featureSchemaVersion != currentFeatureSchemaVersion
        if (!changed) {
            return false
        }

        val previous = states[localSongId]
        val association = previous?.result?.association
        val candidates = previous?.result?.candidates.orEmpty()
        val retryCount = previous?.retryCount ?: 0
        states[localSongId] = StoredState(
            result = LocalSongResult(
                localSongId = localSongId,
                lifecycleState = LifecycleState.OUTDATED,
                association = association,
                candidates = candidates,
                localFeature = null,
                lastReason = lastReason,
                updatedAtMs = updatedAtMs,
            ),
            retryCount = retryCount,
            lastReason = lastReason,
            updatedAtMs = updatedAtMs,
        )
        return true
    }

    override fun saveMatchResult(
        localSongId: String,
        matchResponse: MatchResponse,
        lifecycleState: LifecycleState,
        retryCount: Int,
        lastReason: String?,
        updatedAtMs: Long,
    ) {
        val previous = states[localSongId]
        if (shouldKeepPreviousReliable(previous, lifecycleState)) {
            val existing = previous ?: return
            states[localSongId] = existing.copy(
                retryCount = retryCount,
                lastReason = lastReason,
                updatedAtMs = updatedAtMs,
            )
            return
        }

        states[localSongId] = StoredState(
            result = LocalSongResult(
                localSongId = localSongId,
                lifecycleState = lifecycleState,
                association = matchResponse.association,
                candidates = matchResponse.candidates,
                localFeature = previous?.result?.localFeature,
                lastReason = lastReason,
                updatedAtMs = updatedAtMs,
            ),
            retryCount = retryCount,
            lastReason = lastReason,
            updatedAtMs = updatedAtMs,
        )
    }

    override fun saveLifecycleState(
        localSongId: String,
        lifecycleState: LifecycleState,
        retryCount: Int,
        lastReason: String?,
        updatedAtMs: Long,
    ) {
        val previous = states[localSongId]
        val association = previous?.result?.association
        val candidates = previous?.result?.candidates.orEmpty()

        states[localSongId] = StoredState(
            result = LocalSongResult(
                localSongId = localSongId,
                lifecycleState = lifecycleState,
                association = association,
                candidates = candidates,
                localFeature = previous?.result?.localFeature,
                lastReason = lastReason,
                updatedAtMs = updatedAtMs,
            ),
            retryCount = retryCount,
            lastReason = lastReason,
            updatedAtMs = updatedAtMs,
        )
    }

    override fun markOutdated(localSongId: String, updatedAtMs: Long) {
        val previous = states[localSongId]
        val association = previous?.result?.association
        val candidates = previous?.result?.candidates.orEmpty()
        val lastReason = previous?.lastReason
        val retryCount = previous?.retryCount ?: 0

        states[localSongId] = StoredState(
            result = LocalSongResult(
                localSongId = localSongId,
                lifecycleState = LifecycleState.OUTDATED,
                association = association,
                candidates = candidates,
                localFeature = null,
                lastReason = lastReason,
                updatedAtMs = updatedAtMs,
            ),
            retryCount = retryCount,
            lastReason = lastReason,
            updatedAtMs = updatedAtMs,
        )
    }

    override fun markDeletedOrUnavailable(
        localSongId: String,
        sourceState: SourceState,
        updatedAtMs: Long,
        lastReason: String?,
    ) {
        val previous = states[localSongId]
        val association = previous?.result?.association
        val candidates = previous?.result?.candidates.orEmpty()
        val retryCount = previous?.retryCount ?: 0
        val lifecycleState = when (sourceState) {
            SourceState.DELETED -> LifecycleState.SKIPPED
            SourceState.UNAVAILABLE -> LifecycleState.WAITING_TO_CONTINUE
            SourceState.AVAILABLE -> previous?.result?.lifecycleState ?: LifecycleState.DISCOVERED
        }

        states[localSongId] = StoredState(
            result = LocalSongResult(
                localSongId = localSongId,
                lifecycleState = lifecycleState,
                association = association,
                candidates = candidates,
                localFeature = previous?.result?.localFeature,
                lastReason = lastReason,
                updatedAtMs = updatedAtMs,
            ),
            retryCount = retryCount,
            lastReason = lastReason,
            updatedAtMs = updatedAtMs,
        )
    }

    override fun getResult(localSongId: String): LocalSongResult? {
        return states[localSongId]?.result
    }

    override fun getResults(localSongIds: List<String>): List<LocalSongResult> {
        return localSongIds.mapNotNull { id -> states[id]?.result }
    }

    override fun getByLifecycleStates(states: Set<LifecycleState>): List<LocalSongResult> {
        return this.states.values
            .map { it.result }
            .filter { it.lifecycleState in states }
    }

    override fun getAllLocalSongIds(): Set<String> = songs.keys

    override fun getRetryCount(localSongId: String): Int = states[localSongId]?.retryCount ?: 0

    override fun getLastReason(localSongId: String): String? = states[localSongId]?.lastReason

    private fun shouldKeepPreviousReliable(
        previous: StoredState?,
        newState: LifecycleState,
    ): Boolean {
        return previous?.result?.lifecycleState == LifecycleState.RELIABLY_ASSOCIATED &&
            newState == LifecycleState.CANDIDATE_ASSOCIATED
    }

    private data class StoredState(
        val result: LocalSongResult,
        val retryCount: Int,
        val lastReason: String?,
        val updatedAtMs: Long,
    )
}
