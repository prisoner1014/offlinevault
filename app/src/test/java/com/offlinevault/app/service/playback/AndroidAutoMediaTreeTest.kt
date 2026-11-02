package com.offlinevault.app.service.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoMediaTreeTest {

    @Test
    fun testRootAndCategoryConstants() {
        assertEquals("root", VaultMediaLibraryService.ROOT_ID)
        assertEquals("cat_drive_shuffle", VaultMediaLibraryService.CAT_DRIVE_SHUFFLE)
        assertEquals("cat_ai_briefings", VaultMediaLibraryService.CAT_AI_BRIEFINGS)
        assertEquals("cat_podcasts", VaultMediaLibraryService.CAT_PODCASTS)
        assertEquals("cat_videos", VaultMediaLibraryService.CAT_VIDEOS)
        assertEquals("cat_books", VaultMediaLibraryService.CAT_BOOKS)
    }

    @Test
    fun testPrefixIdentifiers() {
        val testPodcastMediaId = "pod_123"
        val testVideoMediaId = "vid_456"
        val testBookMediaId = "book_789"
        val testBriefingMediaId = "briefing_tech"

        assertTrue(testPodcastMediaId.startsWith(VaultMediaLibraryService.PREFIX_PODCAST))
        assertEquals(123L, testPodcastMediaId.removePrefix(VaultMediaLibraryService.PREFIX_PODCAST).toLong())

        assertTrue(testVideoMediaId.startsWith(VaultMediaLibraryService.PREFIX_VIDEO))
        assertEquals(456L, testVideoMediaId.removePrefix(VaultMediaLibraryService.PREFIX_VIDEO).toLong())

        assertTrue(testBookMediaId.startsWith(VaultMediaLibraryService.PREFIX_BOOK))
        assertEquals(789L, testBookMediaId.removePrefix(VaultMediaLibraryService.PREFIX_BOOK).toLong())

        assertTrue(testBriefingMediaId.startsWith(VaultMediaLibraryService.PREFIX_BRIEFING))
        assertEquals("tech", testBriefingMediaId.removePrefix(VaultMediaLibraryService.PREFIX_BRIEFING))
    }

    @Test
    fun testCustomCommands() {
        assertEquals("com.offlinevault.app.action.REWIND_10", VaultMediaLibraryService.ACTION_REWIND_10)
        assertEquals("com.offlinevault.app.action.FORWARD_30", VaultMediaLibraryService.ACTION_FORWARD_30)
        assertEquals("com.offlinevault.app.action.CYCLE_SPEED", VaultMediaLibraryService.ACTION_CYCLE_SPEED)
    }
}
