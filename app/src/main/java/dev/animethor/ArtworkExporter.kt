package dev.animethor

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException

class ArtworkExporter(private val context: Context) {

    fun saveToDownloads(): Int {
        val fileNames = context.assets.list(ASSET_FOLDER)?.filter { it.endsWith(".png") } ?: emptyList()
        fileNames.forEach { saveOne(it) }
        return fileNames.size
    }

    private fun saveOne(fileName: String) {
        val details = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_FOLDER")
        }
        val resolver = context.contentResolver
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, details)
            ?: throw IOException("Android refused to create $fileName in Downloads")
        resolver.openOutputStream(target).use { output ->
            if (output == null) {
                throw IOException("Could not write $fileName")
            }
            context.assets.open("$ASSET_FOLDER/$fileName").use { input -> input.copyTo(output) }
        }
    }

    companion object {
        const val DOWNLOAD_FOLDER = "Aynime"
        private const val ASSET_FOLDER = "artwork"
    }
}
