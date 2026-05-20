package com.orion.blaster.demo

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
import com.orion.blaster.core.decoder.PcmDecodeInput
import com.orion.blaster.core.decoder.PcmDecodeResult
import com.orion.blaster.core.decoder.PcmDecoder
import com.orion.blaster.core.embedding.LocalEmbeddingGenerationResult
import com.orion.blaster.core.embedding.LocalEmbeddingModel
import com.orion.blaster.core.featurequeue.LocalFeatureQueue
import com.orion.blaster.core.featuretoggle.LocalFeatureToggle
import com.orion.blaster.core.gateway.AudioIdentityMatchRequest
import com.orion.blaster.core.localfeature.model.FileEmbeddingModelSource
import com.orion.blaster.core.localfeature.model.ModelArtifactInfo
import com.orion.blaster.core.mock.MockCloudMatchGateway
import com.orion.blaster.core.model.AudioIdentitySummary
import com.orion.blaster.core.model.LocalFeature
import com.orion.blaster.core.model.LocalFeatureDiagnostics
import com.orion.blaster.core.pipeline.FeaturePipeline
import com.orion.blaster.core.pipeline.AudioIdentityProcessSummary
import com.orion.blaster.core.pipeline.LocalFeatureProcessSummary
import com.orion.blaster.core.pipeline.ScanProcessSummary
import com.orion.blaster.core.pipeline.ScanSource
import com.orion.blaster.core.modelinput.AudioModelInput
import com.orion.blaster.core.modelinput.AudioModelInputGenerator
import com.orion.blaster.core.modelinput.AudioModelInputRequest
import com.orion.blaster.core.modelinput.AudioModelInputResult
import com.orion.blaster.core.modelinput.DefaultAudioModelInputGenerator
import com.orion.blaster.core.modelinput.PcmAudioProvider
import com.orion.blaster.core.result.ResultProvider
import com.orion.blaster.core.scanner.TestLocalSongScanner
import com.orion.blaster.core.scanner.TestSongRecord
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
    private val scenarios = listOf("RELIABLE", "CANDIDATE", "NONE", "ERROR", "TIMEOUT", "DEGRADED")
    private val sources = listOf("TEST_RESOURCES", "MEDIA_STORE")
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
    private val gateway = MockCloudMatchGateway()
    private val resultProvider = ResultProvider(repository)
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var latestSummary: ScanProcessSummary? = null
    private var latestAudioSummary: AudioIdentityProcessSummary? = null
    private var latestLocalFeatureSummary: LocalFeatureProcessSummary? = null
    private var latestRecords: List<TestSongRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        sourceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sources,
        )

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            scenarios,
        )

        audioScenarioSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            scenarios,
        )

        audioGuardSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            audioGuards,
        )
        localFeatureGuardSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            localFeatureGuards,
        )
        localFeatureCandidateSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            localFeatureCandidateToggle,
        )

        runButton.setOnClickListener {
            val scenario = spinner.selectedItem as String
            val selectedSource = sourceSpinner.selectedItem as String
            uiScope.launch {
                runScan(
                    sourceLabel = selectedSource,
                    scenario = scenario,
                    changedSignature = false,
                    resultView = resultText,
                )
            }
        }

        changedScanButton.setOnClickListener {
            val scenario = spinner.selectedItem as String
            val selectedSource = sourceSpinner.selectedItem as String
            uiScope.launch {
                runScan(
                    sourceLabel = selectedSource,
                    scenario = scenario,
                    changedSignature = true,
                    resultView = resultText,
                )
            }
        }

        runAudioButton.setOnClickListener {
            val scenario = audioScenarioSpinner.selectedItem as String
            val guard = audioGuardSpinner.selectedItem as String
            uiScope.launch {
                runAudioIdentity(
                    scenario = scenario,
                    guard = guard,
                    resultView = resultText,
                )
            }
        }

        runLocalFeatureButton.setOnClickListener {
            val guard = localFeatureGuardSpinner.selectedItem as String
            val candidateToggle = localFeatureCandidateSpinner.selectedItem as String
            uiScope.launch {
                runLocalFeature(
                    guard = guard,
                    includeCandidate = candidateToggle == "INCLUDE_CANDIDATE",
                    resultView = resultText,
                )
            }
        }

        outdatedButton.setOnClickListener {
            latestRecords.firstOrNull()?.let { record ->
                buildPipeline(stableRecords(), stableRecords()).markOutdated(record.localSongId)
                renderResult(resultText)
            } ?: run {
                Toast.makeText(this, "Run scan first.", Toast.LENGTH_SHORT).show()
            }
        }

        resultText.text = "Select source/scenario, run scan, then run audio identity and local feature."
    }

    private suspend fun runScan(
        sourceLabel: String,
        scenario: String,
        changedSignature: Boolean,
        resultView: TextView,
    ) {
        val source = if (sourceLabel == "TEST_RESOURCES") {
            ScanSource.TEST_RESOURCES
        } else {
            ScanSource.MEDIA_STORE
        }
        if (source == ScanSource.MEDIA_STORE) {
            resultView.text = "MEDIA_STORE demo scanner not wired yet. Use TEST_RESOURCES for MVP-2 acceptance."
            return
        }

        val stable = stableRecords()
        val changed = changedSignatureRecords()
        latestRecords = if (changedSignature) changed else stable

        val pipeline = buildPipeline(stable, changed)
        latestSummary = pipeline.scanAndProcess(
            source = source,
            forceScenario = scenario,
        )
        renderResult(resultView)
    }

    private suspend fun runAudioIdentity(
        scenario: String,
        guard: String,
        resultView: TextView,
    ) {
        if (latestSummary == null) {
            resultView.text = "Run scan first, preferably with basic scenario NONE or CANDIDATE."
            return
        }

        val scheduler = AudioIdentityScheduler(
            queue = AudioIdentityQueue(repository),
            deviceStateProvider = { deviceStateForGuard(guard) },
        )
        latestAudioSummary = buildPipeline(stableRecords(), changedSignatureRecords()).processAudioIdentityQueue(
            scheduler = scheduler,
            forceScenario = scenario,
        )
        renderResult(resultView)
    }

    private fun runLocalFeature(
        guard: String,
        includeCandidate: Boolean,
        resultView: TextView,
    ) {
        if (latestSummary == null) {
            resultView.text = "Run scan first, preferably with basic scenario NONE or CANDIDATE."
            return
        }

        val scheduler = LocalFeatureScheduler(
            queue = LocalFeatureQueue(
                repository = repository,
                toggleProvider = {
                    LocalFeatureToggle(
                        enabled = guard != "DISABLED",
                        includeCandidateAssociated = includeCandidate,
                    )
                },
            ),
            deviceStateProvider = { localFeatureDeviceStateForGuard(guard) },
        )
        latestLocalFeatureSummary = buildPipeline(stableRecords(), changedSignatureRecords()).processLocalFeatureQueue(
            scheduler = scheduler,
        )
        renderResult(resultView)
    }

    private fun renderResult(resultView: TextView) {
        val summary = latestSummary
        if (summary == null) {
            resultView.text = "No scan result yet."
            return
        }

        val songIds = repository.getAllLocalSongIds().sorted()
        val results = resultProvider.getResults(songIds)

        resultView.text = buildString {
            appendLine("summary:")
            appendLine("scanned=${summary.scannedCount}, new=${summary.newCount}, changed=${summary.changedCount}, unchanged=${summary.unchangedCount}")
            appendLine("deleted=${summary.deletedCount}, unavailable=${summary.unavailableCount}, matched=${summary.matchedCount}, skipped=${summary.skippedCount}")
            latestAudioSummary?.let { audio ->
                appendLine("audioIdentity: scheduled=${audio.scheduledCount}, waiting=${audio.waitingCount}, failed=${audio.failedCount}, reliable=${audio.reliableCount}, candidate=${audio.candidateCount}, none=${audio.noneCount}")
            }
            latestLocalFeatureSummary?.let { local ->
                appendLine("localFeature: scheduled=${local.scheduledCount}, waiting=${local.waitingCount}, ready=${local.readyCount}, failed=${local.failedCount}, skipped=${local.skippedCount}")
            }
            appendLine()
            appendLine("scanned songs:")
            latestRecords.forEach { record ->
                appendLine("${record.localSongId} | title=${record.title ?: "null"} | artist=${record.artist ?: "null"} | signature=${record.contentSignature ?: "null"} | state=${record.sourceState}")
            }
            appendLine()
            appendLine("audio identity queue:")
            AudioIdentityQueue(repository).pendingItems().forEach { item ->
                appendLine("${item.localSongId} | state=${item.currentLifecycleState} | uri=${item.uri ?: "null"} | duration=${item.durationMs ?: "null"} | retry=${item.retryCount}")
            }
            appendLine()
            appendLine("local feature queue:")
            LocalFeatureQueue(repository).pendingItems().forEach { item ->
                appendLine("${item.localSongId} | state=${item.currentLifecycleState} | uri=${item.uri ?: "null"} | duration=${item.durationMs ?: "null"} | retry=${item.retryCount}")
            }
            appendLine()
            appendLine("audio identity summaries:")
            songIds.forEach { songId ->
                repository.getAudioIdentitySummary(songId)?.let { summary ->
                    appendLine("${summary.localSongId} | algorithm=${summary.algorithm} | version=${summary.algorithmVersion} | clip=${summary.clipPolicy} | encoding=${summary.payloadEncoding} | digest=${summary.payloadDigest ?: "null"} | costMs=${summary.costMs ?: "null"} | reason=${summary.lastReason ?: "null"}")
                }
            }
            appendLine()
            appendLine("local feature diagnostics:")
            songIds.forEach { songId ->
                repository.getLocalFeatureDiagnostics(songId)?.let { summary ->
                    appendLine("${summary.localSongId} | model=${summary.modelName}@${summary.modelVersion} | schema=${summary.featureSchemaVersion} | shape=${summary.outputTensorShape} | costMs=${summary.costMs ?: "null"} | topK=${summary.topClasses.joinToString { "${it.label}:${it.score}" }} | failure=${summary.failureReason ?: "null"}")
                }
            }
            appendLine()
            appendLine("result provider:")
            results.forEach { result ->
                val signature = repository.getContentSignature(result.localSongId)
                val localFeature = result.localFeature
                appendLine(
                    "${result.localSongId} | state=${result.lifecycleState} | signature=${signature ?: "null"} | association=${result.association?.cloudSongId ?: "null"} | candidates=${result.candidates.size} | embedding=${localFeature?.embedding?.size ?: 0} | model=${localFeature?.modelName ?: "null"}@${localFeature?.modelVersion ?: "null"} | schema=${localFeature?.featureSchemaVersion ?: "null"} | lastReason=${result.lastReason ?: "null"}",
                )
            }
        }
    }

    private fun buildPipeline(
        stableRecords: List<TestSongRecord>,
        changedRecords: List<TestSongRecord>,
    ): FeaturePipeline {
        return FeaturePipeline(
            gateway = gateway,
            repository = repository,
            audioIdentifyInputGenerator = DemoAudioIdentifyInputGenerator(),
            audioModelInputGenerator = DemoAudioModelInputGenerator(),
            localEmbeddingModel = createLocalEmbeddingModel(),
            testResourceScanner = TestLocalSongScanner(stableRecords),
            mediaStoreScanner = object : com.orion.blaster.core.scanner.LocalSongScanner {
                override fun scan(): List<com.orion.blaster.core.scanner.ScannedLocalSong> = emptyList()
            },
        ).let { pipeline ->
            if (latestRecords === changedRecords) {
                FeaturePipeline(
                    gateway = gateway,
                    repository = repository,
                    audioIdentifyInputGenerator = DemoAudioIdentifyInputGenerator(),
                    audioModelInputGenerator = DemoAudioModelInputGenerator(),
                    localEmbeddingModel = createLocalEmbeddingModel(),
                    testResourceScanner = TestLocalSongScanner(changedRecords),
                    mediaStoreScanner = object : com.orion.blaster.core.scanner.LocalSongScanner {
                        override fun scan(): List<com.orion.blaster.core.scanner.ScannedLocalSong> = emptyList()
                    },
                )
            } else {
                pipeline
            }
        }
    }

    private fun stableRecords(): List<TestSongRecord> {
        return listOf(
            TestSongRecord(
                localSongId = "demo-song-1",
                uri = "content://demo/song1",
                title = "Hello",
                artist = "Adele",
                album = "25",
                durationMs = 295000L,
                sizeBytes = 1111L,
                dateModified = 100L,
                mimeType = "audio/mpeg",
                sourceState = com.orion.blaster.core.model.SourceState.AVAILABLE,
                contentSignature = "sig-stable-1",
            ),
            TestSongRecord(
                localSongId = "demo-song-2",
                uri = "content://demo/song2",
                title = "Numb",
                artist = "Linkin Park",
                album = "Meteora",
                durationMs = 185000L,
                sizeBytes = 2222L,
                dateModified = 200L,
                mimeType = "audio/mpeg",
                sourceState = com.orion.blaster.core.model.SourceState.AVAILABLE,
                contentSignature = "sig-stable-2",
            ),
            TestSongRecord(
                localSongId = "demo-song-3",
                uri = null,
                title = null,
                artist = null,
                album = null,
                durationMs = null,
                sourceState = com.orion.blaster.core.model.SourceState.UNAVAILABLE,
                contentSignature = null,
            ),
        )
    }

    private fun changedSignatureRecords(): List<TestSongRecord> {
        return stableRecords().map {
            if (it.localSongId == "demo-song-2") {
                it.copy(contentSignature = "sig-changed-2")
            } else {
                it
            }
        }
    }

    private fun deviceStateForGuard(guard: String): AudioIdentityDeviceState {
        return when (guard) {
            "HIGH_COST_DISABLED" -> AudioIdentityDeviceState(highCostEnabled = false)
            "PLAYBACK" -> AudioIdentityDeviceState(isPlaying = true)
            "LOW_BATTERY" -> AudioIdentityDeviceState(lowBattery = true)
            "HIGH_TEMPERATURE" -> AudioIdentityDeviceState(highTemperature = true)
            "FOREGROUND_BUSY" -> AudioIdentityDeviceState(foregroundBusy = true)
            "NO_PERMISSION" -> AudioIdentityDeviceState(mediaPermissionAvailable = false)
            else -> AudioIdentityDeviceState()
        }
    }

    private fun localFeatureDeviceStateForGuard(guard: String): LocalFeatureDeviceState {
        return when (guard) {
            "DISABLED" -> LocalFeatureDeviceState(enabled = false)
            "MODEL_MISSING" -> LocalFeatureDeviceState(modelAvailable = false)
            "PLAYBACK" -> LocalFeatureDeviceState(isPlaying = true)
            "LOW_BATTERY" -> LocalFeatureDeviceState(lowBattery = true)
            "HIGH_TEMPERATURE" -> LocalFeatureDeviceState(highTemperature = true)
            "FOREGROUND_BUSY" -> LocalFeatureDeviceState(foregroundBusy = true)
            else -> LocalFeatureDeviceState()
        }
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

    private inner class DemoAudioModelInputGenerator : AudioModelInputGenerator {
        private val fallback = DefaultAudioModelInputGenerator(
            pcmAudioProvider = PcmAudioProvider { input ->
                if (input.uri == null) {
                    PcmDecodeResult.Failure(PcmDecodeFailureReason.INACCESSIBLE_URI, "uri is null")
                } else {
                    PcmDecoder(applicationContext).decode(input)
                }
            },
        )

        override fun generate(request: AudioModelInputRequest): AudioModelInputResult {
            if (request.uri?.startsWith("content://demo/") == true) {
                return AudioModelInputResult.Success(
                    AudioModelInput(
                        localSongId = request.localSongId,
                        values = FloatArray(DefaultAudioModelInputGenerator.DEFAULT_TARGET_SAMPLE_COUNT),
                        inputStrategy = "demo-generated-waveform",
                        sourceSampleRate = 16000,
                        sourceChannelCount = 1,
                    ),
                )
            }
            return fallback.generate(request)
        }
    }

    private class DemoAudioIdentifyInputGenerator : AudioIdentifyInputGenerator {
        override fun generate(
            item: AudioIdentityQueueItem,
            forceScenario: String?,
        ): AudioIdentifyInputResult {
            val payloadText = "demo-audio-identity:${item.localSongId}:${item.contentSignature ?: "no-signature"}"
            val payload = payloadText.toByteArray(Charsets.UTF_8)
            val digest = "demo:${payload.size}:${payload.firstOrNull()?.toInt() ?: 0}"
            return AudioIdentifyInputResult.Success(
                request = AudioIdentityMatchRequest(
                    localSongId = item.localSongId,
                    durationMs = item.durationMs,
                    clipPolicy = "demo:middle-60s",
                    algorithm = "chromaprint-compatible",
                    algorithmVersion = "demo-mock-1",
                    payloadEncoding = "demo-chromaprint-base64",
                    payload = payload,
                    basicInfo = item.basicInfo,
                    forceScenario = forceScenario,
                ),
                summary = AudioIdentitySummary(
                    localSongId = item.localSongId,
                    algorithm = "chromaprint-compatible",
                    algorithmVersion = "demo-mock-1",
                    clipPolicy = "demo:middle-60s",
                    payloadEncoding = "demo-chromaprint-base64",
                    payloadDigest = digest,
                    costMs = 1L,
                    lastReason = null,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }
}
