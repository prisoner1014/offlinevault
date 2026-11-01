package com.offlinevault.app.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileSharingUtils {

    /**
     * Safely opens any video or audio file in an external media player
     * (e.g. VLC, MX Player, Samsung Video Player) with temporary content URI read permissions.
     */
    fun openInExternalPlayer(context: Context, filePath: String, isAudio: Boolean = false) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
                return
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "com.offlinevault.app.fileprovider",
                file
            )
            val mimeType = if (isAudio) "audio/*" else "video/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Play with"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open external player: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
