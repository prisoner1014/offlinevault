package com.offlinevault.app.service.crawler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteCrawlerTest {

    @Test
    fun testImageQualityOptionDescriptions() {
        assertEquals("No Images (Maximum Data & Space Saver)", ImageQualityOption.NO_IMAGES.label)
        assertEquals("Low Quality (Compressed 30% WebP)", ImageQualityOption.LOW_QUALITY.label)
        assertEquals("Full Quality Images", ImageQualityOption.FULL_QUALITY.label)

        assertTrue(ImageQualityOption.NO_IMAGES.description.contains("near-zero bandwidth"))
        assertTrue(ImageQualityOption.LOW_QUALITY.description.contains("30% quality"))
    }

    @Test
    fun testCrawlConfigDefaults() {
        val config = CrawlConfig(startUrl = "https://example.com")
        assertEquals(1, config.maxDepth)
        assertEquals(20, config.maxPages)
        assertTrue(config.sameDomainOnly)
        assertEquals(ImageQualityOption.LOW_QUALITY, config.imageOption)
    }

    @Test
    fun testCustomCrawlConfig() {
        val config = CrawlConfig(
            startUrl = "https://docs.python.org",
            maxDepth = 2,
            maxPages = 50,
            sameDomainOnly = true,
            imageOption = ImageQualityOption.NO_IMAGES
        )
        assertEquals(2, config.maxDepth)
        assertEquals(50, config.maxPages)
        assertEquals(ImageQualityOption.NO_IMAGES, config.imageOption)
    }
}
