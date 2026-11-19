package com.nero.sdstorage

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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

object SmartGameWork {
    private const val MONITOR_WORK = "sd_micro_game_folder_monitor"
    private const val QUEUE_WORK = "sd_micro_game_transfer_queue"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<GameFolderMonitorWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MONITOR_WORK, ExistingPeriodicWorkPolicy.KEEP, request
        )
        scanNow(context)
    }

    fun scanNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sd_micro_game_scan_once", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GameFolderMonitorWorker>().build()
        )
    }

    fun runTransferQueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            QUEUE_WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<GameTransferQueueWorker>().build()
        )
    }

    fun restartTransferQueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            QUEUE_WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GameTransferQueueWorker>().build()
        )
    }
}

class GameFolderMonitorWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    private data class ScannedFile(
        val uri: Uri,
        val name: String,
        val size: Long,
        val modifiedAt: Long,
        val mimeType: String?
    )

    override fun doWork(): Result {
        val db = SmartGameDatabase(applicationContext)
        val targetUri = applicationContext.getSharedPreferences("sd_storage", Context.MODE_PRIVATE)
            .getString("tree_uri", null)?.let(Uri::parse)
        val targetRoot = targetUri?.let { DocumentFile.fromTreeUri(applicationContext, it) }
        var detectedCount = 0
        var queuedCount = 0
        val samples = mutableListOf<String>()

        for (tracked in db.getFolders()) {
            if (isStopped) return Result.retry()
            val root = DocumentFile.fromTreeUri(applicationContext, Uri.parse(tracked.treeUri))
            if (root == null || !root.canRead()) {
                db.updateFolderScanTime(tracked.packageName)
                continue
            }
            val files = scanTree(root)
            for (file in files) {
                if (isStopped) return Result.retry()
                val change = db.recordSnapshot(
                    tracked.packageName, file.uri, file.name,
                    file.size, file.modifiedAt, file.mimeType
                )
                if (change != SnapshotChange.UNCHANGED) {
                    detectedCount++
                    if (samples.size < 4) samples.add(file.name)
                    val eligible = isAutoCopyEligible(file)
                    val enoughSpace = targetUri != null &&
                        freeBytesForTree(targetUri) > file.size + RESERVE_BYTES
                    if (tracked.autoCopy && eligible && targetUri != null &&
                        targetRoot?.canWrite() == true && enoughSpace) {
                        val id = db.enqueueJob(
                            tracked.packageName, file.uri, targetUri,
                            file.name, file.size, automatic = true
                        )
                        if (id > 0L) queuedCount++
                    }
                }
            }
            db.updateFolderScanTime(tracked.packageName)
        }

        if (detectedCount > 0) {
            val title = if (queuedCount > 0)
                "$detectedCount thay đổi mới • $queuedCount tệp đã vào hàng đợi SD"
            else "$detectedCount tệp mới hoặc đã thay đổi"
            postNotification(title, samples.joinToString(", ").ifBlank { "Mở Smart Game Storage để xem." })
        }
        if (queuedCount > 0) SmartGameWork.runTransferQueue(applicationContext)
        return Result.success()
    }

    private fun scanTree(root: DocumentFile): List<ScannedFile> {
        val queue = ArrayDeque<Pair<DocumentFile, Int>>()
        val result = mutableListOf<ScannedFile>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && result.size < MAX_FILES && !isStopped) {
            val pair = queue.removeFirst()
            val folder = pair.first
            val depth = pair.second
            if (depth > MAX_DEPTH) continue
            val children = try { folder.listFiles() } catch (_: Exception) { emptyArray() }
            for (child in children) {
                if (result.size >= MAX_FILES) break
                if (child.isDirectory) {
                    queue.add(child to depth + 1)
                } else if (child.isFile) {
                    val name = child.name ?: continue
                    if (name.startsWith(".sdmt-") || name.endsWith(".part", true) ||
                        name.endsWith(".tmp", true) || name.startsWith(".")) continue
                    result.add(
                        ScannedFile(
                            child.uri, name, child.length().coerceAtLeast(0L),
                            child.lastModified(), child.type
                        )
                    )
                }
            }
        }
        return result
    }

    private fun isAutoCopyEligible(file: ScannedFile): Boolean {
        if (file.size <= 0L || file.size > MAX_AUTO_FILE_BYTES) return false
        val lower = file.name.lowercase()
        return !lower.endsWith(".part") && !lower.endsWith(".tmp") &&
            !lower.endsWith(".lock") && !lower.startsWith(".")
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

    private fun postNotification(title: String, body: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "sd_micro_game_monitor", "Theo dõi tài nguyên game",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, "sd_micro_game_monitor")
            .setSmallIcon(android.R.drawable.stat_notify_sdcard)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        manager.notify(8103, notification)
    }

    companion object {
        private const val MAX_FILES = 1500
        private const val MAX_DEPTH = 6
        private const val MAX_AUTO_FILE_BYTES = 512L * 1024L * 1024L
        private const val RESERVE_BYTES = 16L * 1024L * 1024L
    }
}
