package com.orion.blaster.core.model

enum class SourceState {
    AVAILABLE,
    UNAVAILABLE,
    DELETED,
}

enum class BasicInfoSource {
    MEDIA_STORE,
    METADATA_RETRIEVER,
    FILENAME_FALLBACK,
    TEST_CONSTRUCTED,
}

enum class QualityFlag {
    MISSING_FIELD,
    GARBLED_TEXT,
    CONFLICTING_METADATA,
    FALLBACK_USED,
    DEGRADED_SIGNATURE_INPUT,
}

data class LocalSong(
    val localSongId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val sourceState: SourceState,
    val uri: String? = null,
    val sizeBytes: Long? = null,
    val dateModified: Long? = null,
    val mimeType: String? = null,
    val contentSignature: String? = null,
)

data class BasicSongInfo(
    val localSongId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val source: BasicInfoSource = BasicInfoSource.TEST_CONSTRUCTED,
    val qualityFlags: Set<QualityFlag> = emptySet(),
)

enum class MatchResult {
    RELIABLE,
    CANDIDATE,
    NONE,
    ERROR,
}

enum class AssociationStage {
    BASIC_INFO,
    AUDIO_IDENTITY,
}

data class CloudAssociation(
    val cloudSongId: String,
    val stage: AssociationStage,
    val isReliable: Boolean,
)

data class CloudCandidate(
    val cloudSongId: String,
    val reason: String?,
    val score: Float?,
)

data class MatchResponse(
    val result: MatchResult,
    val association: CloudAssociation?,
    val candidates: List<CloudCandidate> = emptyList(),
    val rejectReason: String? = null,
)

enum class LifecycleState {
    DISCOVERED,
    BASIC_INFO_READY,
    BASIC_MATCHING,
    RELIABLY_ASSOCIATED,
    CANDIDATE_ASSOCIATED,
    UNASSOCIATED,
    WAITING_TO_CONTINUE,
    OUTDATED,
    SKIPPED,
    FAILED,
    AUDIO_IDENTIFYING,
    AUDIO_MATCHING,
    LOCAL_FEATURE_EXTRACTING,
    LOCAL_FEATURE_READY,
}

data class LocalFeature(
    val embedding: FloatArray,
    val modelName: String,
    val modelVersion: String,
    val featureSchemaVersion: Int,
    val generatedAtMs: Long,
)

data class LocalFeatureTopClass(
    val label: String,
    val score: Float,
)

data class LocalFeatureDiagnostics(
    val localSongId: String,
    val modelName: String,
    val modelVersion: String,
    val featureSchemaVersion: Int,
    val inputStrategy: String,
    val outputTensorShape: List<Int>,
    val costMs: Long?,
    val topClasses: List<LocalFeatureTopClass> = emptyList(),
    val failureReason: String?,
    val generatedAtMs: Long,
)

data class AudioIdentitySummary(
    val localSongId: String,
    val algorithm: String,
    val algorithmVersion: String,
    val clipPolicy: String,
    val payloadEncoding: String,
    val payloadDigest: String?,
    val costMs: Long?,
    val lastReason: String?,
    val updatedAtMs: Long,
)

data class LocalSongResult(
    val localSongId: String,
    val lifecycleState: LifecycleState,
    val association: CloudAssociation?,
    val candidates: List<CloudCandidate> = emptyList(),
    val localFeature: LocalFeature? = null,
    val lastReason: String? = null,
    val updatedAtMs: Long,
)

fun MatchResponse.toLifecycleState(): LifecycleState =
    when (result) {
        MatchResult.RELIABLE -> LifecycleState.RELIABLY_ASSOCIATED
        MatchResult.CANDIDATE -> LifecycleState.CANDIDATE_ASSOCIATED
        MatchResult.NONE -> LifecycleState.UNASSOCIATED
        MatchResult.ERROR -> when (rejectReason?.lowercase()) {
            "degraded" -> LifecycleState.WAITING_TO_CONTINUE
            else -> LifecycleState.FAILED
        }
    }

fun LocalSongResult.isReliablyAssociated(): Boolean = lifecycleState == LifecycleState.RELIABLY_ASSOCIATED
