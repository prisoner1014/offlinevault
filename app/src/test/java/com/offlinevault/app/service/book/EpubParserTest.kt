package com.offlinevault.app.service.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubParserTest {

    @Test
    fun testParseGatsbyEpub() {
        val file = File("../../gatsby_test.epub")
        if (!file.exists()) {
            println("gatsby_test.epub not found at ${file.absolutePath}, skipping")
            return
        }

        val parsed = EpubParser.parseEpub(file)
        println("Book Title: ${parsed.title}")
        println("Total Chapters: ${parsed.chapters.size}")
        parsed.chapters.forEach { ch ->
            println("  Chapter ${ch.index}: ${ch.title} (${ch.wordCount} words)")
        }

        assertTrue("Should have at least 9 chapters for Gatsby, got ${parsed.chapters.size}", parsed.chapters.size >= 9)
    }
}
