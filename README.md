# Offline Vault for Samsung Galaxy Fold 📱

An all-in-one offline media hub, content downloader, podcast player, low-data browser, and offline web archiver designed specifically for Samsung Galaxy Fold devices.

---

## 🌟 Key Features

### 1. 🎬 Universal Video & Audio Downloader
- Powered by embedded **`yt-dlp`** and **`FFmpeg`** via `youtubedl-android`.
- **Wide Range of Quality & Data-Saving Options:**
  - 🎬 **Video:** 1080p Full HD, 720p HD, 480p SD, **360p Low (Save Data)**, **240p Very Low**, and **144p Ultra Low (Minimal Data)**.
  - 🎙️ **Audio Bitrate Selection:** **HQ 320 kbps MP3**, **Standard 128 kbps**, **Voice/Podcast 64 kbps (Small Size)**, and **Ultra-Low 32 kbps (Tiny File Size)**.
- **Subtitles & Captions:** 1-tap toggle to download and embed subtitles (`.srt`/`.vtt`) into media files.
- Accelerated multi-connection downloading via **Aria2c**.
- Real-time progress, speed, and ETA monitoring with background foreground notification service.
- System share target: share links directly from YouTube, X (Twitter), Reddit, or Chrome to immediately download.

### 2. 🌐 Low-Data Web Browser & Deep Site & Links Downloader
- **Deep Recursive Site Crawler (`SiteCrawler`):**
  - Download a page and recursively follow every internal link on the page!
  - **Crawl Depth Control:** Level 0 (Single page), Level 1 (Page + all direct links), Level 2 (Deep links).
  - **Safety & Page Budget:** Set maximum page caps (10, 25, 50, 100 pages).
  - **Image & Data Saver Options:**
    - 🚫 **No Images (Pure Text / HTML / CSS):** Strips image downloads completely for near-zero bandwidth and tiny disk usage.
    - 🖼️ **Low Quality Images (WebP 30%):** Downloads images, downscales them to 480px, and compresses to 30% WebP quality, saving ~85-90% data!
    - 📷 **Full Quality:** Downloads images as hosted online.
  - **Domain Restriction:** Stays strictly within the same domain to avoid downloading external third-party sites or ads.
  - **Offline Navigation:** Automatically rewrites internal links to relative local files, so you can click between all saved pages 100% offline inside the app!
- **Ad & Tracker Blocking:** Built-in filter rules block heavy ads, trackers, and telemetry scripts to minimize bandwidth.
- **Data Saver Mode:** Toggle image loading on/off on the fly (`loadsImagesAutomatically = false`) to browse with near-zero data usage.
- **1-Tap MHTML Saver:** Archives full web pages with exact styling into `.mht` files for offline reading.

### 4. 🎙️ Offline RSS Feed & Podcast Player
- Synchronize any standard RSS/Atom podcast feed.
- Download episodes directly into the offline vault for commute or airplane listening.
- Variable playback speeds, progress persistence, and background audio service.

### 5. 🎛️ Network & Mobile Data Controls
- **Auto-Download on Mobile Data:** Toggle on/off in Settings.
- **Cellular Limit Capping:** Choose between 50MB, 100MB, or 250MB caps to prevent unexpected mobile data consumption.

### 6. 📱 Samsung Fold Posture Support
- **Cover Screen (Narrow):** Ergonomic bottom navigation bar with thumb-accessible controls.
- **Unfolded Inner Screen (7.6"+):** Adaptive navigation rail with expanded workspace and dual-pane browsing/downloading.
- **Flex Mode (Tabletop):** Automatic layout shift when device is folded at ~90 degrees.

---

## 📂 Project Architecture

```
project/
├── gradle/
│   ├── libs.versions.toml             # Gradle Version Catalog (dependencies & plugins)
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts                   # Root build script
├── settings.gradle.kts                # Project & repository settings
├── gradle.properties
└── app/
    ├── build.gradle.kts               # Module build script with Compose & yt-dlp
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml        # Services, permissions, and Fold configs
        ├── res/
        │   └── values/strings.xml, themes.xml
        └── java/com/offlinevault/app/
            ├── MainActivity.kt         # Entry point & Intent share handler
            ├── OfflineVaultApp.kt      # Application initialization & notifications
            ├── data/
            │   ├── local/              # Room Database (Downloads, Feeds, Archives)
            │   ├── storage/            # StorageTracker, StorageBreakdown & Cleaner
            │   └── preferences/        # DataStore Settings (Mobile data caps, AdBlock)
            ├── service/
            │   └── download/           # DownloaderService & YtDlpEngine
            └── ui/
                ├── navigation/         # AdaptiveNavScaffold (Fold cover vs inner screen)
                └── screens/
                    ├── vault/          # Storage Analytics & Offline Files
                    ├── downloader/     # yt-dlp Video/Audio Downloader
                    ├── browser/        # Low-Data Browser & MHTML Archiver
                    ├── podcasts/       # RSS & Offline Podcast Hub
                    └── settings/       # Cellular & Storage Configuration
```

---

## 🛠️ Building & Running

1. Open the project in **Android Studio** (Koala / Ladybug or newer recommended).
2. Sync Gradle with project files.
3. Connect your **Samsung Galaxy Fold** via USB with USB Debugging enabled (or use the 7.6" Foldable Emulator).
4. Run the `:app` target.
