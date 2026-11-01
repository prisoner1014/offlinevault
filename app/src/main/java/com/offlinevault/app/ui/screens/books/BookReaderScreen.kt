package com.offlinevault.app.ui.screens.books

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.BookEntity
import com.offlinevault.app.data.local.entity.BookmarkEntity
import com.offlinevault.app.service.ai.AiModelManager
import com.offlinevault.app.service.ai.OfflineAiEngine
import com.offlinevault.app.service.book.BookChapter
import com.offlinevault.app.service.book.BookTtsManager
import com.offlinevault.app.service.book.EpubParser
import com.offlinevault.app.service.book.ParsedBook
import com.offlinevault.app.service.book.TtsVoiceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ReaderTheme(
    val displayName: String,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val textColor: Color,
    val accentColor: Color
) {
    AMOLED_DARK(
        displayName = "AMOLED",
        backgroundColor = Color(0xFF090D16),
        surfaceColor = Color(0xFF131A29),
        textColor = Color(0xFFE2E8F0),
        accentColor = Color(0xFF38BDF8)
    ),
    SEPIA(
        displayName = "Sepia",
        backgroundColor = Color(0xFFFBF0D9),
        surfaceColor = Color(0xFFF4E3C1),
        textColor = Color(0xFF2D241E),
        accentColor = Color(0xFF8C5A28)
    ),
    WHITE(
        displayName = "Light",
        backgroundColor = Color(0xFFFFFFFF),
        surfaceColor = Color(0xFFF1F5F9),
        textColor = Color(0xFF0F172A),
        accentColor = Color(0xFF0284C7)
    ),
    MIDNIGHT(
        displayName = "Midnight",
        backgroundColor = Color(0xFF0F172A),
        surfaceColor = Color(0xFF1E293B),
        textColor = Color(0xFFCBD5E1),
        accentColor = Color(0xFF818CF8)
    )
}

enum class ReaderFont(val displayName: String, val fontFamily: FontFamily) {
    SERIF("Serif", FontFamily.Serif),
    SANS_SERIF("Sans", FontFamily.SansSerif),
    MONOSPACE("Mono", FontFamily.Monospace)
}

enum class ReadingLayout(val displayName: String) {
    PAGED("Paged"),
    SCROLL("Scroll")
}

data class ParaWithIndex(
    val index: Int,
    val text: String
)

data class BookSearchResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val paragraphIndex: Int,
    val snippet: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(
    bookId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }

    var bookEntity by remember { mutableStateOf<BookEntity?>(null) }
    var parsedBook by remember { mutableStateOf<ParsedBook?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isPdf = bookEntity?.format?.equals("pdf", ignoreCase = true) == true

    // Reading Settings State
    var readerTheme by remember { mutableStateOf(ReaderTheme.AMOLED_DARK) }
    var selectedFont by remember { mutableStateOf(ReaderFont.SERIF) }
    var fontSizeSp by remember { mutableFloatStateOf(20f) }
    var lineSpacingMultiplier by remember { mutableFloatStateOf(1.55f) }
    var readingLayout by remember { mutableStateOf(ReadingLayout.PAGED) }
    var twoPageSpreadOnFold by remember { mutableStateOf(false) } // Default to Full Width for max screen use, toggleable to 2-page

    // Controls Visibility & Dialogs
    var showControls by remember { mutableStateOf(true) }
    var showTocDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showVoiceSettingsDialog by remember { mutableStateOf(false) }
    var showBookAiDialog by remember { mutableStateOf(false) }
    var tocSelectedTab by remember { mutableIntStateOf(0) } // 0 = Chapters, 1 = Bookmarks

    // Text-to-Speech (TTS) Engine
    val ttsManager = remember { BookTtsManager(context) }
    var showTtsControls by remember { mutableStateOf(false) }
    var activeTtsParagraphIndex by remember { mutableIntStateOf(-1) }

    // Intercept hardware back button & system edge back gestures
    BackHandler(enabled = true) {
        when {
            showSearchDialog -> showSearchDialog = false
            showAppearanceDialog -> showAppearanceDialog = false
            showTocDialog -> showTocDialog = false
            showVoiceSettingsDialog -> showVoiceSettingsDialog = false
            showBookAiDialog -> showBookAiDialog = false
            showTtsControls -> {
                showTtsControls = false
                ttsManager.pause()
            }
            else -> onBack()
        }
    }

    // Offline AI Engine for In-Book Companion
    val aiModelManager = remember { AiModelManager(context) }
    val aiEngine = remember { OfflineAiEngine(context, aiModelManager) }

    // Bookmarks Flow
    val bookmarks by db.bookDao().getBookmarksForBook(bookId).collectAsState(initial = emptyList())

    // Navigation State
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var currentPageInChapter by remember { mutableIntStateOf(0) }

    // PDF state
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pdfTotalPages by remember { mutableIntStateOf(0) }
    var currentPdfPage by remember { mutableIntStateOf(0) }
    var pdfPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    // Load Book on Launch
    LaunchedEffect(bookId) {
        withContext(Dispatchers.IO) {
            try {
                val entity = db.bookDao().getBookById(bookId)
                if (entity == null || entity.localFilePath == null) {
                    errorMessage = "Book file not found"
                    isLoading = false
                    return@withContext
                }

                val file = File(entity.localFilePath)
                if (!file.exists()) {
                    errorMessage = "File does not exist on disk"
                    isLoading = false
                    return@withContext
                }

                bookEntity = entity
                currentChapterIndex = entity.currentChapterIndex.coerceAtLeast(0)

                if (entity.format.equals("pdf", ignoreCase = true)) {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfDescriptor = pfd
                    val renderer = PdfRenderer(pfd)
                    pdfRenderer = renderer
                    pdfTotalPages = renderer.pageCount
                    currentPdfPage = entity.currentChapterIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                } else if (entity.format.equals("txt", ignoreCase = true)) {
                    val parsed = EpubParser.parsePlainText(file)
                    parsedBook = parsed
                    currentChapterIndex = entity.currentChapterIndex.coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0))
                    if (entity.totalChapters != parsed.chapters.size) {
                        val updated = entity.copy(totalChapters = parsed.chapters.size)
                        db.bookDao().updateBook(updated)
                        bookEntity = updated
                    }
                } else {
                    val parsed = EpubParser.parseEpub(file)
                    parsedBook = parsed
                    currentChapterIndex = entity.currentChapterIndex.coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0))
                    if (entity.totalChapters != parsed.chapters.size) {
                        val updated = entity.copy(totalChapters = parsed.chapters.size)
                        db.bookDao().updateBook(updated)
                        bookEntity = updated
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Failed to open book: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    // Render PDF page when page changes
    LaunchedEffect(currentPdfPage, pdfRenderer) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (pdfTotalPages <= 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val pageIndex = currentPdfPage.coerceIn(0, pdfTotalPages - 1)
                val page = renderer.openPage(pageIndex)
                val width = page.width * 2
                val height = page.height * 2
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pdfPageBitmap = bmp

                // Save progress
                val progress = (pageIndex + 1).toFloat() / pdfTotalPages.toFloat()
                db.bookDao().updateReadingProgress(
                    id = bookId,
                    progress = progress,
                    chapterIndex = pageIndex,
                    scrollOffset = 0,
                    lastRead = System.currentTimeMillis()
                )
            } catch (_: Exception) {}
        }
    }

    // Save reading progress when chapter changes
    fun onChapterSelected(newIndex: Int) {
        val total = parsedBook?.chapters?.size ?: 1
        currentChapterIndex = newIndex.coerceIn(0, total - 1)
        currentPageInChapter = 0
        val progress = (currentChapterIndex + 1).toFloat() / total.toFloat()
        scope.launch(Dispatchers.IO) {
            db.bookDao().updateReadingProgress(
                id = bookId,
                progress = progress,
                chapterIndex = currentChapterIndex,
                scrollOffset = 0,
                lastRead = System.currentTimeMillis()
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { pdfDescriptor?.close() } catch (_: Exception) {}
            try { ttsManager.shutdown() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerTheme.backgroundColor)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = readerTheme.accentColor)
            }
            return
        }

        if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(errorMessage!!, color = readerTheme.textColor, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
            }
            return
        }

        val book = bookEntity ?: return

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isFoldUnfolded = maxWidth >= 600.dp
            val screenWidth = maxWidth

            val statusBarsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val contentTopPadding = if (showControls) statusBarsTop + 64.dp else statusBarsTop + 16.dp
            val contentBottomPadding = if (showControls) navBarsBottom + 84.dp else navBarsBottom + 20.dp

            if (isPdf) {
                // PDF Viewer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showControls = !showControls
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = pdfPageBitmap
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Page ${currentPdfPage + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = contentTopPadding,
                                    bottom = contentBottomPadding
                                )
                        )
                    } else {
                        CircularProgressIndicator(color = readerTheme.accentColor)
                    }
                }
            } else {
                // EPUB / TXT Chapter Reader
                val chapters = parsedBook?.chapters ?: emptyList()
                val currentChapter = chapters.getOrNull(currentChapterIndex)

                if (currentChapter != null) {
                    // Split chapter text into clean indexed paragraphs
                    val paragraphsWithIndex = remember(currentChapter.plainText) {
                        currentChapter.plainText.split("\n\n")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .mapIndexed { idx, t -> ParaWithIndex(idx, t) }
                    }
                    val paragraphs = remember(paragraphsWithIndex) {
                        paragraphsWithIndex.map { it.text }
                    }

                    // Dynamically calculate words per page based on font size and fold layout
                    val targetWordsPerPage = remember(isFoldUnfolded, twoPageSpreadOnFold, fontSizeSp) {
                        val baseWords = if (isFoldUnfolded && !twoPageSpreadOnFold) 340f else if (isFoldUnfolded) 180f else 170f
                        ((baseWords * (20f / fontSizeSp))).toInt().coerceIn(35, 550)
                    }

                    // Paginate paragraphs into distinct book pages
                    val pages = remember(paragraphsWithIndex, targetWordsPerPage) {
                        val result = mutableListOf<List<ParaWithIndex>>()
                        var curPage = mutableListOf<ParaWithIndex>()
                        var curWords = 0

                        for (para in paragraphsWithIndex) {
                            val words = para.text.split(Regex("\\s+")).count { it.isNotBlank() }
                            if (curWords > 0 && (curWords + words > targetWordsPerPage)) {
                                result.add(curPage)
                                curPage = mutableListOf(para)
                                curWords = words
                            } else {
                                curPage.add(para)
                                curWords += words
                            }
                        }
                        if (curPage.isNotEmpty()) {
                            result.add(curPage)
                        }
                        if (result.isEmpty()) listOf(listOf(ParaWithIndex(0, "End of chapter."))) else result
                    }

                    val paraToPageIndex = remember(pages) {
                        val map = mutableMapOf<Int, Int>()
                        pages.forEachIndexed { pIdx, pList ->
                            pList.forEach { p -> map[p.index] = pIdx }
                        }
                        map
                    }

                    // Sync TTS paragraph updates to page flips & chapter progression
                    LaunchedEffect(paragraphs, currentChapterIndex) {
                        ttsManager.onParagraphChanged = { pIdx ->
                            activeTtsParagraphIndex = pIdx
                            paraToPageIndex[pIdx]?.let { targetPage ->
                                if (currentPageInChapter != targetPage) {
                                    currentPageInChapter = targetPage
                                }
                            }
                        }
                        ttsManager.onChapterCompleted = {
                            if (currentChapterIndex < chapters.size - 1) {
                                onChapterSelected(currentChapterIndex + 1)
                            } else {
                                showTtsControls = false
                            }
                        }
                    }

                    val totalPages = pages.size.coerceAtLeast(1)
                    val activePage = currentPageInChapter.coerceIn(0, totalPages - 1)

                    fun goToPrevPage() {
                        if (isFoldUnfolded && twoPageSpreadOnFold) {
                            if (activePage >= 2) {
                                currentPageInChapter = activePage - 2
                            } else if (currentChapterIndex > 0) {
                                onChapterSelected(currentChapterIndex - 1)
                            }
                        } else {
                            if (activePage > 0) {
                                currentPageInChapter = activePage - 1
                            } else if (currentChapterIndex > 0) {
                                onChapterSelected(currentChapterIndex - 1)
                            }
                        }
                    }

                    fun goToNextPage() {
                        if (isFoldUnfolded && twoPageSpreadOnFold) {
                            if (activePage + 2 < totalPages) {
                                currentPageInChapter = activePage + 2
                            } else if (currentChapterIndex < chapters.size - 1) {
                                onChapterSelected(currentChapterIndex + 1)
                            }
                        } else {
                            if (activePage < totalPages - 1) {
                                currentPageInChapter = activePage + 1
                            } else if (currentChapterIndex < chapters.size - 1) {
                                onChapterSelected(currentChapterIndex + 1)
                            }
                        }
                    }

                    if (readingLayout == ReadingLayout.PAGED) {
                        // PAGED BOOK READER MODE (Clean book pages with tap-to-turn zones)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = contentTopPadding,
                                    bottom = contentBottomPadding
                                )
                        ) {
                            // Reading Page Content
                            if (isFoldUnfolded && twoPageSpreadOnFold) {
                                // Two-Page Book Spread on Galaxy Fold
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp)
                                ) {
                                    // Left Book Page
                                    val leftPageParas = pages.getOrElse(activePage) { emptyList() }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(end = 16.dp)
                                    ) {
                                        Text(
                                            text = currentChapter.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = selectedFont.fontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (fontSizeSp * 1.05f).sp
                                            ),
                                            color = readerTheme.accentColor,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        leftPageParas.forEach { para ->
                                            val isTtsActive = showTtsControls && activeTtsParagraphIndex == para.index
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isTtsActive) readerTheme.accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                                    .padding(horizontal = if (isTtsActive) 6.dp else 0.dp, vertical = if (isTtsActive) 2.dp else 0.dp)
                                                    .padding(bottom = 12.dp)
                                            ) {
                                                Text(
                                                    text = para.text,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontFamily = selectedFont.fontFamily,
                                                        fontSize = fontSizeSp.sp,
                                                        lineHeight = (fontSizeSp * lineSpacingMultiplier).sp
                                                    ),
                                                    color = if (isTtsActive) readerTheme.accentColor else readerTheme.textColor
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "Page ${activePage + 1} of $totalPages",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = readerTheme.textColor.copy(alpha = 0.5f)
                                        )
                                    }

                                    // Crease Line (aligns with Fold hinge)
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(readerTheme.textColor.copy(alpha = 0.12f))
                                    )

                                    // Right Book Page
                                    val rightPageParas = pages.getOrElse(activePage + 1) { emptyList() }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(start = 16.dp)
                                    ) {
                                        if (rightPageParas.isNotEmpty()) {
                                            rightPageParas.forEach { para ->
                                                val isTtsActive = showTtsControls && activeTtsParagraphIndex == para.index
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isTtsActive) readerTheme.accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                                        .padding(horizontal = if (isTtsActive) 6.dp else 0.dp, vertical = if (isTtsActive) 2.dp else 0.dp)
                                                        .padding(bottom = 12.dp)
                                                ) {
                                                    Text(
                                                        text = para.text,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontFamily = selectedFont.fontFamily,
                                                            fontSize = fontSizeSp.sp,
                                                            lineHeight = (fontSizeSp * lineSpacingMultiplier).sp
                                                        ),
                                                        color = if (isTtsActive) readerTheme.accentColor else readerTheme.textColor
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = "Page ${activePage + 2} of $totalPages",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = readerTheme.textColor.copy(alpha = 0.5f),
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = "End of chapter",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = readerTheme.textColor.copy(alpha = 0.4f),
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Single Page / Full Width on Unfolded Fold & Cover Screen
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = if (isFoldUnfolded) 36.dp else 20.dp)
                                ) {
                                    Text(
                                        text = currentChapter.title,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = selectedFont.fontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (fontSizeSp * 1.15f).sp
                                        ),
                                        color = readerTheme.accentColor,
                                        modifier = Modifier.padding(bottom = 14.dp)
                                    )

                                    val pageParas = pages.getOrElse(activePage) { emptyList() }
                                    pageParas.forEach { para ->
                                        val isTtsActive = showTtsControls && activeTtsParagraphIndex == para.index
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isTtsActive) readerTheme.accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                                .padding(horizontal = if (isTtsActive) 8.dp else 0.dp, vertical = if (isTtsActive) 4.dp else 0.dp)
                                                .padding(bottom = 12.dp)
                                        ) {
                                            Text(
                                                text = para.text,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = selectedFont.fontFamily,
                                                    fontSize = fontSizeSp.sp,
                                                    lineHeight = (fontSizeSp * lineSpacingMultiplier).sp
                                                ),
                                                color = if (isTtsActive) readerTheme.accentColor else readerTheme.textColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentChapter.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = readerTheme.textColor.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = "Page ${activePage + 1} of $totalPages",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = readerTheme.accentColor
                                        )
                                    }
                                }
                            }

                            // Tap Zone Overlays:
                            // Left 22% -> Prev Page, Right 22% -> Next Page, Center 56% -> Toggle Bars
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.22f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { goToPrevPage() }
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.56f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { showControls = !showControls }
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.22f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { goToNextPage() }
                                )
                            }
                        }
                    } else {
                        // SCROLL MODE (Continuous vertical scroll with clean paragraph blocks)
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = contentTopPadding,
                                    bottom = contentBottomPadding,
                                    start = if (isFoldUnfolded) 36.dp else 20.dp,
                                    end = if (isFoldUnfolded) 36.dp else 20.dp
                                )
                                .verticalScroll(scrollState)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showControls = !showControls
                                }
                        ) {
                            Text(
                                text = currentChapter.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = selectedFont.fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSizeSp * 1.2f).sp
                                ),
                                color = readerTheme.accentColor,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            paragraphsWithIndex.forEach { para ->
                                val isTtsActive = showTtsControls && activeTtsParagraphIndex == para.index
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isTtsActive) readerTheme.accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                        .padding(horizontal = if (isTtsActive) 8.dp else 0.dp, vertical = if (isTtsActive) 4.dp else 0.dp)
                                        .padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = para.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = selectedFont.fontFamily,
                                            fontSize = fontSizeSp.sp,
                                            lineHeight = (fontSizeSp * lineSpacingMultiplier).sp
                                        ),
                                        color = if (isTtsActive) readerTheme.accentColor else readerTheme.textColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            // Chapter End Navigation
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (currentChapterIndex > 0) {
                                    OutlinedButton(
                                        onClick = { onChapterSelected(currentChapterIndex - 1) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = readerTheme.accentColor)
                                    ) {
                                        Text("← Previous Chapter")
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                if (currentChapterIndex < chapters.size - 1) {
                                    Button(
                                        onClick = { onChapterSelected(currentChapterIndex + 1) },
                                        colors = ButtonDefaults.buttonColors(containerColor = readerTheme.accentColor)
                                    ) {
                                        Text("Next Chapter →")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        // TOP CONTROLS BAR (Direct Quick Font Size & Layout Controls)
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = readerTheme.surfaceColor.copy(alpha = 0.96f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = readerTheme.textColor)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = readerTheme.textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            book.author?.let { author ->
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = readerTheme.accentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Direct Quick Font Size Controls & Layout Toggles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Quick Font Smaller
                        IconButton(
                            onClick = { fontSizeSp = (fontSizeSp - 2f).coerceAtLeast(14f) }
                        ) {
                            Text(
                                text = "A-",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = readerTheme.textColor
                            )
                        }

                        // Current Font Size Indicator Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = readerTheme.accentColor.copy(alpha = 0.15f),
                            onClick = { showAppearanceDialog = true }
                        ) {
                            Text(
                                text = "${fontSizeSp.toInt()}sp",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = readerTheme.accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Quick Font Bigger (can reach up to 42sp)
                        IconButton(
                            onClick = { fontSizeSp = (fontSizeSp + 2f).coerceAtMost(42f) }
                        ) {
                            Text(
                                text = "A+",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                color = readerTheme.textColor
                            )
                        }

                        // Paged vs Scroll Mode Toggle
                        IconButton(
                            onClick = {
                                readingLayout = if (readingLayout == ReadingLayout.PAGED) ReadingLayout.SCROLL else ReadingLayout.PAGED
                            }
                        ) {
                            Icon(
                                if (readingLayout == ReadingLayout.PAGED) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.FormatAlignJustify,
                                contentDescription = "Reading Mode (${readingLayout.displayName})",
                                tint = readerTheme.accentColor
                            )
                        }

                        // In-Book Search
                        IconButton(onClick = { showSearchDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Book", tint = readerTheme.textColor)
                        }

                        // Bookmark Current Page
                        val isCurrentBookmarked = remember(bookmarks, currentChapterIndex, currentPageInChapter, currentPdfPage, isPdf) {
                            if (isPdf) {
                                bookmarks.any { it.pageIndex == currentPdfPage }
                            } else {
                                bookmarks.any { it.chapterIndex == currentChapterIndex && it.pageIndex == currentPageInChapter }
                            }
                        }
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    if (isPdf) {
                                        if (isCurrentBookmarked) {
                                            db.bookDao().deleteBookmarkAt(bookId, 0, currentPdfPage)
                                        } else {
                                            db.bookDao().insertBookmark(
                                                BookmarkEntity(
                                                    bookId = bookId,
                                                    chapterIndex = 0,
                                                    chapterTitle = "PDF Page ${currentPdfPage + 1}",
                                                    pageIndex = currentPdfPage,
                                                    snippet = "Page ${currentPdfPage + 1} of $pdfTotalPages"
                                                )
                                            )
                                        }
                                    } else {
                                        val currentChapter = parsedBook?.chapters?.getOrNull(currentChapterIndex)
                                        if (isCurrentBookmarked) {
                                            db.bookDao().deleteBookmarkAt(bookId, currentChapterIndex, currentPageInChapter)
                                        } else {
                                            val snippetText = currentChapter?.plainText?.take(120)?.replace("\n", " ") ?: ""
                                            db.bookDao().insertBookmark(
                                                BookmarkEntity(
                                                    bookId = bookId,
                                                    chapterIndex = currentChapterIndex,
                                                    chapterTitle = currentChapter?.title ?: "Chapter ${currentChapterIndex + 1}",
                                                    pageIndex = currentPageInChapter,
                                                    snippet = snippetText
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isCurrentBookmarked) readerTheme.accentColor else readerTheme.textColor
                            )
                        }

                        // Read Aloud (TTS)
                        if (!isPdf) {
                            IconButton(
                                onClick = {
                                    showTtsControls = !showTtsControls
                                    if (showTtsControls && !ttsManager.isSpeaking) {
                                        val currentParas = parsedBook?.chapters?.getOrNull(currentChapterIndex)?.plainText
                                            ?.split("\n\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                                        ttsManager.startReading(currentParas, activeTtsParagraphIndex.coerceAtLeast(0))
                                    }
                                }
                            ) {
                                Icon(
                                    if (showTtsControls) Icons.Default.VolumeUp else Icons.Default.Headphones,
                                    contentDescription = "Read Aloud (TTS)",
                                    tint = if (showTtsControls) readerTheme.accentColor else readerTheme.textColor
                                )
                            }

                            // Book AI Companion
                            IconButton(onClick = { showBookAiDialog = true }) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Book AI Assistant",
                                    tint = readerTheme.accentColor
                                )
                            }
                        }

                        // Table of Contents & Bookmarks
                        IconButton(onClick = { showTocDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of Contents", tint = readerTheme.textColor)
                        }

                        // Appearance Settings Dialog
                        IconButton(onClick = { showAppearanceDialog = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Appearance", tint = readerTheme.textColor)
                        }
                    }
                }
            }
        }

        // TTS FLOATING PLAYER CONTROLS (Active when Read Aloud is toggled)
        AnimatedVisibility(
            visible = showTtsControls && !isPdf,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showControls) 130.dp else 24.dp)
                .padding(horizontal = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = readerTheme.surfaceColor.copy(alpha = 0.98f),
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, readerTheme.accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Voice & Pitch Settings button
                    IconButton(
                        onClick = { showVoiceSettingsDialog = true }
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "Voice & Pitch Settings", tint = readerTheme.accentColor)
                    }

                    // Speed selector chip (opens Voice & Speech settings)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = readerTheme.accentColor.copy(alpha = 0.15f),
                        onClick = { showVoiceSettingsDialog = true }
                    ) {
                        Text(
                            text = "${ttsManager.speechRate}x",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = readerTheme.accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    // Prev Paragraph
                    IconButton(
                        onClick = { ttsManager.previousParagraph() }
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Paragraph", tint = readerTheme.textColor)
                    }

                    // Play/Pause
                    FilledIconButton(
                        onClick = {
                            if (ttsManager.isSpeaking) {
                                ttsManager.pause()
                            } else if (ttsManager.isPaused) {
                                ttsManager.resume()
                            } else {
                                val currentParas = parsedBook?.chapters?.getOrNull(currentChapterIndex)?.plainText
                                    ?.split("\n\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                                ttsManager.startReading(currentParas, activeTtsParagraphIndex.coerceAtLeast(0))
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = readerTheme.accentColor)
                    ) {
                        Icon(
                            if (ttsManager.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (ttsManager.isSpeaking) "Pause" else "Play",
                            tint = readerTheme.backgroundColor
                        )
                    }

                    // Next Paragraph
                    IconButton(
                        onClick = { ttsManager.nextParagraph() }
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Paragraph", tint = readerTheme.textColor)
                    }

                    // Close TTS Dock
                    IconButton(
                        onClick = {
                            ttsManager.stop()
                            showTtsControls = false
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close TTS", tint = readerTheme.textColor.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // BOTTOM CONTROLS & SCRUBBER BAR
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = readerTheme.surfaceColor.copy(alpha = 0.96f),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isPdf) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPdfPage > 0) currentPdfPage-- },
                                enabled = currentPdfPage > 0
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page", tint = readerTheme.textColor)
                            }

                            Text(
                                text = "Page ${currentPdfPage + 1} of $pdfTotalPages",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = readerTheme.textColor
                            )

                            IconButton(
                                onClick = { if (currentPdfPage < pdfTotalPages - 1) currentPdfPage++ },
                                enabled = currentPdfPage < pdfTotalPages - 1
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Page", tint = readerTheme.textColor)
                            }
                        }
                    } else {
                        val chapters = parsedBook?.chapters ?: emptyList()
                        val totalChapters = chapters.size.coerceAtLeast(1)
                        val percent = ((currentChapterIndex + 1).toFloat() / totalChapters.toFloat() * 100).toInt()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onChapterSelected(currentChapterIndex - 1) },
                                enabled = currentChapterIndex > 0
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Chapter", tint = readerTheme.textColor)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Chapter ${currentChapterIndex + 1} of $totalChapters ($percent%)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = readerTheme.textColor
                                )
                                chapters.getOrNull(currentChapterIndex)?.let { ch ->
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = readerTheme.accentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onChapterSelected(currentChapterIndex + 1) },
                                enabled = currentChapterIndex < totalChapters - 1
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Chapter", tint = readerTheme.textColor)
                            }
                        }

                        Slider(
                            value = currentChapterIndex.toFloat(),
                            onValueChange = { onChapterSelected(it.toInt()) },
                            valueRange = 0f..(totalChapters - 1).coerceAtLeast(0).toFloat(),
                            steps = (totalChapters - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = readerTheme.accentColor,
                                activeTrackColor = readerTheme.accentColor
                            )
                        )
                    }
                }
            }
        }
    }

    // TABLE OF CONTENTS DIALOG
    if (showTocDialog) {
        val chapters = parsedBook?.chapters ?: emptyList()
        Dialog(onDismissRequest = { showTocDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = readerTheme.surfaceColor
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Contents & Bookmarks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTheme.textColor
                        )
                        IconButton(onClick = { showTocDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = readerTheme.textColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TabRow(
                        selectedTabIndex = tocSelectedTab,
                        containerColor = Color.Transparent,
                        contentColor = readerTheme.accentColor,
                        divider = {}
                    ) {
                        Tab(
                            selected = tocSelectedTab == 0,
                            onClick = { tocSelectedTab = 0 },
                            text = { Text("Chapters (${chapters.size})") }
                        )
                        Tab(
                            selected = tocSelectedTab == 1,
                            onClick = { tocSelectedTab = 1 },
                            text = { Text("Bookmarks (${bookmarks.size})") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (tocSelectedTab == 0) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(chapters) { ch ->
                                val isSelected = ch.index == currentChapterIndex
                                Surface(
                                    onClick = {
                                        onChapterSelected(ch.index)
                                        showTocDialog = false
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) readerTheme.accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${ch.index + 1}.",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) readerTheme.accentColor else readerTheme.textColor.copy(alpha = 0.6f),
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Text(
                                            text = ch.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) readerTheme.accentColor else readerTheme.textColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (ch.wordCount > 0) {
                                            Text(
                                                text = "${ch.wordCount} words",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = readerTheme.textColor.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Bookmarks Tab
                        if (bookmarks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = readerTheme.textColor.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No bookmarks yet", style = MaterialTheme.typography.titleSmall, color = readerTheme.textColor)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tap the bookmark icon in the top bar while reading to save pages", style = MaterialTheme.typography.bodySmall, color = readerTheme.textColor.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(bookmarks) { bm ->
                                    Surface(
                                        onClick = {
                                            if (isPdf) {
                                                currentPdfPage = bm.pageIndex
                                            } else {
                                                onChapterSelected(bm.chapterIndex)
                                                currentPageInChapter = bm.pageIndex
                                            }
                                            showTocDialog = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = readerTheme.backgroundColor.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = readerTheme.accentColor, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${bm.chapterTitle} • Page ${bm.pageIndex + 1}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = readerTheme.accentColor
                                                )
                                                if (bm.snippet.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = bm.snippet,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = readerTheme.textColor.copy(alpha = 0.8f),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        db.bookDao().deleteBookmarkById(bm.id)
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Bookmark", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // IN-BOOK SEARCH DIALOG
    if (showSearchDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<BookSearchResult>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }

        LaunchedEffect(searchQuery) {
            if (searchQuery.trim().length < 2) {
                searchResults = emptyList()
                return@LaunchedEffect
            }
            isSearching = true
            withContext(Dispatchers.Default) {
                val q = searchQuery.trim()
                val list = mutableListOf<BookSearchResult>()
                val chapters = parsedBook?.chapters ?: emptyList()
                for (ch in chapters) {
                    val paras = ch.plainText.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
                    paras.forEachIndexed { pIdx, paraText ->
                        val idx = paraText.indexOf(q, ignoreCase = true)
                        if (idx >= 0 && list.size < 60) {
                            val start = (idx - 35).coerceAtLeast(0)
                            val end = (idx + q.length + 45).coerceAtMost(paraText.length)
                            val snippet = (if (start > 0) "…" else "") +
                                    paraText.substring(start, end).replace("\n", " ") +
                                    (if (end < paraText.length) "…" else "")
                            list.add(
                                BookSearchResult(
                                    chapterIndex = ch.index,
                                    chapterTitle = ch.title,
                                    paragraphIndex = pIdx,
                                    snippet = snippet
                                )
                            )
                        }
                    }
                    if (list.size >= 60) break
                }
                searchResults = list
                isSearching = false
            }
        }

        Dialog(onDismissRequest = { showSearchDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = readerTheme.surfaceColor
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search in Book",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTheme.textColor
                        )
                        IconButton(onClick = { showSearchDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = readerTheme.textColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search text, quotes, characters…", color = readerTheme.textColor.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = readerTheme.textColor,
                            unfocusedTextColor = readerTheme.textColor,
                            focusedBorderColor = readerTheme.accentColor,
                            unfocusedBorderColor = readerTheme.textColor.copy(alpha = 0.2f),
                            cursorColor = readerTheme.accentColor
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = readerTheme.accentColor)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = readerTheme.textColor)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = readerTheme.accentColor, modifier = Modifier.size(28.dp))
                        }
                    } else if (searchQuery.trim().length >= 2 && searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No occurrences found for \"$searchQuery\"", color = readerTheme.textColor.copy(alpha = 0.6f))
                        }
                    } else {
                        Text(
                            text = if (searchResults.isNotEmpty()) "${searchResults.size} results found" else "Type at least 2 characters to search",
                            style = MaterialTheme.typography.labelSmall,
                            color = readerTheme.textColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { result ->
                                Surface(
                                    onClick = {
                                        onChapterSelected(result.chapterIndex)
                                        showSearchDialog = false
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = readerTheme.backgroundColor.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = result.chapterTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = readerTheme.accentColor
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = result.snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = readerTheme.textColor,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // APPEARANCE & TYPOGRAPHY DIALOG
    if (showAppearanceDialog) {
        Dialog(onDismissRequest = { showAppearanceDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = readerTheme.surfaceColor
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reader Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTheme.textColor
                        )
                        IconButton(onClick = { showAppearanceDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = readerTheme.textColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Reading Themes (AMOLED, Sepia, Light, Midnight)
                    Text("Theme Palette", style = MaterialTheme.typography.labelMedium, color = readerTheme.textColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderTheme.entries.forEach { theme ->
                            val isSelected = readerTheme == theme
                            Surface(
                                onClick = { readerTheme = theme },
                                shape = RoundedCornerShape(12.dp),
                                color = theme.backgroundColor,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, theme.accentColor) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Font Family
                    Text("Font Style", style = MaterialTheme.typography.labelMedium, color = readerTheme.textColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderFont.entries.forEach { font ->
                            FilterChip(
                                selected = selectedFont == font,
                                onClick = { selectedFont = font },
                                label = { Text(font.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Font Size Slider (14sp up to 42sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Size: ${fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelMedium, color = readerTheme.textColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { fontSizeSp = (fontSizeSp - 2).coerceAtLeast(14f) }) {
                                Text("A-", fontWeight = FontWeight.Bold, color = readerTheme.textColor)
                            }
                            IconButton(onClick = { fontSizeSp = (fontSizeSp + 2).coerceAtMost(42f) }) {
                                Text("A+", fontWeight = FontWeight.Bold, color = readerTheme.textColor, fontSize = 20.sp)
                            }
                        }
                    }
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 14f..42f,
                        steps = 14
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Line Spacing Multiplier
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Line Spacing: ${String.format("%.2f", lineSpacingMultiplier)}x", style = MaterialTheme.typography.labelMedium, color = readerTheme.textColor)
                    }
                    Slider(
                        value = lineSpacingMultiplier,
                        onValueChange = { lineSpacingMultiplier = it },
                        valueRange = 1.2f..2.2f,
                        steps = 10
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Reading Layout: Paged vs Scroll
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reading Layout", style = MaterialTheme.typography.labelMedium, color = readerTheme.textColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = readingLayout == ReadingLayout.PAGED,
                                onClick = { readingLayout = ReadingLayout.PAGED },
                                label = { Text("Paged") }
                            )
                            FilterChip(
                                selected = readingLayout == ReadingLayout.SCROLL,
                                onClick = { readingLayout = ReadingLayout.SCROLL },
                                label = { Text("Scroll") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Spacer(modifier = Modifier.height(14.dp))

                    // Shortcut to Voice & Speech Settings
                    OutlinedButton(
                        onClick = {
                            showAppearanceDialog = false
                            showVoiceSettingsDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = readerTheme.accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Voice, Accent & Pitch Settings", color = readerTheme.textColor)
                    }
                }
            }
        }
    }

    // VOICE, ACCENT & PITCH SETTINGS DIALOG
    if (showVoiceSettingsDialog) {
        var selectedAccentFilter by remember { mutableStateOf("All") }
        Dialog(onDismissRequest = { showVoiceSettingsDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = readerTheme.surfaceColor
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = readerTheme.accentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice & Speech Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = readerTheme.textColor
                            )
                        }
                        IconButton(onClick = { showVoiceSettingsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = readerTheme.textColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Accent Filter Chips (US, UK, System, All)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All", "US 🇺🇸", "UK 🇬🇧", "System 🌐").forEach { accent ->
                            FilterChip(
                                selected = selectedAccentFilter == accent,
                                onClick = { selectedAccentFilter = accent },
                                label = { Text(accent, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Voices List
                    val filteredVoices = remember(ttsManager.availableVoices, selectedAccentFilter) {
                        if (selectedAccentFilter == "All") {
                            ttsManager.availableVoices
                        } else {
                            ttsManager.availableVoices.filter { it.accent == selectedAccentFilter }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredVoices) { voice ->
                            val isSelected = ttsManager.selectedVoiceId == voice.id
                            Surface(
                                onClick = { ttsManager.selectVoice(voice.id) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) readerTheme.accentColor.copy(alpha = 0.18f) else readerTheme.backgroundColor.copy(alpha = 0.6f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, readerTheme.accentColor) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { ttsManager.selectVoice(voice.id) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = voice.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = readerTheme.textColor
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "${voice.accent} • ${voice.gender}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = readerTheme.accentColor
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            ttsManager.selectVoice(voice.id)
                                            ttsManager.speakTestPreview("Previewing ${voice.displayName}")
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayCircleOutline, contentDescription = "Test Voice", tint = readerTheme.accentColor)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pitch Slider & Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pitch: ${(ttsManager.pitch * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTheme.textColor
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { ttsManager.setPitch(0.80f) }) { Text("Deep", fontSize = 11.sp, color = readerTheme.accentColor) }
                            TextButton(onClick = { ttsManager.setPitch(1.0f) }) { Text("Normal", fontSize = 11.sp, color = readerTheme.accentColor) }
                            TextButton(onClick = { ttsManager.setPitch(1.25f) }) { Text("High", fontSize = 11.sp, color = readerTheme.accentColor) }
                        }
                    }
                    Slider(
                        value = ttsManager.pitch,
                        onValueChange = { ttsManager.setPitch(it) },
                        valueRange = 0.5f..1.6f,
                        colors = SliderDefaults.colors(
                            thumbColor = readerTheme.accentColor,
                            activeTrackColor = readerTheme.accentColor
                        )
                    )

                    // Speed / Speech Rate Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speech Rate: ${String.format("%.2f", ttsManager.speechRate)}x",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTheme.textColor
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { ttsManager.setRate(0.85f) }) { Text("0.85x", fontSize = 11.sp, color = readerTheme.accentColor) }
                            TextButton(onClick = { ttsManager.setRate(1.0f) }) { Text("1.0x", fontSize = 11.sp, color = readerTheme.accentColor) }
                            TextButton(onClick = { ttsManager.setRate(1.25f) }) { Text("1.25x", fontSize = 11.sp, color = readerTheme.accentColor) }
                            TextButton(onClick = { ttsManager.setRate(1.5f) }) { Text("1.5x", fontSize = 11.sp, color = readerTheme.accentColor) }
                        }
                    }
                    Slider(
                        value = ttsManager.speechRate,
                        onValueChange = { ttsManager.setRate(it) },
                        valueRange = 0.5f..2.2f,
                        colors = SliderDefaults.colors(
                            thumbColor = readerTheme.accentColor,
                            activeTrackColor = readerTheme.accentColor
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Test Sample Button
                    Button(
                        onClick = { ttsManager.speakTestPreview() },
                        colors = ButtonDefaults.buttonColors(containerColor = readerTheme.accentColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = readerTheme.backgroundColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Voice & Pitch Sample", color = readerTheme.backgroundColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // BOOK AI COMPANION DIALOG
    if (showBookAiDialog) {
        val currentChapter = parsedBook?.chapters?.getOrNull(currentChapterIndex)
        val chapterText = currentChapter?.plainText ?: ""
        var customPrompt by remember { mutableStateOf("") }
        var aiResponse by remember { mutableStateOf<String?>(null) }
        var isAnalyzing by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showBookAiDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(20.dp),
                color = readerTheme.surfaceColor
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = readerTheme.accentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Book AI Assistant",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = readerTheme.textColor
                                )
                                Text(
                                    text = "Active: ${aiModelManager.getActiveModelInfo()?.name ?: "SmolLM2 135M"} • 100% Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = readerTheme.accentColor
                                )
                            }
                        }
                        IconButton(onClick = { showBookAiDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = readerTheme.textColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val chapterHeaderTitle = currentChapter?.title ?: "Chapter ${currentChapterIndex + 1}"
                    Text(
                        text = "Analyzing: \"$chapterHeaderTitle\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = readerTheme.textColor.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Quick Action Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                isAnalyzing = true
                                scope.launch(Dispatchers.Default) {
                                    val result = aiEngine.sendMessage("Summarize this chapter:", chapterText)
                                    isAnalyzing = false
                                }
                            },
                            label = { Text("📖 Summarize", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                isAnalyzing = true
                                scope.launch(Dispatchers.Default) {
                                    aiEngine.sendMessage("Explain the key themes and symbolism in this chapter:", chapterText)
                                    isAnalyzing = false
                                }
                            },
                            label = { Text("🔑 Key Themes", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                isAnalyzing = true
                                scope.launch(Dispatchers.Default) {
                                    aiEngine.sendMessage("Extract notable quotes from this chapter:", chapterText)
                                    isAnalyzing = false
                                }
                            },
                            label = { Text("💡 Quotes", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Result Display Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = readerTheme.backgroundColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val lastAiMsg = aiEngine.messages.lastOrNull { it.role == com.offlinevault.app.service.ai.MessageRole.ASSISTANT }
                        val displayText = if (aiEngine.isGenerating) aiEngine.currentStreamingText else lastAiMsg?.text ?: ""

                        if (isAnalyzing || aiEngine.isGenerating) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = readerTheme.accentColor, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Generating on-device analysis…", style = MaterialTheme.typography.labelSmall, color = readerTheme.textColor)
                                }
                            }
                        } else if (displayText.isNotBlank()) {
                            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("AI Analysis", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = readerTheme.accentColor)
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Book Summary", displayText))
                                            Toast.makeText(context, "Copied analysis", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = readerTheme.textColor, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val scrollState = rememberScrollState()
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = readerTheme.textColor,
                                    modifier = Modifier.verticalScroll(scrollState)
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Tap a prompt above or ask a question below to analyze this chapter with your offline AI.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = readerTheme.textColor.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Custom Question Input Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customPrompt,
                            onValueChange = { customPrompt = it },
                            placeholder = { Text("Ask anything about this chapter…", fontSize = 12.sp, color = readerTheme.textColor.copy(alpha = 0.5f)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = readerTheme.textColor,
                                unfocusedTextColor = readerTheme.textColor,
                                focusedBorderColor = readerTheme.accentColor,
                                unfocusedBorderColor = readerTheme.textColor.copy(alpha = 0.2f),
                                cursorColor = readerTheme.accentColor
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledIconButton(
                            onClick = {
                                if (customPrompt.isNotBlank() && !aiEngine.isGenerating) {
                                    val q = customPrompt.trim()
                                    customPrompt = ""
                                    aiEngine.sendMessage(q, chapterText)
                                }
                            },
                            enabled = customPrompt.isNotBlank() && !aiEngine.isGenerating,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = readerTheme.accentColor)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = readerTheme.backgroundColor)
                        }
                    }
                }
            }
        }
    }
}

