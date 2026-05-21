package com.orion.blaster.core.searchrecommend

import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.model.BasicInfoSource
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.LocalFeature
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
    fun search_ranks_metadata_plus_fingerprint_as_primary() = runBlocking {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(song("seed"))
        repository.saveBasicInfo(basic("seed", "Song", "Artist"))
        repository.saveAudioIdentitySummary(audioIdentity("seed", "digest-a"))

        repository.saveLocalSong(song("candidate-fingerprint"))
        repository.saveBasicInfo(basic("candidate-fingerprint", "Song", "Artist"))
        repository.saveAudioIdentitySummary(audioIdentity("candidate-fingerprint", "digest-a"))

        val response = service(repository).search("seed", 5)

        assertEquals(SearchRecommendStatus.OK, response.status)
        assertEquals("candidate-fingerprint", response.results.first().localSongId)
        assertTrue(response.results.first().reasons.contains("fingerprint_primary"))
    }

    @Test
    fun search_uses_embedding_fallback_when_fingerprint_is_missing() = runBlocking {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(song("seed"))
        repository.saveBasicInfo(basic("seed", "Song", "Artist"))
        repository.saveLocalFeature("seed", embedding(floatArrayOf(1f, 0f)), 1L)

        repository.saveLocalSong(song("candidate-embedding"))
        repository.saveBasicInfo(basic("candidate-embedding", "Song", "Artist"))
        repository.saveLocalFeature("candidate-embedding", embedding(floatArrayOf(1f, 0f)), 1L)

        val response = service(repository).search("seed", 5)

        assertEquals(SearchRecommendStatus.OK, response.status)
        assertEquals("candidate-embedding", response.results.first().localSongId)
        assertTrue(response.results.first().reasons.contains("embedding_fallback"))
        assertTrue(response.results.first().reasons.contains("fingerprint_missing"))
    }

    @Test
    fun search_supports_mixed_result_sets() = runBlocking {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(song("seed"))
        repository.saveBasicInfo(basic("seed", "Song", "Artist"))
        repository.saveAudioIdentitySummary(audioIdentity("seed", "digest-a"))
        repository.saveLocalFeature("seed", embedding(floatArrayOf(1f, 0f)), 1L)

        repository.saveLocalSong(song("candidate-fingerprint"))
        repository.saveBasicInfo(basic("candidate-fingerprint", "Song", "Artist"))
        repository.saveAudioIdentitySummary(audioIdentity("candidate-fingerprint", "digest-a"))

        repository.saveLocalSong(song("candidate-embedding"))
        repository.saveBasicInfo(basic("candidate-embedding", "Song", "Artist"))
        repository.saveLocalFeature("candidate-embedding", embedding(floatArrayOf(1f, 0f)), 1L)

        val response = service(repository).search("seed", 5)

        assertEquals(SearchRecommendStatus.OK, response.status)
        assertEquals(2, response.results.size)
        assertTrue(response.results.any { it.reasons.contains("fingerprint_primary") })
        assertTrue(response.results.any { it.reasons.contains("embedding_fallback") })
    }

    @Test
    fun search_returns_empty_for_no_signal_candidates() = runBlocking {
        val repository = InMemoryFeatureRepository()
        repository.saveLocalSong(song("seed"))
        repository.saveBasicInfo(basic("seed", "Song", "Artist"))

        repository.saveLocalSong(song("candidate-no-signal"))
        repository.saveBasicInfo(basic("candidate-no-signal", "Song", "Artist"))

        val response = service(repository).search("seed", 5)

        assertEquals(SearchRecommendStatus.EMPTY, response.status)
        assertTrue(response.results.isEmpty())
        assertEquals("no_retrieval_signal", response.errorCode)
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

    private fun basic(localSongId: String, title: String, artist: String): BasicSongInfo {
        return BasicSongInfo(
            localSongId = localSongId,
            title = title,
            artist = artist,
            album = "Album",
            durationMs = 1000L,
            source = BasicInfoSource.TEST_CONSTRUCTED,
        )
    }

    private fun audioIdentity(localSongId: String, digest: String): AudioIdentitySummary {
        return AudioIdentitySummary(
            localSongId = localSongId,
            algorithm = "chromaprint-compatible",
            algorithmVersion = "1.6.0",
            clipPolicy = "full",
            payloadEncoding = "base64",
            payloadDigest = digest,
            costMs = 10L,
            lastReason = null,
            updatedAtMs = 1L,
        )
    }

    private fun embedding(values: FloatArray): LocalFeature {
        return LocalFeature(
            embedding = values,
            modelName = "YAMNet",
            modelVersion = "tfhub-lite-1",
            featureSchemaVersion = 1,
            generatedAtMs = 1L,
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
