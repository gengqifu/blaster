package com.orion.blaster.core.searchrecommend

interface Ranker {
    fun rank(
        request: SearchRecommendRequest,
        candidates: List<RetrievalCandidate>,
    ): List<RankedSong>
}
