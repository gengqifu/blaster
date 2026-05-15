package com.orion.blaster.core.signature

import com.orion.blaster.core.model.QualityFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSignatureGeneratorTest {
    private val generator = ContentSignatureGenerator()

    @Test
    fun same_input_generates_stable_signature() {
        val input = ContentSignatureInput(
            uri = " content://media/1 ",
            sizeBytes = 1234L,
            dateModified = 10L,
            durationMs = 1000L,
        )

        val first = generator.generate(input)
        val second = generator.generate(input)

        assertEquals(first.contentSignature, second.contentSignature)
        assertTrue(first.qualityFlags.isEmpty())
    }

    @Test
    fun null_fields_use_placeholder_and_mark_degraded_flag() {
        val result = generator.generate(
            ContentSignatureInput(
                uri = "content://media/2",
                sizeBytes = null,
                dateModified = 10L,
                durationMs = null,
            ),
        )

        assertTrue(result.qualityFlags.contains(QualityFlag.DEGRADED_SIGNATURE_INPUT))
    }

    @Test
    fun field_order_is_significant_for_signature() {
        val a = generator.generate(
            ContentSignatureInput(
                uri = "content://media/3",
                sizeBytes = 10L,
                dateModified = 20L,
                durationMs = 30L,
            ),
        )
        val b = generator.generate(
            ContentSignatureInput(
                uri = "content://media/3",
                sizeBytes = 30L,
                dateModified = 20L,
                durationMs = 10L,
            ),
        )

        assertNotEquals(a.contentSignature, b.contentSignature)
    }
}
