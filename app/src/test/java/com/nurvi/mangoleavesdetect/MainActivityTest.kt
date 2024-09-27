package com.nurvi.mangoleavesdetect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainActivityTest {

    @Test
    fun testLoadLabels() {
        val mainActivity = MainActivity()
        val labels = mainActivity.loadLabels("labels.txt")
        // Asumsi bahwa file labels.txt memiliki label tertentu
        assertEquals("SomeLabel", labels[0])
    }
}
