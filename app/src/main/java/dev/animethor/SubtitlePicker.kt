package dev.animethor

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubtitlePicker(
    private val activity: AppCompatActivity,
    private val kitsunekko: KitsunekkoClient,
    private val onFileChosen: (SubtitleFile) -> Unit,
    private val onLoadFromDevice: () -> Unit,
) {
    fun show(searchText: String, episode: Int?) {
        activity.lifecycleScope.launch {
            Toast.makeText(activity, "Loading subtitle list…", Toast.LENGTH_SHORT).show()
            val folders = withContext(Dispatchers.IO) { runCatching { kitsunekko.animeFolders() } }
            folders.onSuccess { showFolderDialog(it, searchText, episode) }
            folders.onFailure { showError("Could not load the subtitle list: ${it.message}") }
        }
    }

    private fun showFolderDialog(folders: List<AnimeFolder>, searchText: String, episode: Int?) {
        val searchField = EditText(activity).apply {
            setText(searchText)
            hint = "Anime name in romaji, e.g. Sousou no Frieren"
            setSingleLine()
        }
        val listView = ListView(activity)
        val adapter = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter
        var shownFolders = emptyList<AnimeFolder>()

        fun refreshList() {
            shownFolders = FolderMatching.search(folders, searchField.text.toString()).take(MAX_FOLDERS_SHOWN)
            adapter.clear()
            adapter.addAll(shownFolders.map { "${it.name}   (${it.category.replace('_', ' ')})" })
        }

        searchField.doAfterTextChanged { refreshList() }
        refreshList()

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(searchField)
            addView(listView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320)))
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Japanese subtitles: choose the anime")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("File on device") { _, _ -> onLoadFromDevice() }
            .create()
        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            showFileDialog(shownFolders[position], episode)
        }
        dialog.show()
    }

    private fun showFileDialog(folder: AnimeFolder, episode: Int?) {
        activity.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { runCatching { kitsunekko.subtitleFiles(folder) } }
            files.onSuccess { showFileList(folder, orderForEpisode(it, episode), episode) }
            files.onFailure { showError("Could not list the files: ${it.message}") }
        }
    }

    private fun orderForEpisode(files: List<SubtitleFile>, episode: Int?): List<SubtitleFile> {
        val matching = files
            .filter { episode != null && it.episode == episode }
            .sortedByDescending { SubtitleFileSources.preference(it.fileName) }
        return matching + (files - matching.toSet())
    }

    private fun showFileList(folder: AnimeFolder, files: List<SubtitleFile>, episode: Int?) {
        if (files.isEmpty()) {
            showError("No .srt / .ass / .vtt files in ${folder.name} (zip files are not supported yet).")
            return
        }
        val labels = files.map { file ->
            val marker = if (episode != null && file.episode == episode) "★ " else ""
            marker + "[" + SubtitleFileSources.label(file.fileName) + "]  " + file.fileName
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(if (episode != null) "${folder.name}: episode $episode first" else folder.name)
            .setItems(labels.toTypedArray()) { _, position -> onFileChosen(files[position]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(activity).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val MAX_FOLDERS_SHOWN = 80
    }
}
