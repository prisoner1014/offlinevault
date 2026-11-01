package com.offlinevault.app.service.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class BookRepository(
    val id: String,
    val displayName: String,
    val badge: String,
    val description: String
) {
    ALL("all", "All Repositories", "All", "Poll from Gutenberg, Standard Ebooks & Open Library"),
    STANDARD_EBOOKS("standard_ebooks", "Standard Ebooks", "Standard Ebooks", "Curated public domain classics with modern typography"),
    GUTENBERG("gutenberg", "Project Gutenberg", "Gutenberg", "70,000+ free digital books from the oldest digital library"),
    OPEN_LIBRARY("open_library", "Open Library", "Open Library", "Millions of public domain books & Internet Archive editions")
}

data class DiscoveredBook(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val downloadUrl: String,
    val format: String = "EPUB",
    val synopsis: String? = null,
    val subjects: List<String> = emptyList(),
    val downloadCount: Int = 0,
    val repository: String = "Project Gutenberg",
    val repositoryBadge: String = "Gutenberg"
)

class BookDiscoveryService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Curated catalog covering Standard Ebooks, Project Gutenberg, and Open Library
    private val masterCatalog = listOf(
        // === STANDARD EBOOKS (Carefully edited typography editions) ===
        DiscoveredBook(
            id = "se-1984",
            title = "Nineteen Eighty-Four",
            author = "George Orwell",
            coverUrl = "https://standardebooks.org/ebooks/george-orwell/nineteen-eighty-four/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/george-orwell/nineteen-eighty-four/downloads/george-orwell_nineteen-eighty-four.epub",
            format = "EPUB",
            synopsis = "Winston Smith struggles against the totalitarian regime of Oceania and Big Brother in this iconic dystopian masterpiece.",
            subjects = listOf("Dystopia", "Sci-Fi", "Classics", "Politics"),
            downloadCount = 92000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-animal-farm",
            title = "Animal Farm",
            author = "George Orwell",
            coverUrl = "https://standardebooks.org/ebooks/george-orwell/animal-farm/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/george-orwell/animal-farm/downloads/george-orwell_animal-farm.epub",
            format = "EPUB",
            synopsis = "A biting political allegory in which farm animals rebel against their human farmer, only to succumb to pig tyranny.",
            subjects = listOf("Satire", "Allegory", "Classics", "Politics"),
            downloadCount = 85000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-great-gatsby",
            title = "The Great Gatsby",
            author = "F. Scott Fitzgerald",
            coverUrl = "https://standardebooks.org/ebooks/f-scott-fitzgerald/the-great-gatsby/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/f-scott-fitzgerald/the-great-gatsby/downloads/f-scott-fitzgerald_the-great-gatsby.epub",
            format = "EPUB",
            synopsis = "A portrait of the Jazz Age on Long Island, following Jay Gatsby's relentless obsession with Daisy Buchanan.",
            subjects = listOf("Literature", "Classics", "Jazz Age"),
            downloadCount = 78000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-frankenstein",
            title = "Frankenstein; or, The Modern Prometheus",
            author = "Mary Wollstonecraft Shelley",
            coverUrl = "https://standardebooks.org/ebooks/mary-shelley/frankenstein/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/mary-shelley/frankenstein/downloads/mary-shelley_frankenstein.epub",
            format = "EPUB",
            synopsis = "The seminal science fiction and gothic horror story of Victor Frankenstein and his tragic, sentient creation.",
            subjects = listOf("Sci-Fi", "Gothic", "Horror", "Classics"),
            downloadCount = 74000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-dracula",
            title = "Dracula",
            author = "Bram Stoker",
            coverUrl = "https://standardebooks.org/ebooks/bram-stoker/dracula/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/bram-stoker/dracula/downloads/bram-stoker_dracula.epub",
            format = "EPUB",
            synopsis = "The vampire thriller told through personal diaries, following Count Dracula's relocation to Victorian London.",
            subjects = listOf("Horror", "Gothic", "Classics"),
            downloadCount = 69000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-meditations",
            title = "Meditations",
            author = "Marcus Aurelius",
            coverUrl = "https://standardebooks.org/ebooks/marcus-aurelius/meditations/george-long/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/marcus-aurelius/meditations/george-long/downloads/marcus-aurelius_meditations_george-long.epub",
            format = "EPUB",
            synopsis = "Private philosophical reflections on Stoic philosophy, ethics, self-discipline, and duty written by the Roman Emperor.",
            subjects = listOf("Philosophy", "Stoicism", "Ethics", "Ancient History"),
            downloadCount = 65000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-beyond-good-evil",
            title = "Beyond Good and Evil",
            author = "Friedrich Nietzsche",
            coverUrl = "https://standardebooks.org/ebooks/friedrich-nietzsche/beyond-good-and-evil/helen-zimmern/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/friedrich-nietzsche/beyond-good-and-evil/helen-zimmern/downloads/friedrich-nietzsche_beyond-good-and-evil_helen-zimmern.epub",
            format = "EPUB",
            synopsis = "Nietzsche dramatically critiques traditional morality and champions the will to power and individual perspective.",
            subjects = listOf("Philosophy", "Ethics", "Psychology"),
            downloadCount = 59000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-art-of-war",
            title = "The Art of War",
            author = "Sun Tzu",
            coverUrl = "https://standardebooks.org/ebooks/sun-tzu/the-art-of-war/lionel-giles/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/sun-tzu/the-art-of-war/lionel-giles/downloads/sun-tzu_the-art-of-war_lionel-giles.epub",
            format = "EPUB",
            synopsis = "An ancient Chinese military treatise devoted to strategy, psychology, tactical positioning, and statecraft.",
            subjects = listOf("Strategy", "Philosophy", "Military", "Classics"),
            downloadCount = 58000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-war-of-worlds",
            title = "The War of the Worlds",
            author = "H. G. Wells",
            coverUrl = "https://standardebooks.org/ebooks/h-g-wells/the-war-of-the-worlds/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/h-g-wells/the-war-of-the-worlds/downloads/h-g-wells_the-war-of-the-worlds.epub",
            format = "EPUB",
            synopsis = "One of the earliest stories detailing a conflict between mankind and an extraterrestrial race attacking Earth from Mars.",
            subjects = listOf("Sci-Fi", "Aliens", "Adventure", "Classics"),
            downloadCount = 52000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),
        DiscoveredBook(
            id = "se-hound-baskervilles",
            title = "The Hound of the Baskervilles",
            author = "Arthur Conan Doyle",
            coverUrl = "https://standardebooks.org/ebooks/arthur-conan-doyle/the-hound-of-the-baskervilles/downloads/cover-thumbnail.jpg",
            downloadUrl = "https://standardebooks.org/ebooks/arthur-conan-doyle/the-hound-of-the-baskervilles/downloads/arthur-conan-doyle_the-hound-of-the-baskervilles.epub",
            format = "EPUB",
            synopsis = "Sherlock Holmes and Dr. Watson investigate the legend of a supernatural, diabolical hound on Dartmoor.",
            subjects = listOf("Mystery", "Detective", "Gothic", "Classics"),
            downloadCount = 51000,
            repository = "Standard Ebooks",
            repositoryBadge = "Standard Ebooks"
        ),

        // === PROJECT GUTENBERG (70,000+ Catalog) ===
        DiscoveredBook(
            id = "pg-1342",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            coverUrl = "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/1342.epub3.images",
            format = "EPUB",
            synopsis = "Elizabeth Bennet navigates manners, family expectations, and romance with Mr. Darcy in Regency England.",
            subjects = listOf("Classics", "Romance", "Literature"),
            downloadCount = 71000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),
        DiscoveredBook(
            id = "pg-1661",
            title = "The Adventures of Sherlock Holmes",
            author = "Arthur Conan Doyle",
            coverUrl = "https://www.gutenberg.org/cache/epub/1661/pg1661.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/1661.epub3.images",
            format = "EPUB",
            synopsis = "Twelve classic detective cases solved by Sherlock Holmes and Dr. John Watson at 221B Baker Street.",
            subjects = listOf("Mystery", "Detective", "Classics"),
            downloadCount = 48000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),
        DiscoveredBook(
            id = "pg-35",
            title = "The Time Machine",
            author = "H. G. Wells",
            coverUrl = "https://www.gutenberg.org/cache/epub/35/pg35.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/35.epub3.images",
            format = "EPUB",
            synopsis = "The pioneering science fiction tale of an English scientist who voyages forward to the year 802,701 AD.",
            subjects = listOf("Sci-Fi", "Classics", "Time Travel"),
            downloadCount = 38000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),
        DiscoveredBook(
            id = "pg-2701",
            title = "Moby Dick; Or, The Whale",
            author = "Herman Melville",
            coverUrl = "https://www.gutenberg.org/cache/epub/2701/pg2701.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/2701.epub3.images",
            format = "EPUB",
            synopsis = "Captain Ahab's obsessive vendetta against the white whale Moby Dick across stormy oceanic voyages.",
            subjects = listOf("Adventure", "Classics", "Sea Stories"),
            downloadCount = 35000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),
        DiscoveredBook(
            id = "pg-174",
            title = "The Picture of Dorian Gray",
            author = "Oscar Wilde",
            coverUrl = "https://www.gutenberg.org/cache/epub/174/pg174.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/174.epub3.images",
            format = "EPUB",
            synopsis = "A philosophical gothic story of a handsome young man whose secret portrait absorbs the consequences of his sins.",
            subjects = listOf("Philosophical", "Gothic", "Classics"),
            downloadCount = 34000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),
        DiscoveredBook(
            id = "pg-5200",
            title = "The Metamorphosis",
            author = "Franz Kafka",
            coverUrl = "https://www.gutenberg.org/cache/epub/5200/pg5200.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/5200.epub3.images",
            format = "EPUB",
            synopsis = "Gregor Samsa awakes to discover that he has been abruptly transformed into an enormous insect.",
            subjects = listOf("Psychological", "Existential", "Classics"),
            downloadCount = 32000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),
        DiscoveredBook(
            id = "pg-2600",
            title = "War and Peace",
            author = "Leo Tolstoy",
            coverUrl = "https://www.gutenberg.org/cache/epub/2600/pg2600.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/2600.epub3.images",
            format = "EPUB",
            synopsis = "The monumental Russian epic tracing the Napoleonic Wars and the intertwined lives of five aristocratic families.",
            subjects = listOf("Historical", "Epic", "Classics"),
            downloadCount = 29000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),
        DiscoveredBook(
            id = "pg-1497",
            title = "The Republic",
            author = "Plato",
            coverUrl = "https://www.gutenberg.org/cache/epub/1497/pg1497.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/1497.epub3.images",
            format = "EPUB",
            synopsis = "Socrates and his interlocutors examine the meaning of justice, the ideal city-state, and the Allegory of the Cave.",
            subjects = listOf("Philosophy", "Political Theory", "Classics"),
            downloadCount = 27000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),
        DiscoveredBook(
            id = "pg-1232",
            title = "The Prince",
            author = "Niccolò Machiavelli",
            coverUrl = "https://www.gutenberg.org/cache/epub/1232/pg1232.cover.medium.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/1232.epub3.images",
            format = "EPUB",
            synopsis = "A 16th-century political treatise on how leaders acquire, maintain, and wield power effectively.",
            subjects = listOf("Philosophy", "Politics", "History"),
            downloadCount = 26000,
            repository = "Project Gutenberg",
            repositoryBadge = "Gutenberg"
        ),

        // === OPEN LIBRARY / INTERNET ARCHIVE (Open Books) ===
        DiscoveredBook(
            id = "ol-prophet",
            title = "The Prophet",
            author = "Kahlil Gibran",
            coverUrl = "https://covers.openlibrary.org/b/id/8225266-M.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/58585.epub3.images",
            format = "EPUB",
            synopsis = "A collection of 26 poetic fables offering philosophical insight on love, marriage, children, work, and sorrow.",
            subjects = listOf("Poetry", "Philosophy", "Spiritual"),
            downloadCount = 41000,
            repository = "Open Library",
            repositoryBadge = "Open Library"
        ),
        DiscoveredBook(
            id = "ol-walden",
            title = "Walden; or, Life in the Woods",
            author = "Henry David Thoreau",
            coverUrl = "https://covers.openlibrary.org/b/id/8315124-M.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/205.epub3.images",
            format = "EPUB",
            synopsis = "Thoreau's reflection on simple living in natural surroundings at Walden Pond, emphasizing self-reliance.",
            subjects = listOf("Philosophy", "Nature", "Classics"),
            downloadCount = 37000,
            repository = "Open Library",
            repositoryBadge = "Open Library"
        ),
        DiscoveredBook(
            id = "ol-origin-species",
            title = "On the Origin of Species",
            author = "Charles Darwin",
            coverUrl = "https://covers.openlibrary.org/b/id/10287412-M.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/1228.epub3.images",
            format = "EPUB",
            synopsis = "The foundation of evolutionary biology introducing the theory that populations evolve through natural selection.",
            subjects = listOf("Science", "Biology", "History"),
            downloadCount = 33000,
            repository = "Open Library",
            repositoryBadge = "Open Library"
        ),
        DiscoveredBook(
            id = "ol-siddhartha",
            title = "Siddhartha",
            author = "Hermann Hesse",
            coverUrl = "https://covers.openlibrary.org/b/id/8254881-M.jpg",
            downloadUrl = "https://www.gutenberg.org/ebooks/2500.epub3.images",
            format = "EPUB",
            synopsis = "The spiritual journey of self-discovery of a young man named Siddhartha living during the time of Gautama Buddha.",
            subjects = listOf("Philosophy", "Spiritual", "Classics"),
            downloadCount = 31000,
            repository = "Open Library",
            repositoryBadge = "Open Library"
        )
    )

    fun getMasterCatalog(
        repository: BookRepository = BookRepository.ALL,
        category: String = "All"
    ): List<DiscoveredBook> {
        return masterCatalog.filter { book ->
            val repoMatches = when (repository) {
                BookRepository.ALL -> true
                BookRepository.STANDARD_EBOOKS -> book.repositoryBadge == "Standard Ebooks"
                BookRepository.GUTENBERG -> book.repositoryBadge == "Gutenberg"
                BookRepository.OPEN_LIBRARY -> book.repositoryBadge == "Open Library"
            }
            val catMatches = if (category == "All" || category.isBlank()) {
                true
            } else {
                book.subjects.any { it.contains(category, ignoreCase = true) } ||
                        book.title.contains(category, ignoreCase = true) ||
                        book.synopsis?.contains(category, ignoreCase = true) == true
            }
            repoMatches && catMatches
        }
    }

    suspend fun searchBooks(
        query: String,
        repository: BookRepository = BookRepository.ALL,
        category: String = "All"
    ): List<DiscoveredBook> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()

        // If no search query and no specific category, return curated filtered list
        if (trimmed.isBlank() && (category == "All" || category.isBlank())) {
            return@withContext getMasterCatalog(repository, category)
        }

        val results = mutableListOf<DiscoveredBook>()

        // 1. If searching Open Library
        if (repository == BookRepository.OPEN_LIBRARY || repository == BookRepository.ALL) {
            val olResults = searchOpenLibraryApi(trimmed.ifBlank { category })
            results.addAll(olResults)
        }

        // 2. If searching Gutenberg / Gutendex
        if (repository == BookRepository.GUTENBERG || (repository == BookRepository.ALL && results.size < 8)) {
            val gutendexResults = searchGutendexApi(trimmed.ifBlank { category })
            results.addAll(gutendexResults)
        }

        // 3. Fallback or blend with matching curated entries from Standard Ebooks / Master catalog
        val localMatches = getMasterCatalog(repository, if (trimmed.isNotBlank()) "All" else category).filter { book ->
            if (trimmed.isBlank()) true else {
                book.title.contains(trimmed, ignoreCase = true) ||
                        book.author.contains(trimmed, ignoreCase = true) ||
                        book.subjects.any { it.contains(trimmed, ignoreCase = true) }
            }
        }

        // Deduplicate by ID and downloadUrl
        val combined = (localMatches + results).distinctBy { it.downloadUrl }

        if (combined.isEmpty()) {
            getMasterCatalog(repository, category)
        } else {
            combined
        }
    }

    private fun searchGutendexApi(query: String): List<DiscoveredBook> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://gutendex.com/books/?search=$encodedQuery"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val resultsArray = json.optJSONArray("results") ?: return emptyList()

                val books = mutableListOf<DiscoveredBook>()
                for (i in 0 until minOf(resultsArray.length(), 15)) {
                    val item = resultsArray.getJSONObject(i)
                    val id = item.optInt("id").toString()
                    val title = item.optString("title", "Untitled")

                    val authorsArray = item.optJSONArray("authors")
                    val authorName = if (authorsArray != null && authorsArray.length() > 0) {
                        val firstAuthor = authorsArray.getJSONObject(0)
                        val raw = firstAuthor.optString("name", "Unknown")
                        if (raw.contains(",")) {
                            val parts = raw.split(",").map { it.trim() }
                            if (parts.size >= 2) "${parts[1]} ${parts[0]}" else raw
                        } else raw
                    } else "Unknown"

                    val formatsObj = item.optJSONObject("formats")
                    var epubUrl: String? = null
                    var coverUrl: String? = null

                    if (formatsObj != null) {
                        coverUrl = formatsObj.optString("image/jpeg").takeIf { it.isNotBlank() }
                        epubUrl = formatsObj.optString("application/epub+zip").takeIf { it.isNotBlank() }
                            ?: formatsObj.optString("application/x-mobipocket-ebook").takeIf { it.isNotBlank() }
                    }

                    if (epubUrl.isNullOrBlank()) {
                        epubUrl = "https://www.gutenberg.org/ebooks/$id.epub3.images"
                    }
                    if (coverUrl.isNullOrBlank()) {
                        coverUrl = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg"
                    }

                    val subjectsList = mutableListOf<String>()
                    val subjectsArr = item.optJSONArray("subjects")
                    if (subjectsArr != null) {
                        for (s in 0 until minOf(subjectsArr.length(), 3)) {
                            subjectsList.add(subjectsArr.optString(s).take(30))
                        }
                    }

                    books.add(
                        DiscoveredBook(
                            id = "pg-$id",
                            title = title,
                            author = authorName,
                            coverUrl = coverUrl,
                            downloadUrl = epubUrl,
                            format = "EPUB",
                            synopsis = subjectsList.joinToString(" • "),
                            subjects = subjectsList,
                            downloadCount = item.optInt("download_count", 0),
                            repository = "Project Gutenberg",
                            repositoryBadge = "Gutenberg"
                        )
                    )
                }
                return books
            }
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun searchOpenLibraryApi(query: String): List<DiscoveredBook> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://openlibrary.org/search.json?q=$encodedQuery&has_fulltext=true&limit=10"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val docs = json.optJSONArray("docs") ?: return emptyList()

                val books = mutableListOf<DiscoveredBook>()
                for (i in 0 until minOf(docs.length(), 10)) {
                    val doc = docs.getJSONObject(i)
                    val title = doc.optString("title", "Untitled")

                    val authors = doc.optJSONArray("author_name")
                    val authorName = if (authors != null && authors.length() > 0) {
                        authors.optString(0)
                    } else "Unknown"

                    val coverId = doc.optInt("cover_i", 0)
                    val coverUrl = if (coverId > 0) {
                        "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
                    } else null

                    val iaArray = doc.optJSONArray("ia")
                    val iaId = if (iaArray != null && iaArray.length() > 0) {
                        iaArray.optString(0)
                    } else null

                    // If Internet archive item exists, form direct public download link
                    val downloadUrl = if (!iaId.isNullOrBlank()) {
                        "https://archive.org/download/$iaId/$iaId.epub"
                    } else {
                        val key = doc.optString("key", "")
                        "https://openlibrary.org$key"
                    }

                    val subjectsList = mutableListOf<String>()
                    val subjectsArr = doc.optJSONArray("subject")
                    if (subjectsArr != null) {
                        for (s in 0 until minOf(subjectsArr.length(), 3)) {
                            subjectsList.add(subjectsArr.optString(s).take(28))
                        }
                    }

                    books.add(
                        DiscoveredBook(
                            id = "ol-${doc.optString("key", i.toString()).replace("/", "_")}",
                            title = title,
                            author = authorName,
                            coverUrl = coverUrl,
                            downloadUrl = downloadUrl,
                            format = "EPUB",
                            synopsis = subjectsList.joinToString(" • "),
                            subjects = subjectsList,
                            downloadCount = doc.optInt("readinglog_count", 0),
                            repository = "Open Library",
                            repositoryBadge = "Open Library"
                        )
                    )
                }
                return books
            }
        } catch (_: Exception) {
            return emptyList()
        }
    }
}
