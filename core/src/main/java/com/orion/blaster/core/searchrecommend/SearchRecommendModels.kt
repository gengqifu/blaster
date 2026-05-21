package com.orion.blaster.core.searchrecommend

import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LocalFeature
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.LocalSongResult

enum class SearchRecommendMode {
    SEARCH,
    RECOMMEND,
}

enum class SearchRecommendStatus {
    OK,
    EMPTY,
    INVALID_INPUT,
    UNSUPPORTED,
}

data class RankedSignals(
    val metadataMatched: Boolean,
    val fingerprintMatched: Boolean?,
    val embeddingScore: Float?,
    val hasMetadata: Boolean,
    val hasAudioIdentity: Boolean,
    val hasLocalFeature: Boolean,
)

data class RankedSong(
    val localSongId: String,
    val score: Float,
    val reasons: List<String>,
    val signals: RankedSignals,
)

data class SearchRecommendDiagnostics(
    val candidateCountBeforeRank: Int,
    val candidateCountAfterRank: Int,
    val latencyMs: Long,
    val degradePath: String?,
)

data class SearchRecommendResponse(
    val requestId: String,
    val mode: SearchRecommendMode,
    val status: SearchRecommendStatus,
    val results: List<RankedSong>,
    val diagnostics: SearchRecommendDiagnostics,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

data class SearchRecommendRequest(
    val mode: SearchRecommendMode,
    val inputSongId: String,
    val topK: Int,
)

data class RetrievalCandidate(
    val localSong: LocalSong,
    val basicInfo: BasicSongInfo?,
    val result: LocalSongResult?,
    val audioIdentitySummary: AudioIdentitySummary?,
    val localFeature: LocalFeature?,
)
