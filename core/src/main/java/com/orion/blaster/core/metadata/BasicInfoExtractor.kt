package com.orion.blaster.core.metadata

import com.orion.blaster.core.model.BasicInfoSource
import com.orion.blaster.core.model.BasicSongInfo
import com.orion.blaster.core.model.QualityFlag

data class MetadataCandidate(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
)

data class BasicInfoExtractionInput(
    val localSongId: String,
    val mediaStore: MetadataCandidate? = null,
    val metadataRetriever: MetadataCandidate? = null,
    val fileName: String? = null,
)

class BasicInfoExtractor {
    fun extract(input: BasicInfoExtractionInput): BasicSongInfo {
        val qualityFlags = linkedSetOf<QualityFlag>()

        val media = input.mediaStore?.normalize(qualityFlags)
        val retriever = input.metadataRetriever?.normalize(qualityFlags)
        val fallback = parseFileNameFallback(input.fileName)?.normalize(qualityFlags)

        detectConflicts(media, retriever, qualityFlags)

        val title = media?.title ?: retriever?.title ?: fallback?.title
        val artist = media?.artist ?: retriever?.artist ?: fallback?.artist
        val album = media?.album ?: retriever?.album ?: fallback?.album
        val durationMs = media?.durationMs ?: retriever?.durationMs ?: fallback?.durationMs

        val source = when {
            media?.hasAnyValue() == true -> BasicInfoSource.MEDIA_STORE
            retriever?.hasAnyValue() == true -> BasicInfoSource.METADATA_RETRIEVER
            fallback?.hasAnyValue() == true -> BasicInfoSource.FILENAME_FALLBACK
            else -> BasicInfoSource.TEST_CONSTRUCTED
        }

        if (source == BasicInfoSource.FILENAME_FALLBACK) {
            qualityFlags += QualityFlag.FALLBACK_USED
        }
        if (title == null || artist == null || album == null || durationMs == null) {
            qualityFlags += QualityFlag.MISSING_FIELD
        }

        return BasicSongInfo(
            localSongId = input.localSongId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            source = source,
            qualityFlags = qualityFlags.toSet(),
        )
    }

    private fun MetadataCandidate.normalize(
        qualityFlags: MutableSet<QualityFlag>,
    ): MetadataCandidate {
        return MetadataCandidate(
            title = normalizeText(title, qualityFlags),
            artist = normalizeText(artist, qualityFlags),
            album = normalizeText(album, qualityFlags),
            durationMs = durationMs,
        )
    }

    private fun normalizeText(
        value: String?,
        qualityFlags: MutableSet<QualityFlag>,
    ): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains('\uFFFD')) {
            qualityFlags += QualityFlag.GARBLED_TEXT
            return null
        }
        return trimmed
    }

    private fun parseFileNameFallback(fileName: String?): MetadataCandidate? {
        if (fileName.isNullOrBlank()) return null
        val raw = fileName.substringAfterLast('/').substringBeforeLast('.')
        val normalized = raw.trim()
        if (normalized.isEmpty()) return null

        val parts = normalized.split(" - ", limit = 2)
        return if (parts.size == 2) {
            MetadataCandidate(
                artist = parts[0].trim().ifBlank { null },
                title = parts[1].trim().ifBlank { null },
            )
        } else {
            MetadataCandidate(title = normalized)
        }
    }

    private fun detectConflicts(
        media: MetadataCandidate?,
        retriever: MetadataCandidate?,
        qualityFlags: MutableSet<QualityFlag>,
    ) {
        if (media == null || retriever == null) return
        val hasConflict = listOf(
            media.title to retriever.title,
            media.artist to retriever.artist,
            media.album to retriever.album,
            media.durationMs?.toString() to retriever.durationMs?.toString(),
        ).any { (left, right) -> left != null && right != null && left != right }
        if (hasConflict) {
            qualityFlags += QualityFlag.CONFLICTING_METADATA
        }
    }

    private fun MetadataCandidate.hasAnyValue(): Boolean {
        return title != null || artist != null || album != null || durationMs != null
    }
}
