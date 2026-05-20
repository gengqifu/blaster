package com.orion.blaster.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.orion.blaster.core.audioidentity.AudioIdentifyInputGenerator
import com.orion.blaster.core.audioidentity.AudioIdentifyInputResult
import com.orion.blaster.core.audioqueue.AudioIdentityQueue
import com.orion.blaster.core.audioqueue.AudioIdentityQueueItem
import com.orion.blaster.core.decoder.PcmDecodeFailureReason
import com.orion.blaster.core.decoder.PcmDecodeResult
import com.orion.blaster.core.decoder.PcmDecoder
import com.orion.blaster.core.embedding.LocalEmbeddingModel
import com.orion.blaster.core.featurequeue.LocalFeatureQueue
import com.orion.blaster.core.featuretoggle.LocalFeatureToggle
import com.orion.blaster.core.gateway.AudioIdentityMatchRequest
import com.orion.blaster.core.gateway.NoopCloudMatchGateway
import com.orion.blaster.core.localfeature.model.FileEmbeddingModelSource
import com.orion.blaster.core.localfeature.model.ModelArtifactInfo
import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.modelinput.AudioModelInput
import com.orion.blaster.core.modelinput.AudioModelInputGenerator
import com.orion.blaster.core.modelinput.AudioModelInputRequest
import com.orion.blaster.core.modelinput.AudioModelInputResult
import com.orion.blaster.core.modelinput.DefaultAudioModelInputGenerator
import com.orion.blaster.core.modelinput.PcmAudioProvider
import com.orion.blaster.core.pipeline.AudioIdentityProcessSummary
import com.orion.blaster.core.pipeline.FeaturePipeline
import com.orion.blaster.core.pipeline.LocalFeatureProcessSummary
import com.orion.blaster.core.pipeline.ScanProcessSummary
import com.orion.blaster.core.pipeline.ScanSource
import com.orion.blaster.core.result.ResultProvider
import com.orion.blaster.core.scanner.MediaStoreLocalSongScanner
import com.orion.blaster.core.scheduler.AudioIdentityDeviceState
import com.orion.blaster.core.scheduler.AudioIdentityScheduler
import com.orion.blaster.core.scheduler.LocalFeatureDeviceState
import com.orion.blaster.core.scheduler.LocalFeatureScheduler
import com.orion.blaster.core.store.InMemoryFeatureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        private const val READ_AUDIO_PERMISSION_REQ = 1001
    }

    private val scenarios = listOf("RELIABLE", "CANDIDATE", "NONE", "ERROR", "TIMEOUT", "DEGRADED")
    private val sources = listOf("MEDIA_STORE")
    private val audioGuards = listOf(
        "ALLOW",
        "HIGH_COST_DISABLED",
        "PLAYBACK",
        "LOW_BATTERY",
        "HIGH_TEMPERATURE",
        "FOREGROUND_BUSY",
        "NO_PERMISSION",
    )
    private val localFeatureGuards = listOf(
        "ALLOW",
        "DISABLED",
        "MODEL_MISSING",
        "PLAYBACK",
        "LOW_BATTERY",
        "HIGH_TEMPERATURE",
        "FOREGROUND_BUSY",
    )
    private val localFeatureCandidateToggle = listOf("UNASSOCIATED_ONLY", "INCLUDE_CANDIDATE")
    private val repository = InMemoryFeatureRepository()
    private val gateway = NoopCloudMatchGateway()
    private val resultProvider = ResultProvider(repository)
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private var latestSummary: ScanProcessSummary? = null
    private var latestAudioSummary: AudioIdentityProcessSummary? = null
    private var latestLocalFeatureSummary: LocalFeatureProcessSummary? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ensureAudioPermission()

        val sourceSpinner = findViewById<Spinner>(R.id.sourceSpinner)
        val spinner = findViewById<Spinner>(R.id.scenarioSpinner)
        val audioScenarioSpinner = findViewById<Spinner>(R.id.audioScenarioSpinner)
        val audioGuardSpinner = findViewById<Spinner>(R.id.audioGuardSpinner)
        val localFeatureGuardSpinner = findViewById<Spinner>(R.id.localFeatureGuardSpinner)
        val localFeatureCandidateSpinner = findViewById<Spinner>(R.id.localFeatureCandidateSpinner)
        val runButton = findViewById<Button>(R.id.runButton)
        val changedScanButton = findViewById<Button>(R.id.changedScanButton)
        val runAudioButton = findViewById<Button>(R.id.runAudioButton)
        val runLocalFeatureButton = findViewById<Button>(R.id.runLocalFeatureButton)
        val outdatedButton = findViewById<Button>(R.id.outdatedButton)
        val resultText = findViewById<TextView>(R.id.resultText)

        sourceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sources)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, scenarios)
        audioScenarioSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, scenarios)
        audioGuardSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, audioGuards)
        localFeatureGuardSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, localFeatureGuards)
        localFeatureCandidateSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, localFeatureCandidateToggle)

        runButton.setOnClickListener {
            val scenario = spinner.selectedItem as String
            uiScope.launch { runScan(ScanSource.MEDIA_STORE, scenario, resultText) }
        }
        changedScanButton.setOnClickListener {
            val scenario = spinner.selectedItem as String
            uiScope.launch { runScan(ScanSource.MEDIA_STORE, scenario, resultText) }
        }
        runAudioButton.setOnClickListener {
            val scenario = audioScenarioSpinner.selectedItem as String
            val guard = audioGuardSpinner.selectedItem as String
            uiScope.launch { runAudioIdentity(scenario, guard, resultText) }
        }
        runLocalFeatureButton.setOnClickListener {
            val guard = localFeatureGuardSpinner.selectedItem as String
            val includeCandidate = (localFeatureCandidateSpinner.selectedItem as String) == "INCLUDE_CANDIDATE"
            uiScope.launch { runLocalFeature(guard, includeCandidate, resultText) }
        }
        outdatedButton.setOnClickListener {
            repository.getAllLocalSongIds().firstOrNull()?.let { songId ->
                buildPipeline().markOutdated(songId)
                renderResult(resultText)
            } ?: Toast.makeText(this, "Run scan first.", Toast.LENGTH_SHORT).show()
        }
        resultText.text = "Use MEDIA_STORE, then run scan -> audio identity -> local feature."
    }

    private suspend fun runScan(source: ScanSource, scenario: String, resultView: TextView) {
        if (source == ScanSource.MEDIA_STORE && !hasAudioPermission()) {
            resultView.text = "Audio permission is required for MEDIA_STORE scan."
            ensureAudioPermission()
            return
        }
        latestSummary = buildPipeline().scanAndProcess(source = source, forceScenario = scenario)
        renderResult(resultView)
    }

    private suspend fun runAudioIdentity(scenario: String, guard: String, resultView: TextView) {
        if (latestSummary == null) {
            resultView.text = "Run scan first."
            return
        }
        val scheduler = AudioIdentityScheduler(
            queue = AudioIdentityQueue(repository),
            deviceStateProvider = { deviceStateForGuard(guard) },
        )
        latestAudioSummary = buildPipeline().processAudioIdentityQueue(scheduler = scheduler, forceScenario = scenario)
        renderResult(resultView)
    }

    private fun runLocalFeature(guard: String, includeCandidate: Boolean, resultView: TextView) {
        if (latestSummary == null) {
            resultView.text = "Run scan first."
            return
        }
        val scheduler = LocalFeatureScheduler(
            queue = LocalFeatureQueue(
                repository = repository,
                toggleProvider = {
                    LocalFeatureToggle(enabled = guard != "DISABLED", includeCandidateAssociated = includeCandidate)
                },
            ),
            deviceStateProvider = { localFeatureDeviceStateForGuard(guard) },
        )
        latestLocalFeatureSummary = buildPipeline().processLocalFeatureQueue(scheduler = scheduler)
        renderResult(resultView)
    }

    private fun renderResult(resultView: TextView) {
        val summary = latestSummary ?: run {
            resultView.text = "No scan result yet."
            return
        }
        val songIds = repository.getAllLocalSongIds().sorted()
        val results = resultProvider.getResults(songIds)
        resultView.text = buildString {
            appendLine("summary:")
            appendLine(
                "scanned=${summary.scannedCount}, new=${summary.newCount}, changed=${summary.changedCount}, " +
                    "unchanged=${summary.unchangedCount}",
            )
            appendLine(
                "deleted=${summary.deletedCount}, unavailable=${summary.unavailableCount}, " +
                    "matched=${summary.matchedCount}, skipped=${summary.skippedCount}",
            )
            latestAudioSummary?.let { audio ->
                appendLine(
                    "audioIdentity: scheduled=${audio.scheduledCount}, waiting=${audio.waitingCount}, " +
                        "failed=${audio.failedCount}, reliable=${audio.reliableCount}, " +
                        "candidate=${audio.candidateCount}, none=${audio.noneCount}",
                )
            }
            latestLocalFeatureSummary?.let { local ->
                appendLine(
                    "localFeature: scheduled=${local.scheduledCount}, waiting=${local.waitingCount}, " +
                        "ready=${local.readyCount}, failed=${local.failedCount}, skipped=${local.skippedCount}",
                )
            }
            appendLine()
            appendLine("scanned songs:")
            songIds.forEach { songId ->
                repository.getLocalSong(songId)?.let { song ->
                    appendLine(
                        "${song.localSongId} | title=${song.title ?: "null"} | artist=${song.artist ?: "null"} | " +
                            "signature=${song.contentSignature ?: "null"} | state=${song.sourceState}",
                    )
                }
            }
            appendLine()
            appendLine("result provider:")
            results.forEach { result ->
                val localFeature = result.localFeature
                appendLine(
                    "${result.localSongId} | state=${result.lifecycleState} | " +
                        "association=${result.association?.cloudSongId ?: "null"} | " +
                        "embedding=${localFeature?.embedding?.size ?: 0} | " +
                        "model=${localFeature?.modelName ?: "null"}@${localFeature?.modelVersion ?: "null"} | " +
                        "schema=${localFeature?.featureSchemaVersion ?: "null"} | " +
                        "lastReason=${result.lastReason ?: "null"}",
                )
            }
        }
    }

    private fun buildPipeline(): FeaturePipeline {
        return FeaturePipeline(
            gateway = gateway,
            repository = repository,
            audioIdentifyInputGenerator = MainAudioIdentifyInputGenerator(),
            audioModelInputGenerator = MainAudioModelInputGenerator(),
            localEmbeddingModel = createLocalEmbeddingModel(),
            testResourceScanner = null,
            mediaStoreScanner = MediaStoreLocalSongScanner(this),
        )
    }

    private fun deviceStateForGuard(guard: String): AudioIdentityDeviceState = when (guard) {
        "HIGH_COST_DISABLED" -> AudioIdentityDeviceState(highCostEnabled = false)
        "PLAYBACK" -> AudioIdentityDeviceState(isPlaying = true)
        "LOW_BATTERY" -> AudioIdentityDeviceState(lowBattery = true)
        "HIGH_TEMPERATURE" -> AudioIdentityDeviceState(highTemperature = true)
        "FOREGROUND_BUSY" -> AudioIdentityDeviceState(foregroundBusy = true)
        "NO_PERMISSION" -> AudioIdentityDeviceState(mediaPermissionAvailable = false)
        else -> AudioIdentityDeviceState()
    }

    private fun localFeatureDeviceStateForGuard(guard: String): LocalFeatureDeviceState = when (guard) {
        "DISABLED" -> LocalFeatureDeviceState(enabled = false)
        "MODEL_MISSING" -> LocalFeatureDeviceState(modelAvailable = false)
        "PLAYBACK" -> LocalFeatureDeviceState(isPlaying = true)
        "LOW_BATTERY" -> LocalFeatureDeviceState(lowBattery = true)
        "HIGH_TEMPERATURE" -> LocalFeatureDeviceState(highTemperature = true)
        "FOREGROUND_BUSY" -> LocalFeatureDeviceState(foregroundBusy = true)
        else -> LocalFeatureDeviceState()
    }

    private fun createLocalEmbeddingModel(): LocalEmbeddingModel {
        val modelFile = File(cacheDir, "yamnet.tflite")
        if (!modelFile.exists()) {
            assets.open("models/yamnet.tflite").use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return LocalEmbeddingModel(
            modelSource = FileEmbeddingModelSource(
                artifact = ModelArtifactInfo(
                    modelName = "YAMNet",
                    modelVersion = "tfhub-lite-1",
                    fileName = "yamnet.tflite",
                    sourceUrl = "https://tfhub.dev/google/lite-model/yamnet/tflite/1?lite-format=tflite",
                    sha256 = "8b2bf88f794c0f6f3040f9f8ce7eb197f8026ed2c50df2f6cc7bb67b0b16f2f3",
                    licenseSummary = "Apache-2.0",
                    associatedFiles = listOf("yamnet_label_list.txt"),
                ),
                modelFile = modelFile,
            ),
        )
    }

    private inner class MainAudioModelInputGenerator : AudioModelInputGenerator {
        private val delegate = DefaultAudioModelInputGenerator(
            pcmAudioProvider = PcmAudioProvider { input ->
                if (input.uri == null) {
                    PcmDecodeResult.Failure(PcmDecodeFailureReason.INACCESSIBLE_URI, "uri is null")
                } else {
                    PcmDecoder(applicationContext).decode(input)
                }
            },
        )

        override fun generate(request: AudioModelInputRequest): AudioModelInputResult {
            if (request.uri == null) {
                return AudioModelInputResult.Failure("inaccessible_uri:uri is null")
            }
            return delegate.generate(request)
        }
    }

    private class MainAudioIdentifyInputGenerator : AudioIdentifyInputGenerator {
        override fun generate(item: AudioIdentityQueueItem, forceScenario: String?): AudioIdentifyInputResult {
            val payloadText = "audio-identity:${item.localSongId}:${item.contentSignature ?: "no-signature"}"
            val payload = payloadText.toByteArray(Charsets.UTF_8)
            val digest = "digest:${payload.size}:${payload.firstOrNull()?.toInt() ?: 0}"
            return AudioIdentifyInputResult.Success(
                request = AudioIdentityMatchRequest(
                    localSongId = item.localSongId,
                    durationMs = item.durationMs,
                    clipPolicy = "middle-60s",
                    algorithm = "chromaprint-compatible",
                    algorithmVersion = "mvp4-local",
                    payloadEncoding = "base64",
                    payload = payload,
                    basicInfo = item.basicInfo,
                    forceScenario = forceScenario,
                ),
                summary = AudioIdentitySummary(
                    localSongId = item.localSongId,
                    algorithm = "chromaprint-compatible",
                    algorithmVersion = "mvp4-local",
                    clipPolicy = "middle-60s",
                    payloadEncoding = "base64",
                    payloadDigest = digest,
                    costMs = 1L,
                    lastReason = null,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasAudioPermission(): Boolean =
        checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun ensureAudioPermission() {
        if (!hasAudioPermission()) {
            requestPermissions(arrayOf(audioPermission()), READ_AUDIO_PERMISSION_REQ)
        }
    }
}
