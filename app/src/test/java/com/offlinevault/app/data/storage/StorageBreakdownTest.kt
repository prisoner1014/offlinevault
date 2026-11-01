package com.offlinevault.app.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageBreakdownTest {

    @Test
    fun testFormatBytesFormatting() {
        assertEquals("500 B", StorageBreakdown.formatBytes(500L))
        assertEquals("1.5 KB", StorageBreakdown.formatBytes(1536L))
        assertEquals("2.5 MB", StorageBreakdown.formatBytes(2621440L))
        assertEquals("1.50 GB", StorageBreakdown.formatBytes(1610612736L))
    }

    @Test
    fun testStorageBreakdownCalculations() {
        val breakdown = StorageBreakdown(
            videoBytes = 104857600L,     // 100 MB
            podcastBytes = 52428800L,    // 50 MB
            webArchiveBytes = 10485760L, // 10 MB
            cacheBytes = 5242880L,       // 5 MB
            totalAppUsedBytes = 173015040L, // 165 MB
            freeDeviceBytes = 21474836480L, // 20 GB
            totalDeviceBytes = 107374182400L // 100 GB
        )

        assertEquals(100.0, breakdown.videoMb, 0.01)
        assertEquals(50.0, breakdown.podcastMb, 0.01)
        assertEquals(10.0, breakdown.webArchiveMb, 0.01)
        assertEquals(5.0, breakdown.cacheMb, 0.01)
        assertEquals(165.0, breakdown.totalAppUsedMb, 0.01)
        assertEquals(20.0, breakdown.freeDeviceGb, 0.01)
        assertEquals(100.0, breakdown.totalDeviceGb, 0.01)

        assertTrue(breakdown.appUsagePercentOfDevice > 0f)
        assertTrue(breakdown.appUsagePercentOfDevice < 1f)
    }

    @Test
    fun testStorageCategoryDirectories() {
        assertEquals("vault/videos", StorageCategory.VIDEOS.subDirectory)
        assertEquals("vault/podcasts", StorageCategory.PODCASTS.subDirectory)
        assertEquals("vault/web_archives", StorageCategory.WEB_ARCHIVES.subDirectory)
        assertEquals("vault/cache", StorageCategory.TEMP_CACHE.subDirectory)
    }
}
