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
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

class GameTransferService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val gate = java.lang.Object()
    @Volatile private var paused = false
    @Volatile private var cancelled = false
    @Volatile private var running = false

    companion object {
        const val ACTION_START = "com.nero.sdstorage.GAME_TRANSFER_START"
        const val ACTION_PAUSE = "com.nero.sdstorage.GAME_TRANSFER_PAUSE"
        const val ACTION_RESUME = "com.nero.sdstorage.GAME_TRANSFER_RESUME"
        const val ACTION_CANCEL = "com.nero.sdstorage.GAME_TRANSFER_CANCEL"
        const val ACTION_PROGRESS = "com.nero.sdstorage.GAME_TRANSFER_PROGRESS"
        const val ACTION_FINISHED = "com.nero.sdstorage.GAME_TRANSFER_FINISHED"
        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_TARGET_TREE = "target_tree"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        private const val CHANNEL_ID = "game_transfer"
        private const val NOTIFICATION_ID = 735
        private const val BUFFER_SIZE = 64 * 1024
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Chuyển tài nguyên game", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Tiến trình chuyển tệp do bạn chọn sang thẻ SD" }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (running) return START_NOT_STICKY
                val source = intent.getStringExtra(EXTRA_SOURCE_URI)?.let(Uri::parse)
                val target = intent.getStringExtra(EXTRA_TARGET_TREE)?.let(Uri::parse)
                val name = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
                val expected = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L)
                if (source == null || target == null || name.isBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                running = true
                paused = false
                cancelled = false
                startForeground(NOTIFICATION_ID, notification("Đang chuẩn bị chuyển: " + name, 0, 0))
                executor.execute { transfer(source, target, name, expected, startId) }
            }
            ACTION_PAUSE -> synchronized(gate) { paused = true }
            ACTION_RESUME -> synchronized(gate) { paused = false; gate.notifyAll() }
            ACTION_CANCEL -> synchronized(gate) { cancelled = true; paused = false; gate.notifyAll() }
        }
        return START_NOT_STICKY
    }

    private fun transfer(sourceUri: Uri, targetTreeUri: Uri, originalName: String, expected: Long, startId: Int) {
        var temporary: DocumentFile? = null
        var success = false
        var errorMessage = "Chuyển dữ liệu thất bại"
        var transferred = 0L
        var verifiedHash = ""
        try {
            val source = DocumentFile.fromSingleUri(this, sourceUri) ?: error("Không mở được tệp nguồn")
            require(source.isFile && source.canRead()) { "Tệp nguồn không thể đọc" }
            val root = DocumentFile.fromTreeUri(this, targetTreeUri) ?: error("Không mở được thư mục SD")
            require(root.canWrite()) { "Thư mục SD không có quyền ghi" }

            val finalName = uniqueName(root, safeName(originalName))
            val temporaryName = ".sdmt-" + System.currentTimeMillis() + "-" + finalName.take(70) + ".part"
            val tempFile = root.createFile(source.type ?: "application/octet-stream", temporaryName)
                ?: error("Không tạo được file tạm trên SD")
            temporary = tempFile

            val digest = MessageDigest.getInstance("SHA-256")
            val total = expected.takeIf { it > 0L } ?: source.length().coerceAtLeast(0L)
            val startTime = System.currentTimeMillis()
            var lastNotify = 0L

            val input = contentResolver.openInputStream(sourceUri) ?: error("Không mở được dữ liệu nguồn")
            val output = contentResolver.openOutputStream(tempFile.uri, "wt") ?: error("Không mở được file đích trên SD")
            input.use { ins ->
                output.use { outs ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        synchronized(gate) {
                            while (paused && !cancelled) gate.wait()
                            if (cancelled) throw InterruptedException("Đã hủy theo yêu cầu")
                        }
                        val count = ins.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        outs.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        transferred += count

                        val now = System.currentTimeMillis()
                        if (now - lastNotify >= 500L) {
                            val elapsed = (now - startTime).coerceAtLeast(1L).toDouble() / 1000.0
                            val speed = (transferred / elapsed).toLong()
                            val percent = if (total > 0L) ((transferred * 100L) / total).toInt().coerceIn(0, 100) else 0
                            broadcastProgress(transferred, total, percent, speed)
                            getSystemService(NotificationManager::class.java).notify(
                                NOTIFICATION_ID, notification("Đang chuyển " + originalName, percent, 100)
                            )
                            lastNotify = now
                        }
                    }
                    outs.flush()
                }
            }

            verifiedHash = digest.digest().toHex()
            if (cancelled) throw InterruptedException("Đã hủy theo yêu cầu")
            val rereadHash = hashDocument(tempFile.uri)
            require(rereadHash == verifiedHash) { "SHA-256 sau khi ghi không khớp. Bản gốc được giữ nguyên." }
            if (cancelled) throw InterruptedException("Đã hủy theo yêu cầu")
            require(tempFile.renameTo(finalName)) {
                "Không đổi tên được file tạm. File .part vẫn trên SD; bản gốc được giữ nguyên."
            }
            success = true
            errorMessage = ""
        } catch (e: InterruptedException) {
            errorMessage = "Tác vụ đã bị hủy. Bản gốc vẫn còn nguyên."
            temporary?.delete()
        } catch (e: Exception) {
            errorMessage = e.message ?: e.javaClass.simpleName
            temporary?.delete()
        } finally {
            running = false
            paused = false
            cancelled = false
            sendBroadcast(Intent(ACTION_FINISHED).apply {
                setPackage(packageName)
                putExtra("success", success)
                putExtra("error", errorMessage)
                putExtra("source_uri", sourceUri.toString())
                putExtra("file_name", originalName)
                putExtra("bytes", transferred)
                putExtra("sha256", verifiedHash)
            })
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                notification(if (success) "Đã chuyển và xác minh: " + originalName
                    else "Không chuyển được: " + originalName, 100, 100, true)
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun hashDocument(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = contentResolver.openInputStream(uri) ?: error("Không đọc lại được tệp đích")
        input.use {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
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

    private fun safeName(value: String): String =
        value.replace("/", "_").replace("\\", "_").replace(":", "_")
            .replace("*", "_").replace("?", "_").replace("\"", "_")
            .replace("<", "_").replace(">", "_").replace("|", "_")
            .trim().trim('.').take(120).ifBlank { "game-resource.bin" }

    private fun broadcastProgress(bytes: Long, total: Long, percent: Int, speed: Long) {
        sendBroadcast(Intent(ACTION_PROGRESS).apply {
            setPackage(packageName)
            putExtra("bytes", bytes)
            putExtra("total", total)
            putExtra("percent", percent)
            putExtra("speed", speed)
        })
    }

    private fun notification(title: String, progress: Int, max: Int, done: Boolean = false): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder.setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Đích lưu: thẻ SD • Bản gốc được giữ tới khi bạn xác nhận")
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
        if (max > 0) builder.setProgress(max, progress, false) else builder.setProgress(0, 0, true)
        return builder.build()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    override fun onBind(intent: Intent?): IBinder? = null
}
