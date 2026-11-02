package com.offlinevault.app.service.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.offlinevault.app.MainActivity
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.BookEntity
import com.offlinevault.app.data.local.entity.DownloadEntity
import com.offlinevault.app.data.local.entity.EpisodeEntity
import com.offlinevault.app.service.podcast.PodcastPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class VaultMediaLibraryService : MediaLibraryService() {

    companion object {
        const val ROOT_ID = "root"
        const val CAT_PODCASTS = "cat_podcasts"
        const val CAT_VIDEOS = "cat_videos"
        const val CAT_BOOKS = "cat_books"

        const val PREFIX_PODCAST = "pod_"
        const val PREFIX_VIDEO = "vid_"
        const val PREFIX_BOOK = "book_"

        const val ACTION_REWIND_10 = "com.offlinevault.app.action.REWIND_10"
        const val ACTION_FORWARD_30 = "com.offlinevault.app.action.FORWARD_30"
    }

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()

        // Initialize Android TextToSpeech engine for Book Read Aloud
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }

        val player = PodcastPlayerManager.getPlayer(this)

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Custom in-dash buttons for car steering / dashboard
        val rewindButton = CommandButton.Builder()
            .setDisplayName("Rewind 10s")
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(SessionCommand(ACTION_REWIND_10, Bundle.EMPTY))
            .build()

        val forwardButton = CommandButton.Builder()
            .setDisplayName("Forward 30s")
            .setIconResId(android.R.drawable.ic_media_ff)
            .setSessionCommand(SessionCommand(ACTION_FORWARD_30, Bundle.EMPTY))
            .build()

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(pendingIntent)
            .setCustomLayout(ImmutableList.of(rewindButton, forwardButton))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        tts?.shutdown()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("OfflineVault")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

            serviceScope.launch {
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    val items = mutableListOf<MediaItem>()

                    when (parentId) {
                        ROOT_ID -> {
                            // Top-Level 3 Media Pillars for Android Auto Dashboard
                            items.add(
                                createCategoryItem(
                                    CAT_PODCASTS,
                                    "🎙️ Podcasts",
                                    "Downloaded episodes and subscriptions"
                                )
                            )
                            items.add(
                                createCategoryItem(
                                    CAT_VIDEOS,
                                    "🎬 Downloaded Videos (Audio Only)",
                                    "Listen to downloaded videos while driving"
                                )
                            )
                            items.add(
                                createCategoryItem(
                                    CAT_BOOKS,
                                    "📚 Books Read Aloud",
                                    "Listen to saved books narrated by voice"
                                )
                            )
                        }

                        CAT_PODCASTS -> {
                            // Pillar 1: Downloaded Podcast Episodes
                            val episodes: List<EpisodeEntity> = db.feedDao().getDownloadedEpisodesList()
                            for (ep in episodes) {
                                val artUri = if (!ep.imageUrl.isNullOrBlank()) Uri.parse(ep.imageUrl) else null
                                val item = MediaItem.Builder()
                                    .setMediaId("$PREFIX_PODCAST${ep.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(ep.title)
                                            .setArtist("Podcast Episode")
                                            .setAlbumTitle("Offline Podcasts")
                                            .setArtworkUri(artUri)
                                            .setIsPlayable(true)
                                            .setIsBrowsable(false)
                                            .build()
                                    )
                                    .build()
                                items.add(item)
                            }
                        }

                        CAT_VIDEOS -> {
                            // Pillar 2: Downloaded Videos (Audio-Only Driving Mode)
                            val videos: List<DownloadEntity> = db.downloadDao().getCompletedVideosList()
                            for (vid in videos) {
                                val item = MediaItem.Builder()
                                    .setMediaId("$PREFIX_VIDEO${vid.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(vid.title)
                                            .setArtist("Downloaded Video")
                                            .setAlbumTitle("Video Audio")
                                            .setIsPlayable(true)
                                            .setIsBrowsable(false)
                                            .build()
                                    )
                                    .build()
                                items.add(item)
                            }
                        }

                        CAT_BOOKS -> {
                            // Pillar 3: Books Read Aloud (TTS Reader)
                            val books: List<BookEntity> = db.bookDao().getDownloadedBooksList()
                            for (book in books) {
                                val artUri = if (!book.coverUrl.isNullOrBlank()) Uri.parse(book.coverUrl) else null
                                val item = MediaItem.Builder()
                                    .setMediaId("$PREFIX_BOOK${book.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(book.title)
                                            .setArtist(book.author ?: "Author")
                                            .setAlbumTitle("Audiobooks & Reader")
                                            .setArtworkUri(artUri)
                                            .setIsPlayable(true)
                                            .setIsBrowsable(false)
                                            .build()
                                    )
                                    .build()
                                items.add(item)
                            }
                        }
                    }

                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                }
            }

            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()

            serviceScope.launch {
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    val resolvedItem = resolveMediaItem(db, mediaId)
                    if (resolvedItem != null) {
                        future.set(LibraryResult.ofItem(resolvedItem, null))
                    } else {
                        future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                    }
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }

            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()

            serviceScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val resolvedList = mutableListOf<MediaItem>()

                for (item in mediaItems) {
                    val mediaId = item.mediaId
                    val resolved = resolveMediaItemWithUri(db, mediaId)
                    if (resolved != null) {
                        resolvedList.add(resolved)
                    } else {
                        resolvedList.add(item)
                    }
                }
                future.set(resolvedList)
            }

            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_REWIND_10 -> {
                    PodcastPlayerManager.seekRelative(-10000L)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_FORWARD_30 -> {
                    PodcastPlayerManager.seekRelative(30000L)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun createCategoryItem(id: String, title: String, subtitle: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private suspend fun resolveMediaItem(db: AppDatabase, mediaId: String): MediaItem? {
        return when {
            mediaId.startsWith(PREFIX_PODCAST) -> {
                val epId = mediaId.removePrefix(PREFIX_PODCAST).toLongOrNull() ?: return null
                val ep = db.feedDao().getEpisodeById(epId) ?: return null
                val artUri = if (!ep.imageUrl.isNullOrBlank()) Uri.parse(ep.imageUrl) else null
                MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(ep.title)
                            .setArtist("Podcast Episode")
                            .setArtworkUri(artUri)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            }
            mediaId.startsWith(PREFIX_VIDEO) -> {
                val vidId = mediaId.removePrefix(PREFIX_VIDEO).toLongOrNull() ?: return null
                val vid = db.downloadDao().getDownloadById(vidId) ?: return null
                MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(vid.title)
                            .setArtist("Downloaded Video")
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            }
            mediaId.startsWith(PREFIX_BOOK) -> {
                val bookId = mediaId.removePrefix(PREFIX_BOOK).toLongOrNull() ?: return null
                val book = db.bookDao().getBookById(bookId) ?: return null
                val artUri = if (!book.coverUrl.isNullOrBlank()) Uri.parse(book.coverUrl) else null
                MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(book.title)
                            .setArtist(book.author ?: "Author")
                            .setArtworkUri(artUri)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            }
            else -> null
        }
    }

    private suspend fun resolveMediaItemWithUri(db: AppDatabase, mediaId: String): MediaItem? {
        return when {
            mediaId.startsWith(PREFIX_PODCAST) -> {
                val epId = mediaId.removePrefix(PREFIX_PODCAST).toLongOrNull() ?: return null
                val ep = db.feedDao().getEpisodeById(epId) ?: return null
                val uri = if (ep.localAudioPath != null && File(ep.localAudioPath).exists()) {
                    Uri.fromFile(File(ep.localAudioPath))
                } else {
                    Uri.parse(ep.audioUrl)
                }
                val artUri = if (!ep.imageUrl.isNullOrBlank()) Uri.parse(ep.imageUrl) else null
                MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(ep.title)
                            .setArtist("Podcast Episode")
                            .setArtworkUri(artUri)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            }
            mediaId.startsWith(PREFIX_VIDEO) -> {
                val vidId = mediaId.removePrefix(PREFIX_VIDEO).toLongOrNull() ?: return null
                val vid = db.downloadDao().getDownloadById(vidId) ?: return null
                val filePath = vid.localFilePath ?: return null
                val file = File(filePath)
                if (!file.exists()) return null

                // ExoPlayer extracts and plays the audio track from the video file cleanly
                MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(Uri.fromFile(file))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(vid.title)
                            .setArtist("Video Audio (Driving Mode)")
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            }
            mediaId.startsWith(PREFIX_BOOK) -> {
                val bookId = mediaId.removePrefix(PREFIX_BOOK).toLongOrNull() ?: return null
                val book = db.bookDao().getBookById(bookId) ?: return null
                val filePath = book.localFilePath

                // If an audio file or TTS audio exists, play it
                val audioUri = if (filePath != null && File(filePath).exists() && (filePath.endsWith(".mp3") || filePath.endsWith(".m4a") || filePath.endsWith(".wav") || filePath.endsWith(".ogg"))) {
                    Uri.fromFile(File(filePath))
                } else {
                    // Synthesize chapter text into cache WAV for in-car speech playback
                    synthesizeBookChapterAudio(book)
                } ?: return null

                val artUri = if (!book.coverUrl.isNullOrBlank()) Uri.parse(book.coverUrl) else null
                MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(audioUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(book.title)
                            .setArtist(book.author ?: "Author")
                            .setArtworkUri(artUri)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            }
            else -> null
        }
    }

    private fun synthesizeBookChapterAudio(book: BookEntity): Uri? {
        val cacheDir = File(cacheDir, "tts_audio").apply { if (!exists()) mkdirs() }
        val targetFile = File(cacheDir, "book_${book.id}_ch${book.currentChapterIndex}.wav")

        if (targetFile.exists() && targetFile.length() > 1024) {
            return Uri.fromFile(targetFile)
        }

        // Read sample or chapter text from the file if available
        val textToSpeak = try {
            val bookFile = book.localFilePath?.let { File(it) }
            if (bookFile != null && bookFile.exists()) {
                val content = bookFile.readText()
                content.take(4000) // First 4k characters of chapter
            } else {
                "${book.title} by ${book.author ?: "Unknown author"}. Reading aloud in your vehicle with OfflineVault."
            }
        } catch (_: Exception) {
            "${book.title} by ${book.author ?: "Unknown author"}."
        }

        if (ttsReady && tts != null) {
            val params = Bundle()
            tts?.synthesizeToFile(textToSpeak, params, targetFile, "book_${book.id}")
            // Give short moment for first buffer write
            Thread.sleep(300)
            if (targetFile.exists()) {
                return Uri.fromFile(targetFile)
            }
        }
        return null
    }
}
