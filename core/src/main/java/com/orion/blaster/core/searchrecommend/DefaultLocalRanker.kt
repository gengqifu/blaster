package com.orion.blaster.core.searchrecommend

import com.orion.blaster.core.model.BasicSongInfo
import kotlin.math.abs
import kotlin.math.sqrt

class DefaultLocalRanker : Ranker {
    override fun rank(
        request: SearchRecommendRequest,
        candidates: List<RetrievalCandidate>,
    ): List<RankedSong> {
        val metadataCandidates = candidates.filter { candidate ->
            metadataMatches(request, candidate)
        }

        return metadataCandidates
            .mapNotNull { candidate ->
                val fingerprintMatched = fingerprintMatched(request, candidate)
                val embeddingScore = embeddingScore(request, candidate)
                val canUseEmbeddingFallback = !hasFingerprintPair(request, candidate) && embeddingScore != null

                if (!fingerprintMatched && !canUseEmbeddingFallback) {
                    return@mapNotNull null
                }

                val hasMetadata = candidate.basicInfo != null
                val hasAudioIdentity = candidate.audioIdentitySummary != null
                val hasLocalFeature = candidate.localFeature != null
                val reasons = buildList {
                    add("metadata_filtered")
                    if (fingerprintMatched) {
                        add("fingerprint_primary")
                        if (embeddingScore != null) {
                            add("embedding_tiebreak")
                        }
                    } else {
                        add("fingerprint_missing")
                        if (embeddingScore != null) {
                            add("embedding_fallback")
                        } else {
                            add("embedding_missing")
                        }
                    }
                }

                RankedSong(
                    localSongId = candidate.localSong.localSongId,
                    score = rankingScore(fingerprintMatched, embeddingScore),
                    reasons = reasons,
                    signals = RankedSignals(
                        metadataMatched = true,
                        fingerprintMatched = if (hasAudioIdentity) fingerprintMatched else null,
                        embeddingScore = embeddingScore,
                        hasMetadata = hasMetadata,
                        hasAudioIdentity = hasAudioIdentity,
                        hasLocalFeature = hasLocalFeature,
                    ),
                )
            }
            .sortedWith(
                compareByDescending<RankedSong> { it.score }
                    .thenBy { it.localSongId },
            )
            .take(request.topK)
    }

    private fun metadataMatches(
        request: SearchRecommendRequest,
        candidate: RetrievalCandidate,
    ): Boolean {
        val queryMetadata = metadataFields(request.inputBasicInfo, request.inputLocalSong)
        val candidateMetadata = metadataFields(candidate.basicInfo, candidate.localSong)
        if (queryMetadata.none { it.isNotBlank() }) {
            return true
        }

        val sharedText = queryMetadata
            .zip(candidateMetadata)
            .take(3)
            .any { (left, right) -> left.isNotBlank() && left == right }
        val durationMatches = durationClose(
            durationMs(request.inputBasicInfo, request.inputLocalSong),
            durationMs(candidate.basicInfo, candidate.localSong),
        )
        return sharedText || durationMatches
    }

    private fun fingerprintMatched(
        request: SearchRecommendRequest,
        candidate: RetrievalCandidate,
    ): Boolean {
        val queryDigest = request.inputAudioIdentitySummary?.payloadDigest
        val candidateDigest = candidate.audioIdentitySummary?.payloadDigest
        return !queryDigest.isNullOrBlank() && queryDigest == candidateDigest
    }

    private fun hasFingerprintPair(
        request: SearchRecommendRequest,
        candidate: RetrievalCandidate,
    ): Boolean {
        return !request.inputAudioIdentitySummary?.payloadDigest.isNullOrBlank() &&
            !candidate.audioIdentitySummary?.payloadDigest.isNullOrBlank()
    }

    private fun embeddingScore(
        request: SearchRecommendRequest,
        candidate: RetrievalCandidate,
    ): Float? {
        val left = request.inputLocalFeature?.embedding ?: return null
        val right = candidate.localFeature?.embedding ?: return null
        if (left.size != right.size || left.isEmpty()) {
            return null
        }
        var dot = 0f
        var leftNorm = 0f
        var rightNorm = 0f
        for (index in left.indices) {
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        if (leftNorm <= 0f || rightNorm <= 0f) {
            return null
        }
        val cosine = dot / (sqrt(leftNorm.toDouble()) * sqrt(rightNorm.toDouble())).toFloat()
        return cosine.coerceIn(-1f, 1f)
    }

    private fun rankingScore(
        fingerprintMatched: Boolean,
        embeddingScore: Float?,
    ): Float {
        return if (fingerprintMatched) {
            1f + ((embeddingScore ?: 0f).coerceAtLeast(0f) * 0.01f)
        } else {
            embeddingScore?.coerceAtLeast(0f) ?: 0f
        }
    }

    private fun metadataFields(
        basicInfo: BasicSongInfo?,
        localSong: com.orion.blaster.core.model.LocalSong,
    ): List<String> {
        return listOf(
            normalize(basicInfo?.title ?: localSong.title),
            normalize(basicInfo?.artist ?: localSong.artist),
            normalize(basicInfo?.album ?: localSong.album),
            durationMs(basicInfo, localSong)?.toString().orEmpty(),
        )
    }

    private fun durationMs(
        basicInfo: BasicSongInfo?,
        localSong: com.orion.blaster.core.model.LocalSong,
    ): Long? = basicInfo?.durationMs ?: localSong.durationMs

    private fun durationClose(left: Long?, right: Long?): Boolean {
        if (left == null || right == null) {
            return false
        }
        return abs(left - right) <= 3_000L
    }

    private fun normalize(value: String?): String {
        return value?.trim()?.lowercase().orEmpty()
    }
}
