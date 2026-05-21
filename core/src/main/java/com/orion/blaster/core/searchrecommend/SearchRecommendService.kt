package com.orion.blaster.core.searchrecommend

import com.orion.blaster.core.store.FeatureRepository

interface SearchRecommendService {
    suspend fun search(querySongId: String, topK: Int): SearchRecommendResponse

    suspend fun recommend(seedSongId: String, topK: Int): SearchRecommendResponse
}

class DefaultSearchRecommendService(
    private val repository: FeatureRepository,
    private val engine: HybridRetrievalEngine,
    private val enabledModes: Set<SearchRecommendMode> = SearchRecommendMode.entries.toSet(),
    private val requestIdGenerator: () -> String = { "sr-${System.currentTimeMillis()}" },
) : SearchRecommendService {

    override suspend fun search(querySongId: String, topK: Int): SearchRecommendResponse {
        return execute(SearchRecommendMode.SEARCH, querySongId, topK)
    }

    override suspend fun recommend(seedSongId: String, topK: Int): SearchRecommendResponse {
        return execute(SearchRecommendMode.RECOMMEND, seedSongId, topK)
    }

    private suspend fun execute(
        mode: SearchRecommendMode,
        inputSongId: String,
        topK: Int,
    ): SearchRecommendResponse {
        if (mode !in enabledModes) {
            return unsupported(mode, "mode_disabled")
        }
        if (inputSongId.isBlank()) {
            return invalidInput(mode, "invalid_song_id", "songId is blank")
        }
        if (topK !in 1..100) {
            return invalidInput(mode, "invalid_top_k", "topK must be in 1..100")
        }
        if (repository.getLocalSong(inputSongId) == null) {
            return invalidInput(mode, "song_not_found", "Input song is not available")
        }
        val inputLocalSong = repository.getLocalSong(inputSongId) ?: return invalidInput(
            mode,
            "song_not_found",
            "Input song is not available",
        )
        return engine.execute(
            SearchRecommendRequest(
                mode = mode,
                inputSongId = inputSongId,
                topK = topK,
                inputLocalSong = inputLocalSong,
                inputBasicInfo = repository.getBasicInfo(inputSongId),
                inputAudioIdentitySummary = repository.getAudioIdentitySummary(inputSongId),
                inputLocalFeature = repository.getLocalFeature(inputSongId),
            ),
        )
    }

    private fun invalidInput(
        mode: SearchRecommendMode,
        errorCode: String,
        errorMessage: String,
    ): SearchRecommendResponse {
        return SearchRecommendResponse(
            requestId = requestIdGenerator(),
            mode = mode,
            status = SearchRecommendStatus.INVALID_INPUT,
            results = emptyList(),
            diagnostics = SearchRecommendDiagnostics(
                candidateCountBeforeRank = 0,
                candidateCountAfterRank = 0,
                latencyMs = 0L,
                degradePath = null,
            ),
            errorCode = errorCode,
            errorMessage = errorMessage,
        )
    }

    private fun unsupported(
        mode: SearchRecommendMode,
        errorCode: String,
    ): SearchRecommendResponse {
        return SearchRecommendResponse(
            requestId = requestIdGenerator(),
            mode = mode,
            status = SearchRecommendStatus.UNSUPPORTED,
            results = emptyList(),
            diagnostics = SearchRecommendDiagnostics(
                candidateCountBeforeRank = 0,
                candidateCountAfterRank = 0,
                latencyMs = 0L,
                degradePath = null,
            ),
            errorCode = errorCode,
            errorMessage = "Mode is not enabled in current build configuration",
        )
    }
}
