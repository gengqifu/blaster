package com.orion.blaster.core.searchrecommend

interface RetrievalSource {
    suspend fun retrieve(request: SearchRecommendRequest): List<RetrievalCandidate>
}
