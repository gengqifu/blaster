package com.orion.blaster.core.searchrecommend

class HybridRetrievalEngine(
    private val sources: List<RetrievalSource>,
    private val ranker: Ranker,
    private val requestIdGenerator: () -> String = { "sr-${System.currentTimeMillis()}" },
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun execute(request: SearchRecommendRequest): SearchRecommendResponse {
        val startedAt = clockMs()
        val candidates = sources
            .flatMap { source -> source.retrieve(request) }
            .distinctBy { it.localSong.localSongId }
        val ranked = ranker.rank(request, candidates)
        val status = if (ranked.isEmpty()) SearchRecommendStatus.EMPTY else SearchRecommendStatus.OK
        return SearchRecommendResponse(
            requestId = requestIdGenerator(),
            mode = request.mode,
            status = status,
            results = ranked,
            diagnostics = SearchRecommendDiagnostics(
                candidateCountBeforeRank = candidates.size,
                candidateCountAfterRank = ranked.size,
                latencyMs = clockMs() - startedAt,
                degradePath = "local_only",
            ),
            errorCode = if (status == SearchRecommendStatus.EMPTY) "no_retrieval_signal" else null,
            errorMessage = if (status == SearchRecommendStatus.EMPTY) "No retrieval candidates available" else null,
        )
    }
}
