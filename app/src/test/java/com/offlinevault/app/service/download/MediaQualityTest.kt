package com.offlinevault.app.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaQualityTest {

    @Test
    fun testQualityDefinitions() {
        assertTrue(MediaQuality.AUDIO_320K.isAudioOnly)
        assertTrue(MediaQuality.AUDIO_128K.isAudioOnly)
        assertTrue(MediaQuality.AUDIO_64K.isAudioOnly)
        assertTrue(MediaQuality.AUDIO_32K.isAudioOnly)

        assertFalse(MediaQuality.BEST_VIDEO.isAudioOnly)
        assertFalse(MediaQuality.VIDEO_1080P.isAudioOnly)
        assertFalse(MediaQuality.VIDEO_720P.isAudioOnly)
        assertFalse(MediaQuality.VIDEO_480P.isAudioOnly)
        assertFalse(MediaQuality.VIDEO_360P.isAudioOnly)
        assertFalse(MediaQuality.VIDEO_240P.isAudioOnly)
        assertFalse(MediaQuality.VIDEO_144P.isAudioOnly)

        assertTrue(MediaQuality.VIDEO_360P.formatOption.contains("360"))
        assertTrue(MediaQuality.VIDEO_240P.formatOption.contains("240"))
        assertTrue(MediaQuality.VIDEO_144P.formatOption.contains("144"))

        assertEquals("64K", MediaQuality.AUDIO_64K.audioBitrate)
        assertEquals("32K", MediaQuality.AUDIO_32K.audioBitrate)
    }
}
