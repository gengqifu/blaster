package com.orion.blaster.core.searchrecommend

import com.orion.blaster.core.model.BasicInfoSource
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.store.InMemoryFeatureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRecommendServiceTest {

    @Test
    fun search_returns_unsupported_only_when_mode_is_disabled() = runBlocking {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(song("seed"))

        val service = DefaultSearchRecommendService(
            repository = repository,
            engine = HybridRetrievalEngine(
                sources = listOf(LocalSource(repository)),
                ranker = DefaultLocalRanker(),
                requestIdGenerator = { "req-1" },
                clockMs = { 100L },
            ),
            enabledModes = setOf(SearchRecommendMode.RECOMMEND),
            requestIdGenerator = { "req-unsupported" },
        )

        val response = service.search("seed", 5)

        assertEquals(SearchRecommendStatus.UNSUPPORTED, response.status)
        assertEquals("mode_disabled", response.errorCode)
    }

    @Test
    fun search_returns_invalid_input_for_missing_song_or_invalid_top_k() = runBlocking {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(song("seed"))
        val service = service(repository)

        val missingSong = service.search("missing", 5)
        val invalidTopK = service.search("seed", 0)

        assertEquals(SearchRecommendStatus.INVALID_INPUT, missingSong.status)
        assertEquals("song_not_found", missingSong.errorCode)
        assertEquals(SearchRecommendStatus.INVALID_INPUT, invalidTopK.status)
        assertEquals("invalid_top_k", invalidTopK.errorCode)
    }

    @Test
    fun search_uses_local_source_and_returns_empty_without_candidates() = runBlocking {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(song("seed"))
        repository.saveBasicInfo(
            BasicSongInfo(
                localSongId = "seed",
                title = "Seed",
                artist = "Artist",
                album = "Album",
                durationMs = 1000L,
                source = BasicInfoSource.TEST_CONSTRUCTED,
            ),
        )
        val service = service(repository)

        val response = service.search("seed", 5)

        assertEquals(SearchRecommendStatus.EMPTY, response.status)
        assertEquals("local_only", response.diagnostics.degradePath)
        assertEquals("no_retrieval_signal", response.errorCode)
    }

    @Test
    fun search_returns_ranked_candidates_from_local_source() = runBlocking {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(song("seed"))
        repository.saveBasicInfo(
            BasicSongInfo(
                localSongId = "seed",
                title = "Seed",
                artist = "Artist",
                album = "Album",
                durationMs = 1000L,
                source = BasicInfoSource.TEST_CONSTRUCTED,
            ),
        )
        repository.saveLocalSong(song("candidate-b"))
        repository.saveBasicInfo(
            BasicSongInfo(
                localSongId = "candidate-b",
                title = "Candidate B",
                artist = "Artist",
                album = "Album",
                durationMs = 1000L,
                source = BasicInfoSource.TEST_CONSTRUCTED,
            ),
        )
        repository.saveLocalSong(song("candidate-a"))
        repository.saveBasicInfo(
            BasicSongInfo(
                localSongId = "candidate-a",
                title = "Candidate A",
                artist = "Artist",
                album = "Album",
                durationMs = 1000L,
                source = BasicInfoSource.TEST_CONSTRUCTED,
            ),
        )
        val service = service(repository)

        val response = service.search("seed", 1)

        assertEquals(SearchRecommendStatus.OK, response.status)
        assertEquals(2, response.diagnostics.candidateCountBeforeRank)
        assertEquals(1, response.results.size)
        assertEquals("candidate-a", response.results.first().localSongId)
        assertTrue(response.results.first().reasons.contains("metadata_filtered"))
    }

    private fun service(repository: InMemoryFeatureRepository): DefaultSearchRecommendService {
        return DefaultSearchRecommendService(
            repository = repository,
            engine = HybridRetrievalEngine(
                sources = listOf(LocalSource(repository)),
                ranker = DefaultLocalRanker(),
                requestIdGenerator = { "req-1" },
                clockMs = { 100L },
            ),
            requestIdGenerator = { "svc-1" },
        )
    }

    private fun song(localSongId: String): LocalSong {
        return LocalSong(
            localSongId = localSongId,
            title = localSongId,
            artist = "artist",
            album = "album",
            durationMs = 1000L,
            sourceState = SourceState.AVAILABLE,
            uri = "content://media/$localSongId",
        )
    }
}
