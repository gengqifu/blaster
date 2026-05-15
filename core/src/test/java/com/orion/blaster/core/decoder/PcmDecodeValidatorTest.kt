package com.orion.blaster.core.decoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcmDecodeValidatorTest {
    @Test
    fun missing_uri_is_inaccessible_uri_failure() {
        val failure = PcmDecodeValidator.validate(
            PcmDecodeInput(uri = null, mimeType = "audio/mpeg", durationMs = 1000L),
        )

        assertEquals(PcmDecodeFailureReason.INACCESSIBLE_URI, failure?.reason)
    }

    @Test
    fun unsupported_mime_type_is_explicit_failure() {
        val failure = PcmDecodeValidator.validate(
            PcmDecodeInput(uri = "content://song", mimeType = "audio/x-ape", durationMs = 1000L),
        )

        assertEquals(PcmDecodeFailureReason.UNSUPPORTED_FORMAT, failure?.reason)
    }

    @Test
    fun common_android_audio_mime_types_are_supported() {
        listOf(
            "audio/mpeg",
            "audio/aac",
            "audio/mp4",
            "audio/x-m4a",
            "audio/wav",
            "audio/flac",
            "audio/ogg",
        ).forEach { mimeType ->
            assertNull(
                PcmDecodeValidator.validate(
                    PcmDecodeInput(uri = "content://song", mimeType = mimeType, durationMs = 1000L),
                ),
            )
        }
    }
}
