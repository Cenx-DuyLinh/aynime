package dev.animethor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class ReleaseInfo(val version: String, val apkAddress: String, val notes: String)

object VersionNumbers {
    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = parts(candidate)
        val currentParts = parts(current)
        val length = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until length) {
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (candidatePart != currentPart) {
                return candidatePart > currentPart
            }
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
}

class AppUpdater(private val activity: AppCompatActivity, private val store: AppStore) {

    fun checkForUpdate() {
        val repository = store.updateRepository()
        if (repository.isBlank()) {
            askForRepository()
            return
        }
        activity.lifecycleScope.launch {
            toast("Checking for updates…")
            val result = withContext(Dispatchers.IO) { runCatching { latestRelease(repository) } }
            result.onFailure { showMessage("Could not check $repository: ${it.message}", offerRepositoryChange = true) }
            result.onSuccess { release -> offerRelease(release) }
        }
    }

    fun askForRepository() {
        val field = EditText(activity).apply {
            setText(store.updateRepository())
            hint = "your-github-name/anime-thor"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Where do updates come from?")
            .setMessage("Your personal GitHub repository (name/repository). Each new APK is uploaded there as a Release.")
            .setView(field)
            .setPositiveButton("Save and check") { _, _ ->
                store.saveUpdateRepository(field.text.toString())
                if (store.updateRepository().isNotBlank()) {
                    checkForUpdate()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun latestRelease(repository: String): ReleaseInfo {
        val release = JSONObject(Http.downloadText("https://api.github.com/repos/$repository/releases/latest"))
        val assets = release.getJSONArray("assets")
        val apkAsset = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
            ?: throw IllegalStateException("the latest release has no .apk file")
        return ReleaseInfo(
            version = releaseVersion(release.getString("tag_name"), apkAsset.getString("name")),
            apkAddress = apkAsset.getString("browser_download_url"),
            notes = release.optString("body"),
        )
    }

    private fun releaseVersion(tagName: String, apkFileName: String): String {
        if (tagName.any(Char::isDigit)) {
            return tagName
        }
        return VERSION_IN_FILE_NAME.find(apkFileName)?.value ?: tagName
    }

    private fun offerRelease(release: ReleaseInfo) {
        val currentVersion = BuildConfig.VERSION_NAME
        if (!VersionNumbers.isNewer(release.version, currentVersion)) {
            showMessage("You have the newest version ($currentVersion).", offerRepositoryChange = false)
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Version ${release.version.removePrefix("v")} is available")
            .setMessage(release.notes.ifBlank { "You have $currentVersion." })
            .setPositiveButton("Download and install") { _, _ -> downloadAndInstall(release) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstall(release: ReleaseInfo) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            askForInstallPermission()
            return
        }
        activity.lifecycleScope.launch {
            toast("Downloading ${release.version}…")
            val result = withContext(Dispatchers.IO) { runCatching { downloadApk(release) } }
            result.onFailure { showMessage("Download failed: ${it.message}", offerRepositoryChange = false) }
            result.onSuccess { apkFile -> openInstaller(apkFile) }
        }
    }

    private fun downloadApk(release: ReleaseInfo): File {
        val updateFolder = File(activity.cacheDir, "updates").apply { mkdirs() }
        updateFolder.listFiles()?.forEach { it.delete() }
        val apkFile = File(updateFolder, "Aynime-${release.version}.apk")
        apkFile.writeBytes(Http.downloadBytes(release.apkAddress))
        return apkFile
    }

    private fun openInstaller(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(installIntent)
    }

    private fun askForInstallPermission() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Allow updates")
            .setMessage("Android needs your OK once: turn on \"Allow from this source\" for Aynime, come back, and press Download again.")
            .setPositiveButton("Open setting") { _, _ ->
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                activity.startActivity(settingsIntent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMessage(message: String, offerRepositoryChange: Boolean) {
        val builder = MaterialAlertDialogBuilder(activity).setMessage(message).setPositiveButton("OK", null)
        if (offerRepositoryChange) {
            builder.setNeutralButton("Change repository") { _, _ -> askForRepository() }
        }
        builder.show()
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        val VERSION_IN_FILE_NAME = Regex("""\d+(\.\d+)+""")
    }
}
