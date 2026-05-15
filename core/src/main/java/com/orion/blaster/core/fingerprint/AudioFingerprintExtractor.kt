package com.orion.blaster.core.fingerprint

import com.orion.blaster.core.decoder.ClipPolicy
import java.security.MessageDigest

data class AudioFingerprintInput(
    val pcmBytes: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val clipPolicy: ClipPolicy,
)

data class AudioFingerprint(
    val algorithm: String,
    val algorithmVersion: String,
    val clipPolicy: String,
    val payloadEncoding: String,
    val payload: ByteArray,
    val payloadDigest: String,
    val costMs: Long,
)

sealed class AudioFingerprintResult {
    data class Success(val fingerprint: AudioFingerprint) : AudioFingerprintResult()
    data class Failure(val reason: String) : AudioFingerprintResult()
}

interface ChromaprintBridge {
    fun fingerprintPcm16(pcmBytes: ByteArray, sampleRate: Int, channelCount: Int): String
    fun version(): String
}

class NativeChromaprintBridge : ChromaprintBridge {
    init {
        System.loadLibrary("blaster_chromaprint_jni")
    }

    override fun fingerprintPcm16(pcmBytes: ByteArray, sampleRate: Int, channelCount: Int): String {
        return fingerprintPcm16Native(pcmBytes, sampleRate, channelCount)
    }

    override fun version(): String = versionNative()

    private external fun fingerprintPcm16Native(
        pcmBytes: ByteArray,
        sampleRate: Int,
        channelCount: Int,
    ): String

    private external fun versionNative(): String
}

class AudioFingerprintExtractor(
    private val bridge: ChromaprintBridge = NativeChromaprintBridge(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun extract(input: AudioFingerprintInput): AudioFingerprintResult {
        if (input.pcmBytes.isEmpty()) {
            return AudioFingerprintResult.Failure("pcm bytes are empty")
        }
        if (input.sampleRate <= 0 || input.channelCount <= 0) {
            return AudioFingerprintResult.Failure("invalid pcm format")
        }

        val startedAtMs = clock()
        return try {
            val payloadText = bridge.fingerprintPcm16(input.pcmBytes, input.sampleRate, input.channelCount)
            if (payloadText.isBlank()) {
                AudioFingerprintResult.Failure("chromaprint payload is empty")
            } else {
                val payload = payloadText.toByteArray(Charsets.UTF_8)
                AudioFingerprintResult.Success(
                    AudioFingerprint(
                        algorithm = ALGORITHM,
                        algorithmVersion = bridge.version(),
                        clipPolicy = input.clipPolicy.description,
                        payloadEncoding = PAYLOAD_ENCODING,
                        payload = payload,
                        payloadDigest = sha256(payload),
                        costMs = (clock() - startedAtMs).coerceAtLeast(0L),
                    ),
                )
            }
        } catch (error: Throwable) {
            AudioFingerprintResult.Failure(error.message ?: error::class.java.simpleName)
        }
    }

    private fun sha256(payload: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        return "sha256:" + digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val ALGORITHM = "chromaprint-compatible"
        const val PAYLOAD_ENCODING = "chromaprint-base64"
    }
}
