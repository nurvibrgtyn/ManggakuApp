package com.nurvi.mangoleavesdetect

import android.content.Context
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testOnCreate() {
        ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
            // Check if the views are initialized
            val imageView = activity.findViewById<ImageView>(R.id.imageView)
            val btnLoadImage = activity.findViewById<Button>(R.id.btnLoadImage)
            val btnCaptureImage = activity.findViewById<Button>(R.id.btnCaptureImage)
            val tvOutput = activity.findViewById<TextView>(R.id.tvOutput)
            val tvDescription = activity.findViewById<TextView>(R.id.tvDescription)
        }
    }

    @Test
    fun testLoadLabels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        activityRule.scenario.onActivity { mainActivity ->
            val labels = mainActivity.loadLabels("labels.txt")
            // Asumsi bahwa file labels.txt memiliki label tertentu
            assertEquals("Anthracnose (Antraknosa)", labels[0])
        }
    }

    @Test
    fun testGetMaxProbabilityIndex() {
        activityRule.scenario.onActivity { activity ->
            val probabilities = floatArrayOf(0.1f, 0.4f, 0.35f, 0.15f)
            val maxIndex = activity.getMaxProbabilityIndex(probabilities)
            assertEquals(1, maxIndex)  // Index 1 has the highest probability (0.4f)
        }
    }
}
