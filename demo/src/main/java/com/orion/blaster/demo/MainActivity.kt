package com.orion.blaster.demo

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.orion.blaster.core.mock.MockCloudMatchGateway
import com.orion.blaster.core.model.LocalSong
import com.orion.blaster.core.model.SourceState
import com.orion.blaster.core.pipeline.FeaturePipeline
import com.orion.blaster.core.result.ResultProvider
import com.orion.blaster.core.store.InMemoryFeatureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val scenarios = listOf("RELIABLE", "CANDIDATE", "NONE", "ERROR", "TIMEOUT", "DEGRADED")
    private val repository = InMemoryFeatureRepository()
    private val pipeline = FeaturePipeline(
        gateway = MockCloudMatchGateway(),
        repository = repository,
    )
    private val resultProvider = ResultProvider(repository)
    private val uiScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spinner = findViewById<Spinner>(R.id.scenarioSpinner)
        val runButton = findViewById<Button>(R.id.runButton)
        val outdatedButton = findViewById<Button>(R.id.outdatedButton)
        val resultText = findViewById<TextView>(R.id.resultText)

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            scenarios,
        )

        runButton.setOnClickListener {
            val scenario = spinner.selectedItem as String
            uiScope.launch {
                pipeline.process(sampleSong(), forceScenario = scenario)
                renderResult(resultText)
            }
        }

        outdatedButton.setOnClickListener {
            pipeline.markOutdated(SAMPLE_ID)
            renderResult(resultText)
        }

        resultText.text = "Select scenario and run."
    }

    private fun sampleSong(): LocalSong {
        return LocalSong(
            localSongId = SAMPLE_ID,
            title = "Hello",
            artist = "Adele",
            album = "25",
            durationMs = 295000L,
            sourceState = SourceState.AVAILABLE,
        )
    }

    private fun renderResult(resultView: TextView) {
        val result = resultProvider.getResult(SAMPLE_ID)
        if (result == null) {
            resultView.text = "No result yet."
            return
        }

        resultView.text = buildString {
            appendLine("localSongId: ${result.localSongId}")
            appendLine("lifecycleState: ${result.lifecycleState}")
            appendLine("association: ${result.association?.cloudSongId ?: "null"}")
            appendLine("candidates: ${result.candidates.size}")
            appendLine("lastReason: ${result.lastReason ?: "null"}")
            appendLine("updatedAtMs: ${result.updatedAtMs}")
            appendLine("isReliable: ${resultProvider.isReliablyAssociated(SAMPLE_ID)}")
            appendLine("isProcessing: ${resultProvider.isProcessing(SAMPLE_ID)}")
            appendLine("isFailed: ${resultProvider.isFailed(SAMPLE_ID)}")
            appendLine("isOutdated: ${resultProvider.isOutdated(SAMPLE_ID)}")
        }
    }

    companion object {
        private const val SAMPLE_ID = "demo-song-1"
    }
}
