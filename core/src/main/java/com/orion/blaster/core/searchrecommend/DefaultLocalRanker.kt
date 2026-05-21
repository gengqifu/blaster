package com.orion.blaster.core.searchrecommend

class DefaultLocalRanker : Ranker {
    override fun rank(
        request: SearchRecommendRequest,
        candidates: List<RetrievalCandidate>,
    ): List<RankedSong> {
        return candidates
            .sortedBy { it.localSong.localSongId }
            .take(request.topK)
            .map { candidate ->
                val hasMetadata = candidate.basicInfo != null
                val hasAudioIdentity = candidate.audioIdentitySummary != null
                val hasLocalFeature = candidate.localFeature != null
                RankedSong(
                    localSongId = candidate.localSong.localSongId,
                    score = 0f,
                    reasons = listOfNotNull(
                        if (hasMetadata) "metadata_filtered" else null,
                        if (!hasAudioIdentity && hasLocalFeature) "embedding_fallback" else null,
                        if (!hasAudioIdentity) "fingerprint_missing" else null,
                        if (!hasLocalFeature) "embedding_missing" else null,
                    ).ifEmpty { listOf("metadata_filtered") },
                    signals = RankedSignals(
                        metadataMatched = hasMetadata,
                        fingerprintMatched = if (hasAudioIdentity) false else null,
                        embeddingScore = null,
                        hasMetadata = hasMetadata,
                        hasAudioIdentity = hasAudioIdentity,
                        hasLocalFeature = hasLocalFeature,
                    ),
                )
            }
    }
}
