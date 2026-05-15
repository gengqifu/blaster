package com.orion.blaster.core.audioidentity

import com.orion.blaster.core.audioqueue.AudioIdentityQueueItem
import com.orion.blaster.core.decoder.PcmDecodeFailureReason
import com.orion.blaster.core.decoder.PcmDecodeInput
import com.orion.blaster.core.decoder.PcmDecodeResult
import com.orion.blaster.core.decoder.PcmDecoder
import com.orion.blaster.core.fingerprint.AudioFingerprintInput
import com.orion.blaster.core.fingerprint.AudioFingerprintResult
import com.orion.blaster.core.fingerprint.AudioFingerprintExtractor
import com.orion.blaster.core.gateway.AudioIdentityMatchRequest
import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.model.LifecycleState

interface AudioIdentifyInputGenerator {
    fun generate(
        item: AudioIdentityQueueItem,
        forceScenario: String? = null,
    ): AudioIdentifyInputResult
}

sealed class AudioIdentifyInputResult {
    data class Success(
        val request: AudioIdentityMatchRequest,
        val summary: AudioIdentitySummary,
    ) : AudioIdentifyInputResult()

    data class Failure(
        val reason: String,
        val retryable: Boolean,
        val terminalState: LifecycleState,
    ) : AudioIdentifyInputResult()
}

class DefaultAudioIdentifyInputGenerator(
    private val pcmDecoder: PcmDecoder,
    private val fingerprintExtractor: AudioFingerprintExtractor = AudioFingerprintExtractor(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AudioIdentifyInputGenerator {
    override fun generate(
        item: AudioIdentityQueueItem,
        forceScenario: String?,
    ): AudioIdentifyInputResult {
        return when (
            val decoded = pcmDecoder.decode(
                PcmDecodeInput(
                    uri = item.uri,
                    mimeType = null,
                    durationMs = item.durationMs,
                ),
            )
        ) {
            is PcmDecodeResult.Failure -> decoded.toAudioIdentityFailure()
            is PcmDecodeResult.Success -> buildRequest(item, decoded, forceScenario)
        }
    }

    private fun buildRequest(
        item: AudioIdentityQueueItem,
        decoded: PcmDecodeResult.Success,
        forceScenario: String?,
    ): AudioIdentifyInputResult {
        return when (
            val fingerprint = fingerprintExtractor.extract(
                AudioFingerprintInput(
                    pcmBytes = decoded.audio.pcmBytes,
                    sampleRate = decoded.audio.sampleRate,
                    channelCount = decoded.audio.channelCount,
                    clipPolicy = decoded.audio.clipPolicy,
                ),
            )
        ) {
            is AudioFingerprintResult.Failure -> AudioIdentifyInputResult.Failure(
                reason = fingerprint.reason,
                retryable = true,
                terminalState = LifecycleState.FAILED,
            )

            is AudioFingerprintResult.Success -> {
                val summary = AudioIdentitySummary(
                    localSongId = item.localSongId,
                    algorithm = fingerprint.fingerprint.algorithm,
                    algorithmVersion = fingerprint.fingerprint.algorithmVersion,
                    clipPolicy = fingerprint.fingerprint.clipPolicy,
                    payloadEncoding = fingerprint.fingerprint.payloadEncoding,
                    payloadDigest = fingerprint.fingerprint.payloadDigest,
                    costMs = fingerprint.fingerprint.costMs,
                    lastReason = null,
                    updatedAtMs = clock(),
                )
                AudioIdentifyInputResult.Success(
                    request = AudioIdentityMatchRequest(
                        localSongId = item.localSongId,
                        durationMs = item.durationMs,
                        clipPolicy = fingerprint.fingerprint.clipPolicy,
                        algorithm = fingerprint.fingerprint.algorithm,
                        algorithmVersion = fingerprint.fingerprint.algorithmVersion,
                        payloadEncoding = fingerprint.fingerprint.payloadEncoding,
                        payload = fingerprint.fingerprint.payload,
                        basicInfo = item.basicInfo,
                        forceScenario = forceScenario,
                    ),
                    summary = summary,
                )
            }
        }
    }

    private fun PcmDecodeResult.Failure.toAudioIdentityFailure(): AudioIdentifyInputResult.Failure {
        return when (reason) {
            PcmDecodeFailureReason.INACCESSIBLE_URI -> AudioIdentifyInputResult.Failure(
                reason = message,
                retryable = false,
                terminalState = LifecycleState.WAITING_TO_CONTINUE,
            )

            PcmDecodeFailureReason.UNSUPPORTED_FORMAT -> AudioIdentifyInputResult.Failure(
                reason = message,
                retryable = false,
                terminalState = LifecycleState.SKIPPED,
            )

            PcmDecodeFailureReason.DECODE_ERROR -> AudioIdentifyInputResult.Failure(
                reason = message,
                retryable = true,
                terminalState = LifecycleState.FAILED,
            )
        }
    }
}
