package com.offlinevault.app.data.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class VaultBackupTest {

    @Test
    fun testManifestSerializationAndDeserialization() {
        val manifest = VaultBackupManifest(
            formatVersion = 1,
            appVersionName = "1.4.0",
            appVersionCode = 10,
            createdAtEpochMs = 1730462000000L,
            createdAtFormatted = "2026-11-01 21:15",
            sourceVaultPath = "/storage/emulated/0/Android/data/com.offlinevault.app/files",
            includesDatabase = true,
            includesSettings = true,
            mediaCategories = listOf("BOOKS", "WEB_ARCHIVES"),
            entityCounts = mapOf("books" to 5, "archives" to 12, "feeds" to 3),
            totalSizeBytes = 1048576L
        )

        val json = manifest.toJson()
        assertTrue(json.contains("\"appVersionName\": \"1.4.0\""))
        assertTrue(json.contains("\"BOOKS\""))

        val restored = VaultBackupManifest.fromJson(json)
        assertEquals(manifest.formatVersion, restored.formatVersion)
        assertEquals(manifest.appVersionName, restored.appVersionName)
        assertEquals(manifest.appVersionCode, restored.appVersionCode)
        assertEquals(manifest.createdAtEpochMs, restored.createdAtEpochMs)
        assertEquals(manifest.sourceVaultPath, restored.sourceVaultPath)
        assertEquals(manifest.includesDatabase, restored.includesDatabase)
        assertEquals(manifest.includesSettings, restored.includesSettings)
        assertEquals(manifest.mediaCategories, restored.mediaCategories)
        assertEquals(5, restored.entityCounts["books"])
        assertEquals(12, restored.entityCounts["archives"])
        assertEquals(3, restored.entityCounts["feeds"])
        assertEquals(1048576L, restored.totalSizeBytes)
    }

    @Test
    fun testZipArchiveIntegrity() {
        // Create an in-memory zip containing manifest.json and mock data
        val out = ByteArrayOutputStream()
        val zos = ZipOutputStream(out)

        val manifest = VaultBackupManifest(
            formatVersion = 1,
            appVersionName = "1.4.0",
            appVersionCode = 10,
            createdAtEpochMs = System.currentTimeMillis(),
            createdAtFormatted = "2026-11-01",
            sourceVaultPath = "/test/vault",
            includesDatabase = true,
            includesSettings = true,
            mediaCategories = listOf("BOOKS")
        )

        // Write manifest
        val manifestEntry = ZipEntry("manifest.json")
        zos.putNextEntry(manifestEntry)
        zos.write(manifest.toJson().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // Write sample book
        val bookEntry = ZipEntry("vault/books/sample_book.epub")
        zos.putNextEntry(bookEntry)
        zos.write("MOCK_EPUB_CONTENT_FOR_BACKUP".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.close()

        val zipBytes = out.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        // Read and verify zip structure
        val zis = ZipInputStream(ByteArrayInputStream(zipBytes))
        var foundManifest = false
        var foundBook = false
        var bookContent = ""

        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "manifest.json") {
                foundManifest = true
                val parsed = VaultBackupManifest.fromJson(zis.bufferedReader(Charsets.UTF_8).readText())
                assertEquals("1.4.0", parsed.appVersionName)
            } else if (entry.name == "vault/books/sample_book.epub") {
                foundBook = true
                bookContent = zis.bufferedReader(Charsets.UTF_8).readText()
            }
            entry = zis.nextEntry
        }

        assertTrue("Manifest should be present in backup zip", foundManifest)
        assertTrue("Book entry should be present in backup zip", foundBook)
        assertEquals("MOCK_EPUB_CONTENT_FOR_BACKUP", bookContent)
    }
}
