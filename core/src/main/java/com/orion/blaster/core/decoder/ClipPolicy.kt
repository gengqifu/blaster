package com.orion.blaster.core.decoder

data class ClipSegment(
    val startMs: Long,
    val durationMs: Long,
)

data class ClipPolicy(
    val name: String,
    val description: String,
    val segments: List<ClipSegment>,
)

class ClipPolicySelector {
    fun select(durationMs: Long?): ClipPolicy {
        val normalizedDurationMs = durationMs?.takeIf { it > 0L }
        return when {
            normalizedDurationMs == null -> ClipPolicy(
                name = "unknown-duration:middle-30s",
                description = "unknown-duration:middle-30s",
                segments = listOf(ClipSegment(startMs = 0L, durationMs = DEFAULT_SEGMENT_MS)),
            )

            normalizedDurationMs <= SHORT_AUDIO_MAX_MS -> ClipPolicy(
                name = "short:full-available",
                description = "short:full-available:${normalizedDurationMs}ms",
                segments = listOf(ClipSegment(startMs = 0L, durationMs = normalizedDurationMs)),
            )

            normalizedDurationMs <= LONG_AUDIO_MIN_MS -> {
                val duration = minOf(DEFAULT_SEGMENT_MS, normalizedDurationMs)
                val start = ((normalizedDurationMs - duration) / 2L).coerceAtLeast(0L)
                ClipPolicy(
                    name = "normal:middle-60s",
                    description = "normal:middle-60s:start=${start}ms,duration=${duration}ms",
                    segments = listOf(ClipSegment(startMs = start, durationMs = duration)),
                )
            }

            else -> {
                val duration = DEFAULT_SEGMENT_MS
                val starts = listOf(
                    normalizedDurationMs / 4L,
                    normalizedDurationMs / 2L,
                    (normalizedDurationMs * 3L) / 4L,
                ).map { start ->
                    (start - duration / 2L).coerceIn(0L, normalizedDurationMs - duration)
                }
                ClipPolicy(
                    name = "long:multi-3x60s",
                    description = "long:multi-3x60s:" + starts.joinToString(separator = ",") { "start=${it}ms" },
                    segments = starts.map { ClipSegment(startMs = it, durationMs = duration) },
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_SEGMENT_MS = 60_000L
        private const val SHORT_AUDIO_MAX_MS = 60_000L
        private const val LONG_AUDIO_MIN_MS = 600_000L
    }
}
