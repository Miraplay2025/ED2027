package com.example

import com.example.data.model.VideoBitratePreset
import com.example.data.model.VideoFps
import com.example.data.model.VideoOutputConfig
import com.example.data.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOutputSettingsTest {

    @Test
    fun testExactFourResolutionsAvailable() {
        val resolutions = VideoResolution.ALL
        assertEquals("Devem existir exatamente 4 resoluções de vídeo", 4, resolutions.size)

        val res240p = resolutions.find { it == VideoResolution.RES_240P }
        assertNotNull(res240p)
        assertEquals("240p (320x240)", res240p?.label)
        assertEquals(320, res240p?.width)
        assertEquals(240, res240p?.height)

        val res360p = resolutions.find { it == VideoResolution.RES_360P }
        assertNotNull(res360p)
        assertEquals("360p / 340p (640x360)", res360p?.label)
        assertEquals(640, res360p?.width)
        assertEquals(360, res360p?.height)

        val res480p = resolutions.find { it == VideoResolution.RES_480P }
        assertNotNull(res480p)
        assertEquals("480p (854x480)", res480p?.label)
        assertEquals(854, res480p?.width)
        assertEquals(480, res480p?.height)

        val res720p = resolutions.find { it == VideoResolution.RES_720P }
        assertNotNull(res720p)
        assertEquals("720p (1280x720) [HD]", res720p?.label)
        assertEquals(1280, res720p?.width)
        assertEquals(720, res720p?.height)
    }

    @Test
    fun testFpsOptions() {
        val fpsList = VideoFps.ALL
        assertEquals(3, fpsList.size)

        assertTrue(fpsList.any { it.fps == 24 && it.label == "24 FPS" })
        assertTrue(fpsList.any { it.fps == 30 && it.label == "30 FPS" })
        assertTrue(fpsList.any { it.fps == 60 && it.label == "60 FPS" })
    }

    @Test
    fun testBitratePresets() {
        val presets = VideoBitratePreset.ALL
        assertEquals(4, presets.size)

        val p1 = presets.find { it == VideoBitratePreset.MBPS_1 }
        assertNotNull(p1)
        assertEquals(1.0f, p1?.mbps ?: 0f, 0.01f)
        assertEquals(1_000_000, p1?.bps)

        val p25 = presets.find { it == VideoBitratePreset.MBPS_2_5 }
        assertNotNull(p25)
        assertEquals(2.5f, p25?.mbps ?: 0f, 0.01f)
        assertEquals(2_500_000, p25?.bps)

        val p5 = presets.find { it == VideoBitratePreset.MBPS_5 }
        assertNotNull(p5)
        assertEquals(5.0f, p5?.mbps ?: 0f, 0.01f)
        assertEquals(5_000_000, p5?.bps)

        val p8 = presets.find { it == VideoBitratePreset.MBPS_8 }
        assertNotNull(p8)
        assertEquals(8.0f, p8?.mbps ?: 0f, 0.01f)
        assertEquals(8_000_000, p8?.bps)
    }

    @Test
    fun testVideoOutputConfigConsolidation() {
        val config = VideoOutputConfig(
            resolution = VideoResolution.RES_720P,
            fps = 60,
            bitrateMbps = 8.0f
        )
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
        assertEquals(60, config.fps)
        assertEquals(8_000_000, config.bitrateBps)
        assertEquals("720p (1280x720) [HD] (16:9)", config.resolutionLabel)
    }
}
