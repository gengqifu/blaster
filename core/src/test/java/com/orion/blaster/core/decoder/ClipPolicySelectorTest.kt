package com.orion.blaster.core.decoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipPolicySelectorTest {
    private val selector = ClipPolicySelector()

    @Test
    fun short_audio_uses_full_available_duration() {
        val policy = selector.select(45_000L)

        assertEquals("short:full-available", policy.name)
        assertEquals(listOf(ClipSegment(0L, 45_000L)), policy.segments)
        assertTrue(policy.description.contains("45000ms"))
    }

    @Test
    fun normal_audio_uses_middle_segment() {
        val policy = selector.select(180_000L)

        assertEquals("normal:middle-60s", policy.name)
        assertEquals(listOf(ClipSegment(60_000L, 60_000L)), policy.segments)
    }

    @Test
    fun long_audio_uses_three_representative_segments() {
        val policy = selector.select(1_200_000L)

        assertEquals("long:multi-3x60s", policy.name)
        assertEquals(3, policy.segments.size)
        assertEquals(270_000L, policy.segments[0].startMs)
        assertEquals(570_000L, policy.segments[1].startMs)
        assertEquals(870_000L, policy.segments[2].startMs)
        assertTrue(policy.segments.all { it.durationMs == 60_000L })
    }

    @Test
    fun unknown_duration_uses_safe_default_segment() {
        val policy = selector.select(null)

        assertEquals("unknown-duration:middle-30s", policy.name)
        assertEquals(listOf(ClipSegment(0L, 60_000L)), policy.segments)
    }
}
