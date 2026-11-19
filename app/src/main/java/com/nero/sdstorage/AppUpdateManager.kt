package com.nero.sdstorage

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Checks version.json from the public release metadata endpoint.
 * The APK itself is accepted only when its package name and signing certificate
 * match the already-installed app. Android's package installer still asks the
 * user to confirm the installation.
 */
class AppUpdateManager(private val activity: Activity) {
    companion object {
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/dev-nero-vn/SeverMinecraft/main/version.json"
        private const val PREFS = "sd_micro_tool_update"
        private const val PENDING_APK = "pending_apk_path"
        private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    }

    fun checkForUpdate(showNoUpdate: Boolean) {
        Thread {
            try {
                val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                val manifest = try {
                    require(connection.responseCode in 200..299) {
                        "Máy chủ phiên bản trả HTTP " + connection.responseCode
                    }
                    val text = InputStreamReader(connection.inputStream, Charsets.UTF_8).use { it.readText() }
                    JSONObject(text)
                } finally {
                    connection.disconnect()
                }

                val remoteCode = manifest.optLong("versionCode", 0L)
                val remoteVersion = manifest.optString("version", "").ifBlank { "Không rõ" }
                val currentInfo = getInstalledPackageInfo()
                val currentCode = packageVersionCode(currentInfo)

                // Do not show a prompt for the same or an older release.
                if (remoteCode <= currentCode) {
                    if (showNoUpdate) activity.runOnUiThread {
                        Toast.makeText(activity, "Bạn đang dùng phiên bản mới nhất.", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val downloadUrl = manifest.optString("downloadUrl", "")
                require(downloadUrl.startsWith("https://")) { "Liên kết cập nhật không an toàn" }
                val changelog = manifest.optString("changelog", "Không có ghi chú thay đổi.")
                val mandatory = manifest.optBoolean("mandatory", false)
                activity.runOnUiThread {
                    showUpdateDialog(
                        currentInfo.versionName ?: "Không rõ",
                        remoteVersion,
                        remoteCode,
                        downloadUrl,
                        changelog,
                        mandatory,
                        manifest
                    )
                }
            } catch (error: Exception) {
                if (showNoUpdate) activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Không kiểm tra được cập nhật: " + (error.message ?: "lỗi kết nối"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    fun resumePendingInstall() {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val path = prefs.getString(PENDING_APK, null) ?: return
        if (!activity.packageManager.canRequestPackageInstalls()) return
        prefs.edit().remove(PENDING_APK).apply()
        val apk = File(path)
        if (apk.exists()) installWithAndroidInstaller(apk)
        else Toast.makeText(activity, "Không còn tệp cập nhật. Hãy kiểm tra lại phiên bản.", Toast.LENGTH_LONG).show()
    }

    private fun showUpdateDialog(
        currentVersion: String,
        newVersion: String,
        newCode: Long,
        downloadUrl: String,
        changelog: String,
        mandatory: Boolean,
        manifest: JSONObject
    ) {
        val message = buildString {
            append("Phiên bản hiện tại: ").append(currentVersion).append("\n")
            append("Phiên bản mới: ").append(newVersion).append(" (").append(newCode).append(")\n\n")
            append("Nội dung thay đổi:\n").append(changelog)
            if (mandatory) append("\n\nĐây là bản cập nhật bắt buộc.")
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Có bản cập nhật mới")
            .setMessage(message)
            .setPositiveButton("Cập nhật ngay") { _, _ ->
                downloadAndInstall(downloadUrl, newCode, manifest)
            }
        if (!mandatory) dialog.setNegativeButton("Để sau", null)
        dialog.setCancelable(!mandatory).show()
    }

    private fun downloadAndInstall(downloadUrl: String, versionCode: Long, manifest: JSONObject) {
        val progress = ProgressDialog(activity).apply {
            setTitle("Đang tải cập nhật")
            setMessage("Đang tải SD Micro Tool vào vùng tạm của trình cài đặt…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = true
            setCancelable(false)
            show()
        }

        Thread {
            var downloadedApk: File? = null
            var failure: Exception? = null
            try {
                val folder = File(activity.cacheDir, "updates")
                if (!folder.exists()) check(folder.mkdirs()) { "Không tạo được thư mục tạm cập nhật" }
                val temporary = File(folder, "sd-micro-tool-$versionCode.apk.part")
                val finalFile = File(folder, "sd-micro-tool-$versionCode.apk")
                val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SDMicroTool-Updater")
                }
                try {
                    require(connection.responseCode in 200..299) {
                        "Tải APK thất bại, HTTP " + connection.responseCode
                    }
                    val contentLength = connection.contentLengthLong
                    require(contentLength <= MAX_APK_BYTES || contentLength < 0L) { "APK vượt quá giới hạn 250 MB" }
                    BufferedInputStream(connection.inputStream).use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            var count: Int
                            while (input.read(buffer).also { count = it } >= 0) {
                                if (count == 0) continue
                                total += count
                                require(total <= MAX_APK_BYTES) { "APK vượt quá giới hạn 250 MB" }
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                require(temporary.length() > 0L) { "Tệp APK tải về rỗng" }
                if (finalFile.exists()) finalFile.delete()
                check(temporary.renameTo(finalFile)) { "Không hoàn tất được tệp APK" }

                verifyDownloadedApk(finalFile, versionCode, manifest)
                downloadedApk = finalFile
            } catch (error: Exception) {
                failure = error
            }

            activity.runOnUiThread {
                progress.dismiss()
                if (failure != null) {
                    downloadedApk?.delete()
                    Toast.makeText(
                        activity,
                        "Không thể cập nhật: " + (failure?.message ?: "APK không hợp lệ"),
                        Toast.LENGTH_LONG
                    ).show()
                } else if (downloadedApk != null) {
                    requestInstallPermissionOrContinue(downloadedApk!!)
                }
            }
        }.start()
    }

    private fun verifyDownloadedApk(apk: File, expectedVersionCode: Long, manifest: JSONObject) {
        @Suppress("DEPRECATION")
        val archiveInfo = activity.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: error("Tệp tải về không phải APK hợp lệ")

        require(archiveInfo.packageName == activity.packageName) {
            "APK không thuộc ứng dụng SD Micro Tool"
        }
        require(packageVersionCode(archiveInfo) >= expectedVersionCode) {
            "Phiên bản APK không khớp version.json"
        }

        val installedInfo = getInstalledPackageInfo()
        val installedCerts = signingCertificateDigests(installedInfo)
        val downloadedCerts = signingCertificateDigests(archiveInfo)
        require(installedCerts.isNotEmpty() && downloadedCerts == installedCerts) {
            "Chữ ký APK không trùng với ứng dụng đang cài. Đã hủy cập nhật để bảo vệ dữ liệu."
        }

        val expectedCertificate = manifest.optString("signingCertificateSha256", "")
            .replace(":", "").lowercase(Locale.ROOT)
        if (expectedCertificate.isNotBlank()) {
            require(downloadedCerts.contains(expectedCertificate)) {
                "Fingerprint chứng chỉ không khớp version.json"
            }
        }

        val expectedApkHash = manifest.optString("apkSha256", "")
            .replace(":", "").lowercase(Locale.ROOT)
        if (expectedApkHash.isNotBlank()) {
            val actualHash = sha256File(apk)
            require(actualHash == expectedApkHash) { "Checksum APK không khớp. Đã hủy cập nhật." }
        }
    }

    private fun requestInstallPermissionOrContinue(apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .edit().putString(PENDING_APK, apk.absolutePath).apply()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.packageName)
            )
            activity.startActivity(intent)
            Toast.makeText(
                activity,
                "Hãy cho phép SD Micro Tool cài đặt bản cập nhật, sau đó quay lại ứng dụng.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        installWithAndroidInstaller(apk)
    }

    private fun installWithAndroidInstaller(apk: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                activity.packageName + ".fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
        } catch (error: Exception) {
            Toast.makeText(
                activity,
                "Không mở được trình cài đặt Android: " + (error.message ?: "lỗi"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfo(): PackageInfo =
        activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )

    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    private fun signingCertificateDigests(info: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT < 28) return emptySet()
        val signingInfo = info.signingInfo ?: return emptySet()
        val signatures = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
