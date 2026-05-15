package com.orion.blaster.core.signature

import com.orion.blaster.core.model.QualityFlag
import java.security.MessageDigest

data class ContentSignatureInput(
    val uri: String?,
    val sizeBytes: Long?,
    val dateModified: Long?,
    val durationMs: Long?,
)

data class ContentSignatureComputation(
    val contentSignature: String,
    val qualityFlags: Set<QualityFlag>,
)

class ContentSignatureGenerator {
    fun generate(input: ContentSignatureInput): ContentSignatureComputation {
        val parts = listOf(
            input.uri?.trim(),
            input.sizeBytes?.toString(),
            input.dateModified?.toString(),
            input.durationMs?.toString(),
        ).map { it ?: NULL_PLACEHOLDER }

        val raw = parts.joinToString(separator = "|")
        val hash = sha256(raw)
        val hasDegradedInput = parts.any { it == NULL_PLACEHOLDER }
        val qualityFlags = if (hasDegradedInput) {
            setOf(QualityFlag.DEGRADED_SIGNATURE_INPUT)
        } else {
            emptySet()
        }
        return ContentSignatureComputation(
            contentSignature = hash,
            qualityFlags = qualityFlags,
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val NULL_PLACEHOLDER: String = "<null>"
    }
}
