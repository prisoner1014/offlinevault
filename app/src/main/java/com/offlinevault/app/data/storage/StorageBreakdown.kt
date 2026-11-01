package com.offlinevault.app.data.storage

data class StorageBreakdown(
    val videoBytes: Long,
    val podcastBytes: Long,
    val bookBytes: Long = 0L,
    val webArchiveBytes: Long,
    val cacheBytes: Long,
    val totalAppUsedBytes: Long,
    val freeDeviceBytes: Long,
    val totalDeviceBytes: Long
) {
    val videoMb: Double get() = videoBytes / (1024.0 * 1024.0)
    val podcastMb: Double get() = podcastBytes / (1024.0 * 1024.0)
    val bookMb: Double get() = bookBytes / (1024.0 * 1024.0)
    val webArchiveMb: Double get() = webArchiveBytes / (1024.0 * 1024.0)
    val cacheMb: Double get() = cacheBytes / (1024.0 * 1024.0)
    val totalAppUsedMb: Double get() = totalAppUsedBytes / (1024.0 * 1024.0)
    val freeDeviceGb: Double get() = freeDeviceBytes / (1024.0 * 1024.0 * 1024.0)
    val totalDeviceGb: Double get() = totalDeviceBytes / (1024.0 * 1024.0 * 1024.0)

    val appUsagePercentOfDevice: Float
        get() = if (totalDeviceBytes > 0) (totalAppUsedBytes.toFloat() / totalDeviceBytes.toFloat()) * 100f else 0f

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
    }
}
