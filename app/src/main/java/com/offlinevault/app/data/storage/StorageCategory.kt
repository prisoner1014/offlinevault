package com.offlinevault.app.data.storage

enum class StorageCategory(val displayName: String, val subDirectory: String, val icon: String) {
    VIDEOS("Videos & Audio", "vault/videos", "🎬"),
    PODCASTS("Podcasts", "vault/podcasts", "🎙️"),
    BOOKS("Books & Documents", "vault/books", "📚"),
    WEB_ARCHIVES("Web Archives & Reader", "vault/web_archives", "📰"),
    TEMP_CACHE("Cache & Temp Chunks", "vault/cache", "🧹")
}
