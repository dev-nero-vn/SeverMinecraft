package com.nero.sdstorage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.documentfile.provider.DocumentFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

class DownloadService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var notifications: NotificationManager

    companion object {
        const val ACTION_DOWNLOAD = "com.nero.sdstorage.DOWNLOAD"
        const val EXTRA_URL = "url"
        const val EXTRA_TREE = "tree"
        const val EXTRA_NAME = "name"
        const val ACTION_FINISHED = "com.nero.sdstorage.DOWNLOAD_FINISHED"
        private const val CHANNEL_ID = "sd_downloads"
        private const val NOTIFICATION_ID = 701
    }

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Tải xuống vào SD", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Tiến trình tải tệp trực tiếp vào thẻ SD" }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_DOWNLOAD) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val link = intent.getStringExtra(EXTRA_URL).orEmpty()
        val tree = intent.getStringExtra(EXTRA_TREE).orEmpty()
        val requestedName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        startForeground(NOTIFICATION_ID, buildNotification("Đang chuẩn bị tải vào SD", 0, 0))
        executor.execute {
            var message = "Tải xuống hoàn tất"
            var success = false
            try {
                downloadToSd(link, Uri.parse(tree), requestedName)
                success = true
            } catch (e: Exception) {
                message = "Tải lỗi: " + (e.message ?: e.javaClass.simpleName)
            }
            notifications.notify(NOTIFICATION_ID, buildNotification(message, 100, 100, success))
            sendBroadcast(Intent(ACTION_FINISHED).putExtra("success", success).putExtra("message", message))
            stopForeground(true)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun downloadToSd(link: String, treeUri: Uri, requestedName: String) {
        require(link.startsWith("https://", true)) { "Chỉ hỗ trợ HTTPS" }
        val root = DocumentFile.fromTreeUri(this, treeUri) ?: error("Không mở được thư mục SD")
        require(root.canWrite()) { "Thư mục SD không có quyền ghi" }
        val connection = URL(link).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "SDStorageManager/1.0 Android")
        try {
            connection.connect()
            val code = connection.responseCode
            require(code in 200..299) { "Máy chủ trả mã HTTP " + code }
            val contentType = connection.contentType ?: "application/octet-stream"
            val headerName = connection.getHeaderField("Content-Disposition")
                ?.substringAfter("filename=", "")?.trim('"')?.takeIf { it.isNotBlank() }
            val urlName = link.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
            val name = safeName(requestedName.ifBlank { headerName ?: urlName })
            val finalName = uniqueName(root, name)
            val tempName = "." + finalName + "." + System.currentTimeMillis() + ".part"
            val temp = root.createFile(contentType, tempName) ?: error("Không tạo được file tạm trên SD")
            val total = connection.contentLengthLong
            var downloaded = 0L
            try {
                connection.inputStream.use { input ->
                    val output = contentResolver.openOutputStream(temp.uri, "wt")
                        ?: error("Không mở được file trên SD để ghi")
                    output.use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var lastUpdate = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            downloaded += count
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 900) {
                                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                                val title = "Đang tải: " + formatBytes(downloaded) +
                                    if (total > 0) " / " + formatBytes(total) else ""
                                notifications.notify(NOTIFICATION_ID,
                                    buildNotification(title, percent, if (total > 0) 100 else 0))
                                lastUpdate = now
                            }
                        }
                        out.flush()
                    }
                }
                if (!temp.renameTo(finalName)) {
                    error("Không đổi tên được file tạm; file .part vẫn nằm trên SD")
                }
            } catch (e: Exception) {
                // Keep the partial file on SD so the app never hides where the incomplete data went.
                throw e
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun safeName(value: String): String {
        val cleaned = value.replace("/", "_").replace(":", "_").replace("*", "_")
            .replace("?", "_").replace("\"", "_").replace("<", "_")
            .replace(">", "_").replace("|", "_").trim().trim('.')
        return cleaned.take(120).ifBlank { "download.bin" }
    }

    private fun uniqueName(root: DocumentFile, original: String): String {
        if (root.findFile(original) == null) return original
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var index = 2
        while (root.findFile(base + " (" + index + ")" + ext) != null) index++
        return base + " (" + index + ")" + ext
    }

    private fun formatBytes(value: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = max(0L, value).toDouble()
        var index = 0
        while (size >= 1024 && index < units.lastIndex) { size /= 1024; index++ }
        return String.format(Locale.getDefault(), if (index == 0) "%.0f %s" else "%.1f %s", size, units[index])
    }

    private fun buildNotification(title: String, progress: Int, maxProgress: Int, done: Boolean = false): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder.setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Đích lưu: thẻ SD")
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
        if (maxProgress > 0) builder.setProgress(maxProgress, progress, false) else builder.setProgress(0, 0, true)
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
