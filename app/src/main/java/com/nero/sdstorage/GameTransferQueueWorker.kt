package com.nero.sdstorage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max

class GameTransferQueueWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    private val db = SmartGameDatabase(applicationContext)
    private val channelId = "sd_micro_game_transfer"
    private val notificationId = 8104

    override fun doWork(): Result {
        createChannel()
        return try {
            setForegroundAsync(ForegroundInfo(notificationId, notification("Đang chuẩn bị hàng đợi", 0, false))).get()
            while (!isStopped) {
                val job = db.getNextQueuedJob() ?: return Result.success()
                db.updateJobStatus(job.id, "RUNNING")
                try {
                    transfer(job)
                } catch (_: TransferCancelled) {
                    db.failJob(job.id, "Đã hủy theo yêu cầu. Bản gốc không bị thay đổi.", "CANCELLED")
                } catch (error: Exception) {
                    db.failJob(job.id, error.message ?: "Lỗi không xác định")
                    updateNotification("Không chuyển được: " + job.fileName, 0, true)
                }
            }
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun transfer(job: GameTransferJob) {
        val sourceUri = Uri.parse(job.sourceUri)
        val targetUri = Uri.parse(job.targetTreeUri)
        val source = DocumentFile.fromSingleUri(applicationContext, sourceUri)
            ?: error("Không mở được tệp nguồn.")
        require(source.isFile && source.canRead()) { "Tệp nguồn không thể đọc." }
        val root = DocumentFile.fromTreeUri(applicationContext, targetUri)
            ?: error("Không mở được thư mục SD đích.")
        require(root.canWrite()) { "Thư mục SD không có quyền ghi." }

        val total = source.length().coerceAtLeast(job.totalBytes).coerceAtLeast(0L)
        val free = freeBytesForTree(targetUri)
        require(free > 0L) { "Không xác định được dung lượng trống SD hoặc thẻ chưa sẵn sàng." }
        require(total + RESERVE_BYTES < free) {
            "Thẻ SD không đủ dung lượng. Còn " + formatBytes(free) +
                "; cần khoảng " + formatBytes(total + RESERVE_BYTES) + "."
        }

        val finalName = uniqueName(root, safeName(job.fileName))
        val tempName = ".sdmt-transfer-" + job.id + ".part"
        root.findFile(tempName)?.delete()
        val temp = root.createFile(source.type ?: "application/octet-stream", tempName)
            ?: error("Không tạo được tệp tạm trên SD.")

        var copied = 0L
        val startedAt = System.currentTimeMillis()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val input = applicationContext.contentResolver.openInputStream(sourceUri)
                ?: error("Không mở được dữ liệu nguồn.")
            val output = applicationContext.contentResolver.openOutputStream(temp.uri, "wt")
                ?: error("Không mở được tệp đích trên SD.")
            input.use { ins ->
                output.use { outs ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastUpdate = 0L
                    while (true) {
                        checkJobState(job.id)
                        val count = ins.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        outs.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copied += count
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 700L) {
                            val elapsed = (now - startedAt).coerceAtLeast(1L)
                            val speed = (copied * 1000L / elapsed).coerceAtLeast(1L)
                            db.updateJobProgress(job.id, copied, speed)
                            val percent = if (total > 0L) ((copied * 100L) / total).toInt().coerceIn(0, 99) else 0
                            val title = job.fileName + ": " + formatBytes(copied) + " / " + formatBytes(total)
                            updateNotification(title, percent, false)
                            lastUpdate = now
                        }
                    }
                    outs.flush()
                }
            }

            checkJobState(job.id)
            val sourceHash = digest.digest().toHex()
            val destinationHash = hashDocument(temp.uri)
            require(sourceHash == destinationHash) {
                "SHA-256 không khớp sau khi ghi. Bản gốc được giữ nguyên."
            }
            checkJobState(job.id)
            require(temp.renameTo(finalName)) {
                "Không đổi được tên tệp tạm trên SD. Bản gốc được giữ nguyên."
            }
            val destination = root.findFile(finalName) ?: error("Không tìm thấy tệp sau khi đổi tên.")
            db.finishJob(job, destination.uri, sourceHash)
            updateNotification("Đã chuyển và xác minh: " + job.fileName, 100, true)
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun checkJobState(jobId: Long) {
        while (!isStopped) {
            val job = db.getJobs().firstOrNull { it.id == jobId }
            when (job?.status) {
                "PAUSED" -> {
                    updateNotification("Đang tạm dừng: " + job.fileName, 0, false)
                    Thread.sleep(500L)
                }
                "CANCEL_REQUESTED" -> throw TransferCancelled()
                "RUNNING" -> return
                else -> throw TransferCancelled()
            }
        }
        throw TransferCancelled()
    }

    private fun freeBytesForTree(tree: Uri): Long {
        return try {
            val volumeId = DocumentsContract.getTreeDocumentId(tree).substringBefore(':')
            val manager = applicationContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volume = manager.storageVolumes.firstOrNull {
                it.isRemovable && it.uuid?.equals(volumeId, true) == true
            } ?: return 0L
            val directory = volume.directory ?: return 0L
            StatFs(directory.absolutePath).availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    private fun hashDocument(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = applicationContext.contentResolver.openInputStream(uri)
            ?: error("Không đọc lại được tệp trên SD để xác minh.")
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
        while (root.findFile("$base ($index)$ext") != null) index++
        return "$base ($index)$ext"
    }

    private fun safeName(value: String): String =
        value.replace("/", "_").replace("\\", "_").replace(":", "_")
            .replace("*", "_").replace("?", "_").replace("\"", "_")
            .replace("<", "_").replace(">", "_").replace("|", "_")
            .trim().trim('.').take(120).ifBlank { "game-resource.bin" }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Hàng đợi chuyển tài nguyên game", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(title: String, progress: Int, done: Boolean): Notification =
        NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Sao chép sang SD • Không tự xóa bản gốc")
            .setOngoing(!done)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .build()

    private fun updateNotification(title: String, progress: Int, done: Boolean) {
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification(title, progress, done))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun formatBytes(value: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = max(0L, value).toDouble()
        var index = 0
        while (size >= 1024.0 && index < units.lastIndex) { size /= 1024.0; index++ }
        return String.format(Locale.getDefault(), if (index == 0) "%.0f %s" else "%.1f %s", size, units[index])
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val RESERVE_BYTES = 16L * 1024L * 1024L
    }

    private class TransferCancelled : Exception()
}
