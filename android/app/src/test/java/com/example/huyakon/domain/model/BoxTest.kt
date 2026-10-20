package com.example.huyakon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxTest {
    @Test
    fun scaledMultipliesEachEdgeByItsAxisFactor() {
        val scaled = Box(left = 10f, top = 20f, right = 30f, bottom = 60f).scaled(2f, 0.5f)
        assertEquals(Box(left = 20f, top = 10f, right = 60f, bottom = 30f), scaled)
    }

    @Test
    fun widthAndHeightComeFromTheEdges() {
        val box = Box(10f, 20f, 40f, 90f)
        assertEquals(30f, box.width, 0.001f)
        assertEquals(70f, box.height, 0.001f)
    }
}