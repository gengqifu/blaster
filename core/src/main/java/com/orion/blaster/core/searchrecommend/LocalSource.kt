package com.orion.blaster.core.searchrecommend

import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.store.FeatureRepository

class LocalSource(
    private val repository: FeatureRepository,
) : RetrievalSource {
    override suspend fun retrieve(request: SearchRecommendRequest): List<RetrievalCandidate> {
        return repository.getAllLocalSongIds()
            .asSequence()
            .filter { it != request.inputSongId }
            .mapNotNull { localSongId ->
                val localSong = repository.getLocalSong(localSongId) ?: return@mapNotNull null
                if (localSong.sourceState != SourceState.AVAILABLE) {
                    return@mapNotNull null
                }
                RetrievalCandidate(
                    localSong = localSong,
                    basicInfo = repository.getBasicInfo(localSongId),
                    result = repository.getResult(localSongId),
                    audioIdentitySummary = repository.getAudioIdentitySummary(localSongId),
                    localFeature = repository.getLocalFeature(localSongId),
                )
            }
            .toList()
    }
}
