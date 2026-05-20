package com.orion.blaster.demo

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orion.blaster.core.localfeature.model.FileEmbeddingModelSource
import com.orion.blaster.core.localfeature.model.ModelArtifactInfo
import com.orion.blaster.core.localfeature.model.YamnetModelValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class YamnetInstrumentationTest {
    @Test
    fun bundled_yamnet_model_loads_and_runs_single_inference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(context.cacheDir, "yamnet.tflite")
        context.assets.open("models/yamnet.tflite").use { input ->
            modelFile.outputStream().use { output -> input.copyTo(output) }
        }

        val summary = YamnetModelValidator(
            FileEmbeddingModelSource(
                artifact = ModelArtifactInfo(
                    modelName = "YAMNet",
                    modelVersion = "tfhub-lite-1",
                    fileName = "yamnet.tflite",
                    sourceUrl = "https://tfhub.dev/google/lite-model/yamnet/tflite/1?lite-format=tflite",
                    sha256 = "141fba1cdaae842c816f28edc4937e8b4f0af4c8df21862ccc6b52dc567993c3",
                    licenseSummary = "Apache-2.0",
                ),
                modelFile = modelFile,
            ),
        ).validateOnce()

        Log.i(
            "YamnetInstrumentationTest",
            "inputShape=${summary.inputShape} outputShapes=${summary.outputShapes} embeddingOutputIndex=${summary.selectedEmbeddingOutputIndex} embeddingVectorCount=${summary.embeddingVectorCount}",
        )

        assertTrue(summary.inputShape.isNotEmpty())
        assertTrue(summary.outputShapes.size >= 2)
        assertEquals(1, summary.selectedEmbeddingOutputIndex)
        assertTrue(summary.embeddingVectorCount > 0)
    }
}
