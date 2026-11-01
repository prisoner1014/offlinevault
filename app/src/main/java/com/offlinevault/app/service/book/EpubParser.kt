package com.offlinevault.app.service.book

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.util.zip.ZipFile

data class BookChapter(
    val index: Int,
    val id: String,
    val title: String,
    val htmlContent: String,
    val plainText: String,
    val wordCount: Int
)

data class ParsedBook(
    val title: String,
    val author: String?,
    val coverImageBytes: ByteArray?,
    val description: String?,
    val chapters: List<BookChapter>
)

object EpubParser {

    private data class TocNavEntry(
        val title: String,
        val filePath: String,
        val anchorId: String?
    )

    fun parseEpub(file: File): ParsedBook {
        val zip = ZipFile(file)
        try {
            // 1. Locate rootfile from META-INF/container.xml
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw IllegalArgumentException("Invalid EPUB: META-INF/container.xml not found")

            val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
            val containerDoc = Jsoup.parse(containerXml, "", org.jsoup.parser.Parser.xmlParser())
            val opfPath = containerDoc.select("rootfile").attr("full-path")
            if (opfPath.isBlank()) {
                throw IllegalArgumentException("Invalid EPUB: rootfile not found in container.xml")
            }

            // 2. Parse OPF file
            val opfEntry = zip.getEntry(opfPath)
                ?: zip.getEntry(opfPath.replace('\\', '/'))
                ?: throw IllegalArgumentException("Invalid EPUB: OPF file '$opfPath' not found in archive")

            val opfDir = File(opfPath).parent?.replace('\\', '/') ?: ""
            val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
            val opfDoc = Jsoup.parse(opfXml, "", org.jsoup.parser.Parser.xmlParser())

            // Metadata
            val title = opfDoc.select("metadata > dc|title, metadata > title").firstOrNull()?.text()
                ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
            val author = opfDoc.select("metadata > dc|creator, metadata > creator").firstOrNull()?.text()
            val description = opfDoc.select("metadata > dc|description, metadata > description").firstOrNull()?.text()

            // Manifest: map id -> href
            val manifestMap = mutableMapOf<String, String>()
            val manifestMediaTypeMap = mutableMapOf<String, String>()
            var coverHref: String? = null
            var tocNavHref: String? = null
            var ncxHref: String? = null

            for (item in opfDoc.select("manifest > item")) {
                val id = item.attr("id")
                val href = item.attr("href")
                val mediaType = item.attr("media-type")
                val properties = item.attr("properties")

                manifestMap[id] = href
                manifestMediaTypeMap[id] = mediaType

                if (properties.contains("cover-image") || id.contains("cover", ignoreCase = true)) {
                    if (mediaType.startsWith("image/")) {
                        coverHref = href
                    }
                }

                if (properties.contains("nav")) {
                    tocNavHref = href
                }
                if (mediaType.contains("dtbncx", ignoreCase = true) || id.contains("ncx", ignoreCase = true)) {
                    ncxHref = href
                }
            }

            // Check meta cover if coverHref not yet found
            if (coverHref == null) {
                val metaCoverId = opfDoc.select("metadata > meta[name=cover]").attr("content")
                if (metaCoverId.isNotBlank()) {
                    coverHref = manifestMap[metaCoverId]
                }
            }

            // Read cover bytes
            var coverBytes: ByteArray? = null
            if (coverHref != null) {
                val fullCoverPath = resolveZipPath(opfDir, coverHref)
                val coverEntry = zip.getEntry(fullCoverPath)
                if (coverEntry != null) {
                    coverBytes = zip.getInputStream(coverEntry).use { it.readBytes() }
                }
            }

            // Parse Table of Contents navigation entries if present
            val tocEntries = mutableListOf<TocNavEntry>()

            // Try EPUB 3 nav (toc.xhtml)
            if (tocNavHref != null) {
                val navPath = resolveZipPath(opfDir, tocNavHref)
                val navEntry = zip.getEntry(navPath)
                if (navEntry != null) {
                    val navHtml = zip.getInputStream(navEntry).bufferedReader().use { it.readText() }
                    val navDoc = Jsoup.parse(navHtml)
                    val navLinks = navDoc.select("nav[epub:type=toc] ol li a, nav[role=doc-toc] ol li a, nav ol li a")
                    for (link in navLinks) {
                        val rawHref = link.attr("href").trim()
                        val linkTitle = link.text().trim()
                        if (rawHref.isNotBlank() && linkTitle.isNotBlank()) {
                            val parts = rawHref.split("#")
                            val relativeFile = parts[0]
                            val anchor = if (parts.size > 1) parts[1] else null
                            val resolvedFile = if (relativeFile.isNotBlank()) {
                                resolveZipPath(File(navPath).parent?.replace('\\', '/') ?: opfDir, relativeFile)
                            } else {
                                navPath
                            }
                            tocEntries.add(TocNavEntry(linkTitle, resolvedFile, anchor))
                        }
                    }
                }
            }

            // If empty, try EPUB 2 NCX (toc.ncx)
            if (tocEntries.isEmpty() && ncxHref != null) {
                val ncxPath = resolveZipPath(opfDir, ncxHref)
                val ncxEntry = zip.getEntry(ncxPath)
                if (ncxEntry != null) {
                    val ncxXml = zip.getInputStream(ncxEntry).bufferedReader().use { it.readText() }
                    val ncxDoc = Jsoup.parse(ncxXml, "", org.jsoup.parser.Parser.xmlParser())
                    val navPoints = ncxDoc.select("navMap > navPoint")
                    for (np in navPoints) {
                        val linkTitle = np.select("navLabel > text").text().trim()
                        val src = np.select("content").attr("src").trim()
                        if (src.isNotBlank() && linkTitle.isNotBlank()) {
                            val parts = src.split("#")
                            val relativeFile = parts[0]
                            val anchor = if (parts.size > 1) parts[1] else null
                            val resolvedFile = if (relativeFile.isNotBlank()) {
                                resolveZipPath(File(ncxPath).parent?.replace('\\', '/') ?: opfDir, relativeFile)
                            } else {
                                ncxPath
                            }
                            tocEntries.add(TocNavEntry(linkTitle, resolvedFile, anchor))
                        }
                    }
                }
            }

            // 3. Spine: ordered reading sequence
            val spineItemIds = opfDoc.select("spine > itemref").map { it.attr("idref") }
            val chapters = mutableListOf<BookChapter>()

            fun extractCleanText(element: Element): String {
                val blocks = mutableListOf<String>()
                fun traverse(el: Element) {
                    val tag = el.tagName().lowercase()
                    if (tag in listOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "li", "pre", "dt", "dd")) {
                        val text = el.text().trim()
                        if (text.isNotBlank()) {
                            blocks.add(text)
                        }
                    } else if (el.children().isEmpty()) {
                        val text = el.text().trim()
                        if (text.isNotBlank()) {
                            blocks.add(text)
                        }
                    } else {
                        for (child in el.children()) {
                            traverse(child)
                        }
                    }
                }
                traverse(element)

                return if (blocks.isNotEmpty()) {
                    blocks.joinToString("\n\n")
                } else {
                    val clone = element.clone()
                    clone.select("br").append("\n")
                    clone.select("p, h1, h2, h3, h4, h5, h6, blockquote, li, div, tr").prepend("\n\n")
                    clone.text().split(Regex("(\r?\n){2,}")).map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n\n")
                }
            }

            var chapterIndex = 0

            // Helper to add a parsed chapter if meaningful
            fun addChapter(id: String, chTitle: String, contentHtml: String, text: String) {
                val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
                if (text.length > 25 || words >= 5) {
                    chapters.add(
                        BookChapter(
                            index = chapterIndex++,
                            id = id,
                            title = chTitle,
                            htmlContent = contentHtml,
                            plainText = text,
                            wordCount = words
                        )
                    )
                }
            }

            // Group TOC entries by file path
            val tocByFile = tocEntries.groupBy { it.filePath }

            for (idref in spineItemIds) {
                val href = manifestMap[idref] ?: continue
                val fullPath = resolveZipPath(opfDir, href)
                val chapterEntry = zip.getEntry(fullPath) ?: continue

                val chapterHtml = zip.getInputStream(chapterEntry).bufferedReader().use { it.readText() }
                val doc = Jsoup.parse(chapterHtml)
                doc.select("script, style, link").remove()

                // Check if this spine file contains multiple TOC anchors
                val fileTocList = (tocByFile[fullPath] ?: emptyList()).filter { !it.anchorId.isNullOrBlank() }

                if (fileTocList.size >= 2) {
                    // Split the document by internal anchor elements (e.g. Gatsby's <div id="chapter-1">, etc.)
                    for (tocItem in fileTocList) {
                        val anchorEl = doc.getElementById(tocItem.anchorId!!)
                        if (anchorEl != null) {
                            val text = extractCleanText(anchorEl)
                            val title = tocItem.title.ifBlank { anchorEl.select("h1, h2, h3").firstOrNull()?.text() ?: "Chapter $chapterIndex" }
                            addChapter("${idref}_${tocItem.anchorId}", title, anchorEl.html(), text)
                        }
                    }
                } else {
                    // Check if file has internal <div id="chapter-..."> or <h2> elements
                    val internalChapters = doc.select("div[id^=chapter-], section[id^=chapter-]")
                    if (internalChapters.size >= 2) {
                        for (chEl in internalChapters) {
                            val h = chEl.select("h1, h2, h3").firstOrNull()?.text()?.trim()
                            val chTitle = if (!h.isNullOrBlank()) h else "Chapter ${chapterIndex + 1}"
                            val text = extractCleanText(chEl)
                            addChapter("${idref}_${chEl.id()}", chTitle, chEl.html(), text)
                        }
                    } else {
                        // Standard single-chapter file
                        val heading = doc.select("h1, h2, h3, title").firstOrNull()?.text()?.trim()
                        val chapterTitle = if (!heading.isNullOrBlank()) {
                            heading
                        } else {
                            "Chapter ${chapterIndex + 1}"
                        }

                        val plainText = extractCleanText(doc.body() ?: doc)
                        val bodyHtml = doc.body()?.html() ?: chapterHtml
                        addChapter(idref, chapterTitle, bodyHtml, plainText)
                    }
                }
            }

            // Fallback if no chapters parsed
            if (chapters.isEmpty()) {
                chapters.add(
                    BookChapter(
                        index = 0,
                        id = "content",
                        title = title,
                        htmlContent = "<p>Book content loaded.</p>",
                        plainText = "Book content loaded.",
                        wordCount = 3
                    )
                )
            }

            return ParsedBook(
                title = title,
                author = author,
                coverImageBytes = coverBytes,
                description = description,
                chapters = chapters
            )
        } finally {
            try { zip.close() } catch (_: Exception) {}
        }
    }

    fun parsePlainText(file: File): ParsedBook {
        val text = file.readText()
        val title = file.nameWithoutExtension.replace('_', ' ')

        fun normalizeTextParagraphs(raw: String): String {
            return raw.split(Regex("(\r?\n){2,}"))
                .map { it.replace(Regex("\r?\n"), " ").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        }

        val lines = text.lines()
        val chapterList = mutableListOf<BookChapter>()

        var currentTitle = "Chapter 1"
        var currentBuffer = StringBuilder()
        var chIdx = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("CHAPTER", ignoreCase = true) || trimmed.startsWith("BOOK", ignoreCase = true) || trimmed.startsWith("PART", ignoreCase = true)) {
                if (currentBuffer.length > 500) {
                    val raw = normalizeTextParagraphs(currentBuffer.toString())
                    chapterList.add(
                        BookChapter(
                            index = chIdx++,
                            id = "ch_$chIdx",
                            title = currentTitle,
                            htmlContent = raw.replace("\n", "<br/>"),
                            plainText = raw,
                            wordCount = raw.split(Regex("\\s+")).size
                        )
                    )
                    currentBuffer = StringBuilder()
                    currentTitle = trimmed
                }
            }
            currentBuffer.append(line).append("\n")
        }

        if (currentBuffer.isNotEmpty()) {
            val raw = normalizeTextParagraphs(currentBuffer.toString())
            chapterList.add(
                BookChapter(
                    index = chIdx,
                    id = "ch_$chIdx",
                    title = currentTitle,
                    htmlContent = raw.replace("\n", "<br/>"),
                    plainText = raw,
                    wordCount = raw.split(Regex("\\s+")).size
                )
            )
        }

        if (chapterList.isEmpty()) {
            val raw = normalizeTextParagraphs(text)
            chapterList.add(
                BookChapter(
                    index = 0,
                    id = "ch_0",
                    title = title,
                    htmlContent = raw.replace("\n", "<br/>"),
                    plainText = raw,
                    wordCount = raw.split(Regex("\\s+")).size
                )
            )
        }

        return ParsedBook(
            title = title,
            author = "Unknown",
            coverImageBytes = null,
            description = null,
            chapters = chapterList
        )
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val cleanedHref = href.replace('\\', '/')
        if (baseDir.isBlank()) return cleanedHref
        val combined = "$baseDir/$cleanedHref"
        val parts = combined.split('/')
        val resolved = mutableListOf<String>()
        for (p in parts) {
            if (p == "..") {
                if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
            } else if (p != "." && p.isNotBlank()) {
                resolved.add(p)
            }
        }
        return resolved.joinToString("/")
    }
}
