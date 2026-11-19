package com.nero.sdstorage

import android.app.Activity
import android.app.AppOpsManager
import android.app.AlertDialog
import android.app.usage.StorageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors

class SmartGameStorageActivity : Activity() {
    private data class GameSpec(val packageName: String, val name: String)
    private data class GameFile(val uri: Uri, val name: String, val size: Long)
    private data class FolderScan(val bytes: Long, val files: List<GameFile>, val truncated: Boolean)

    private val games = listOf(
        GameSpec("com.roblox.client", "Roblox"),
        GameSpec("com.dts.freefireth", "Free Fire"),
        GameSpec("com.dts.freefiremax", "Free Fire MAX"),
        GameSpec("com.garena.game.kgvn", "Liên Quân Mobile")
    )
    private val prefs by lazy { getSharedPreferences("smart_game_storage", MODE_PRIVATE) }
    private val database by lazy { SmartGameDatabase(applicationContext) }
    private var pendingRestoreHistoryId: Long? = null
    private val storagePrefs by lazy { getSharedPreferences("sd_storage", MODE_PRIVATE) }
    private val blue = Color.rgb(16, 111, 240)
    private val ink = Color.rgb(19, 40, 72)
    private val muted = Color.rgb(103, 122, 151)
    private val green = Color.rgb(21, 176, 119)
    private val orange = Color.rgb(230, 145, 38)
    private val red = Color.rgb(211, 76, 76)
    private val line = Color.rgb(224, 233, 246)
    private lateinit var page: LinearLayout
    private var activeCard: LinearLayout? = null
    private var pendingPackage = ""
    private var activeProgress: ProgressBar? = null
    private var activeStatus: TextView? = null
    private var pauseButton: TextView? = null
    private var transferRunning = false
    private var paused = false
    private val worker = Executors.newSingleThreadExecutor()

    companion object {
        private const val REQUEST_GAME_TREE = 3101
        private const val REQUEST_RESTORE_TREE = 3102
        private const val PREF_SD_TREE = "tree_uri"
        private const val GAME_TREE_PREFIX = "source_tree_"
        private const val ACTION_PROGRESS = "com.nero.sdstorage.GAME_TRANSFER_PROGRESS"
        private const val ACTION_FINISHED = "com.nero.sdstorage.GAME_TRANSFER_FINISHED"
    }

    private val transferReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PROGRESS -> {
                    val percent = intent.getIntExtra("percent", 0)
                    val bytes = intent.getLongExtra("bytes", 0L)
                    val total = intent.getLongExtra("total", 0L)
                    val speed = intent.getLongExtra("speed", 0L)
                    activeProgress?.progress = percent.coerceIn(0, 100)
                    val etaSeconds = if (speed > 0L && total > bytes) (total - bytes) / speed else 0L
                    activeStatus?.text = "Đã chuyển " + formatBytes(bytes) + " / " + formatBytes(total) +
                        " • " + formatBytes(speed) + "/s • Còn khoảng " + etaSeconds + " giây"
                }
                ACTION_FINISHED -> {
                    transferRunning = false
                    paused = false
                    val success = intent.getBooleanExtra("success", false)
                    if (success) {
                        val sourceUri = intent.getStringExtra("source_uri")?.let(Uri::parse)
                        val name = intent.getStringExtra("file_name") ?: "tệp"
                        val size = intent.getLongExtra("bytes", 0L)
                        val hash = intent.getStringExtra("sha256") ?: ""
                        AlertDialog.Builder(this@SmartGameStorageActivity)
                            .setTitle("Đã chuyển và xác minh")
                            .setMessage(
                                name + " (" + formatBytes(size) + ") đã được ghi lên SD và xác minh SHA-256.\n\n" +
                                    "Bản sao giống bản gốc, nhưng game không tự đổi đường dẫn sang SD. Chỉ xóa bản gốc nếu bạn chắc chắn game không còn cần tệp ở vị trí cũ.\n\nSHA-256: " + hash
                            )
                            .setPositiveButton("Giữ bản gốc") { _, _ -> refreshAll() }
                            .setNegativeButton("Xóa bản gốc") { _, _ ->
                                AlertDialog.Builder(this@SmartGameStorageActivity)
                                    .setTitle("Xác nhận xóa bản gốc")
                                    .setMessage("Bạn đã kiểm tra rằng game không còn cần tệp ở đường dẫn cũ và muốn giải phóng dung lượng?")
                                    .setNegativeButton("Không xóa", null)
                                    .setPositiveButton("Xóa tệp gốc") { _, _ ->
                                        val original = sourceUri?.let { DocumentFile.fromSingleUri(this@SmartGameStorageActivity, it) }
                                        val deleted = original?.delete() == true
                                        toast(if (deleted) "Đã xóa bản gốc sau khi bạn xác nhận." else "Không xóa được bản gốc; bản trên SD vẫn được giữ.")
                                        refreshAll()
                                    }.show()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        val error = intent.getStringExtra("error") ?: "Không xác định"
                        AlertDialog.Builder(this@SmartGameStorageActivity)
                            .setTitle("Không hoàn tất chuyển dữ liệu")
                            .setMessage(error + "\n\nBản gốc không bị xóa.")
                            .setPositiveButton("Đóng") { _, _ -> refreshAll() }
                            .show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 74, 177)
        window.navigationBarColor = Color.WHITE
        buildLayout()
        SmartGameWork.schedule(this)
        val filter = IntentFilter().apply {
            addAction(ACTION_PROGRESS)
            addAction(ACTION_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(transferReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(transferReceiver, filter)
        }
        refreshAll()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 248, 255))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(7, 94, 220), Color.rgb(35, 143, 250))
            ).apply { cornerRadius = dp(22).toFloat() }
        }
        header.addView(makeText("‹   Quay lại", 13f, Color.WHITE, true).apply {
            isClickable = true
            setOnClickListener { finish() }
        })
        header.addView(makeText("Smart Game Storage", 22f, Color.WHITE, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        header.addView(makeText("Quản lý tài nguyên game trong phạm vi Android cho phép", 11f, Color.rgb(230, 243, 255)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(12), dp(12), dp(8))
        })
        val scroll = ScrollView(this).apply { isFillViewport = true }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(18))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun refreshAll() {
        page.removeAllViews()
        addCard {
            addView(makeText("GIỚI HẠN TƯƠNG THÍCH", 12f, blue, true))
            addView(makeText(
                "Android 11+ chặn ứng dụng bên ngoài đọc thư mục riêng Android/data và Android/obb của game. SD Micro Tool chỉ quét thư mục bạn chọn và được Android cấp quyền; không root, không tạo đường dẫn giả, không đụng vào dữ liệu riêng.",
                12f, muted
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }

        val sdTree = storagePrefs.getString(PREF_SD_TREE, null)?.let(Uri::parse)
        val sdRoot = sdTree?.let { DocumentFile.fromTreeUri(this, it) }
        addCard {
            addView(makeText("ĐÍCH LƯU TRÊN THẺ SD", 11f, muted, true))
            addView(makeText(sdRoot?.name ?: "Chưa chọn thư mục SD ở Trang chủ", 14f, ink, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            addView(makeText(
                if (sdRoot?.canWrite() == true) "Sẵn sàng nhận tài nguyên được chuyển."
                else "Hãy chọn và cấp quyền thư mục SD ở Trang chủ trước.",
                11f, if (sdRoot?.canWrite() == true) green else orange
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }

        if (!hasUsageAccess()) {
            addCard {
                addView(makeText("THỐNG KÊ DUNG LƯỢNG GAME", 14f, ink, true))
                addView(makeText(
                    "Android yêu cầu quyền Truy cập dữ liệu sử dụng để đọc dung lượng ứng dụng. Quyền này chỉ để thống kê; không mở khóa thư mục riêng của game.",
                    12f, muted
                ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                addButton("Mở cài đặt quyền thống kê", blue, Color.WHITE) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        }

        addSection("Danh sách game", "Thống kê các game đã khai báo; không quét trái phép dữ liệu riêng")
        games.forEach { addGameCard(it, sdTree) }
        addQueueAndHistory()
    }

    private fun addGameCard(game: GameSpec, sdTree: Uri?) {
        val app = try { packageManager.getApplicationInfo(game.packageName, 0) }
        catch (_: PackageManager.NameNotFoundException) { null }
        val name = app?.let { packageManager.getApplicationLabel(it).toString() } ?: game.name
        val sourceTree = prefs.getString(GAME_TREE_PREFIX + game.packageName, null)?.let(Uri::parse)
        if (sourceTree != null && database.getFolders().none { it.packageName == game.packageName }) {
            database.saveFolder(game.packageName, game.name, sourceTree)
        }
        val trackedFolder = database.getFolders().firstOrNull { it.packageName == game.packageName }

        addCard {
            val head = LinearLayout(this@SmartGameStorageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val badge = makeText(name.take(1).uppercase(Locale.getDefault()), 20f, blue, true).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(232, 242, 255), 14)
            }
            head.addView(badge, LinearLayout.LayoutParams(dp(45), dp(45)))
            val title = LinearLayout(this@SmartGameStorageActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
            }
            title.addView(makeText(name, 15f, ink, true))
            title.addView(makeText(if (app != null) "Đã cài trên thiết bị" else "Chưa phát hiện game đã cài",
                10f, if (app != null) green else muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            addView(head)

            if (app != null && hasUsageAccess()) {
                val statsLine = makeText("Đang đọc thống kê…", 11f, muted)
                addView(statsLine, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                worker.execute {
                    val text = readPackageStats(game.packageName, app.storageUuid)
                    runOnUiThread { statsLine.text = text }
                }
            } else if (app != null) {
                addView(makeText("Dung lượng game: cần cấp quyền thống kê.", 11f, orange),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }

            addView(makeText("KHẢ NĂNG CHUYỂN", 10f, muted, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(makeText(
                if (sourceTree != null)
                    "Chuyển thủ công các tệp trong thư mục bạn đã chọn. Dữ liệu riêng của game vẫn do Android bảo vệ; chế độ tự động chuyển không khả dụng."
                else
                    "Chỉ thống kê game; dữ liệu riêng không thể truy cập. Tự động chuyển tài nguyên game chưa được Android cho phép.",
                11f, orange
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })

            addButton(if (sourceTree == null) "Chọn thư mục tài nguyên được phép" else "Đổi thư mục tài nguyên",
                Color.rgb(235, 244, 255), blue) {
                pendingPackage = game.packageName
                chooseGameFolder()
            }
            if (sourceTree != null) {
                val sourceRoot = DocumentFile.fromTreeUri(this@SmartGameStorageActivity, sourceTree)
                addView(makeText("Nguồn: " + (sourceRoot?.name ?: "Không còn quyền truy cập"),
                    11f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                addButton("Quét thư mục và xem tệp", blue, Color.WHITE) {
                    scanGameFolder(game, sourceTree, sdTree)
                }
                val autoEnabled = trackedFolder?.autoCopy == true
                addButton(
                    if (autoEnabled) "Tắt tự động sao chép tài nguyên mới" else "Bật tự động sao chép tài nguyên mới",
                    if (autoEnabled) Color.rgb(255, 244, 224) else Color.rgb(232, 247, 240),
                    if (autoEnabled) orange else green
                ) {
                    val enabled = !autoEnabled
                    database.setAutoCopy(game.packageName, enabled)
                    SmartGameWork.scanNow(this@SmartGameStorageActivity)
                    toast(if (enabled) "Đã bật theo dõi và tự sao chép tệp mới đủ điều kiện. Bản gốc vẫn được giữ." else "Đã tắt tự động sao chép.")
                    refreshAll()
                }
                addView(makeText(
                    if (autoEnabled) "Đang theo dõi định kỳ. Tệp mới đủ điều kiện sẽ được sao chép sang SD; app không xóa bản gốc và không thay đường dẫn game."
                    else "Có thể theo dõi thư mục định kỳ. Chế độ tự động chỉ sao chép tệp mới và giữ nguyên bản gốc.",
                    10f, muted
                ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            }
        }
    }

    private fun scanGameFolder(game: GameSpec, tree: Uri, sdTree: Uri?) {
        pendingPackage = game.packageName
        page.removeAllViews()
        addSection("Tài nguyên được phép truy cập", game.name + " • Thư mục do bạn chọn")
        val status = makeText("Đang quét thư mục…", 12f, muted)
        page.addView(status)
        worker.execute {
            val folder = DocumentFile.fromTreeUri(this, tree)
            val result = if (folder == null) FolderScan(0L, emptyList(), false) else scanFolder(folder)
            runOnUiThread {
                status.text = result.files.size.toString() + " tệp • " + formatBytes(result.bytes) +
                    if (result.truncated) " • Danh sách giới hạn 500 tệp" else ""
                if (result.files.isEmpty()) {
                    addCard { addView(makeText("Không tìm thấy tệp trong thư mục đã chọn.", 12f, muted)) }
                } else {
                    result.files.forEach { file ->
                        addCard {
                            addView(makeText(file.name, 13f, ink, true))
                            addView(makeText(formatBytes(file.size), 10f, muted),
                                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
                            val canMove = sdTree != null && tree != sdTree &&
                                DocumentFile.fromTreeUri(this@SmartGameStorageActivity, sdTree)?.canWrite() == true
                            if (canMove) {
                                addButton("Thêm vào hàng đợi chuyển SD", blue, Color.WHITE) {
                                    confirmTransfer(file, sdTree!!)
                                }
                            } else {
                                addView(makeText("Cần chọn đích SD hợp lệ khác thư mục nguồn.",
                                    10f, orange), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
                            }
                        }
                    }
                }
                addPageButton("Quay lại danh sách game", Color.rgb(235, 244, 255), blue) { refreshAll() }
            }
        }
    }

    private fun confirmTransfer(file: GameFile, sdTree: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Chuyển tệp sang SD?")
            .setMessage(
                "Tệp: " + file.name + "\nDung lượng: " + formatBytes(file.size) +
                    "\n\nỨng dụng sẽ thêm vào hàng đợi, kiểm tra dung lượng SD, sao chép và xác minh SHA-256. Bản gốc luôn được giữ lại; game không tự đổi đường dẫn sang SD."
            )
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Bắt đầu") { _, _ -> startTransfer(file, sdTree) }
            .show()
    }

    private fun startTransfer(file: GameFile, sdTree: Uri) {
        val packageName = pendingPackage.ifBlank { "manual-selected-folder" }
        val id = database.enqueueJob(
            packageName, file.uri, sdTree, file.name, file.size, automatic = false
        )
        if (id <= 0L) {
            toast("Không thể thêm tệp vào hàng đợi.")
            return
        }
        SmartGameWork.runTransferQueue(this)
        toast("Đã thêm " + file.name + " vào hàng đợi. Bản gốc sẽ được giữ lại.")
        addQueueAndHistory()
    }

    private fun addTransferPanel(file: GameFile) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.WHITE, 18, line)
        }
        panel.addView(makeText("ĐANG CHUYỂN TÀI NGUYÊN", 12f, blue, true))
        panel.addView(makeText(file.name, 13f, ink, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        activeStatus = makeText("Đang chuẩn bị…", 11f, muted)
        panel.addView(activeStatus)
        activeProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        panel.addView(activeProgress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pauseButton = makeText("Tạm dừng", 11f, blue, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(235, 244, 255), 12)
            setOnClickListener {
                val action = if (paused) GameTransferService.ACTION_RESUME else GameTransferService.ACTION_PAUSE
                paused = !paused
                text = if (paused) "Tiếp tục" else "Tạm dừng"
                startService(Intent(this@SmartGameStorageActivity, GameTransferService::class.java).setAction(action))
            }
        }
        val cancel = makeText("Hủy", 11f, red, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(255, 239, 239), 12)
            setOnClickListener {
                AlertDialog.Builder(this@SmartGameStorageActivity)
                    .setMessage("Hủy chuyển dữ liệu? Bản gốc sẽ được giữ lại.")
                    .setNegativeButton("Tiếp tục", null)
                    .setPositiveButton("Hủy tác vụ") { _, _ ->
                        startService(Intent(this@SmartGameStorageActivity, GameTransferService::class.java)
                            .setAction(GameTransferService.ACTION_CANCEL))
                    }.show()
            }
        }
        controls.addView(pauseButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(cancel, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        panel.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        page.addView(panel, 0, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun addQueueAndHistory() {
        addSection("Hàng đợi chuyển dữ liệu", "Tác vụ được lưu lại khi đóng ứng dụng")
        val jobs = database.getJobs()
        if (jobs.isEmpty()) {
            addCard { addView(makeText("Không có tác vụ đang chờ.", 12f, muted)) }
        } else {
            jobs.forEach { job ->
                addCard {
                    addView(makeText(job.fileName, 13f, ink, true))
                    val percent = if (job.totalBytes > 0L)
                        ((job.progressBytes * 100L) / job.totalBytes).toInt().coerceIn(0, 100) else 0
                    val statusText = when (job.status) {
                        "QUEUED" -> "Đang chờ"
                        "RUNNING" -> "Đang chuyển"
                        "PAUSED" -> "Đã tạm dừng"
                        "FAILED" -> "Thất bại"
                        else -> job.status
                    }
                    addView(makeText(
                        statusText + " • " + formatBytes(job.progressBytes) + " / " + formatBytes(job.totalBytes),
                        11f, if (job.status == "FAILED") red else muted
                    ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
                    val progress = ProgressBar(this@SmartGameStorageActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        this.progress = percent
                        progressTintList = android.content.res.ColorStateList.valueOf(blue)
                    }
                    addView(progress, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(7) })
                    val speed = job.speedBytesPerSecond
                    if (job.status == "RUNNING" && speed > 0L) {
                        val remaining = (job.totalBytes - job.progressBytes).coerceAtLeast(0L)
                        val eta = remaining / speed
                        addView(makeText(
                            formatBytes(speed) + "/s • Còn khoảng " + eta + " giây",
                            10f, muted
                        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
                    }
                    when (job.status) {
                        "RUNNING" -> {
                            addButton("Tạm dừng", Color.rgb(235, 244, 255), blue) {
                                database.updateJobStatus(job.id, "PAUSED")
                                refreshAll()
                            }
                            addButton("Hủy tác vụ", Color.rgb(255, 239, 239), red) {
                                confirmCancelJob(job)
                            }
                        }
                        "PAUSED" -> {
                            addButton("Tiếp tục", blue, Color.WHITE) {
                                database.updateJobStatus(job.id, "QUEUED")
                                SmartGameWork.restartTransferQueue(this@SmartGameStorageActivity)
                                refreshAll()
                            }
                            addButton("Hủy tác vụ", Color.rgb(255, 239, 239), red) {
                                confirmCancelJob(job)
                            }
                        }
                        "QUEUED" -> addButton("Hủy khỏi hàng đợi", Color.rgb(255, 239, 239), red) {
                            database.cancelQueuedJob(job.id)
                            refreshAll()
                        }
                        "FAILED" -> addButton("Thử lại", blue, Color.WHITE) {
                            database.updateJobStatus(job.id, "QUEUED")
                            database.updateJobProgress(job.id, 0L, 0L)
                            SmartGameWork.restartTransferQueue(this@SmartGameStorageActivity)
                            refreshAll()
                        }
                    }
                    if (job.autoGenerated) {
                        addView(makeText("Tự động sao chép • Bản gốc được giữ", 10f, green),
                            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
                    }
                }
            }
        }

        addSection("Lịch sử chuyển và khôi phục", "Chỉ tính dung lượng giải phóng khi bản gốc đã thực sự bị xóa")
        val history = database.getHistory()
        val freedTotal = history.sumOf { it.freedBytes }
        addCard {
            addView(makeText("ĐÃ GIẢI PHÓNG THỰC TẾ", 10f, muted, true))
            addView(makeText(formatBytes(freedTotal), 24f, green, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        if (history.isEmpty()) {
            addCard { addView(makeText("Chưa có lịch sử chuyển dữ liệu.", 12f, muted)) }
        } else {
            history.forEach { record ->
                addCard {
                    addView(makeText(record.fileName, 13f, ink, true))
                    val status = when (record.status) {
                        "COPIED_VERIFIED" -> "Đã sao chép và xác minh • Bản gốc còn nguyên"
                        "SOURCE_DELETED_BY_USER" -> "Đã xóa bản gốc theo xác nhận"
                        "RESTORED_COPY" -> "Đã tạo bản khôi phục ở thư mục đã chọn"
                        else -> record.status
                    }
                    addView(makeText(status, 10f, if (record.freedBytes > 0L) green else muted),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    addView(makeText(
                        formatBytes(record.bytes) + " • SHA-256: " + record.sha256.take(16) + "…",
                        10f, muted
                    ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    addButton("Khôi phục bản sao vào thư mục được chọn", Color.rgb(235, 244, 255), blue) {
                        pendingRestoreHistoryId = record.id
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            )
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_RESTORE_TREE)
                    }
                    if (record.status == "COPIED_VERIFIED") {
                        addButton("Giải phóng bản gốc (cần xác nhận)", Color.rgb(255, 244, 224), orange) {
                            confirmDeleteOriginal(record)
                        }
                    } else if (record.freedBytes > 0L) {
                        addView(makeText("Dung lượng đã giải phóng: " + formatBytes(record.freedBytes),
                            10f, green), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
                    }
                }
            }
        }
        addPageButton("Làm mới hàng đợi và lịch sử", Color.rgb(235, 244, 255), blue) {
            refreshAll()
        }
    }

    private fun confirmCancelJob(job: GameTransferJob) {
        AlertDialog.Builder(this)
            .setTitle("Hủy tác vụ chuyển?")
            .setMessage("Tệp gốc sẽ được giữ nguyên. Tệp tạm chưa hoàn tất trên SD sẽ bị xóa.")
            .setNegativeButton("Quay lại", null)
            .setPositiveButton("Hủy tác vụ") { _, _ ->
                if (job.status == "QUEUED") database.cancelQueuedJob(job.id)
                else database.updateJobStatus(job.id, "CANCEL_REQUESTED")
                refreshAll()
            }.show()
    }

    private fun confirmDeleteOriginal(record: GameTransferHistory) {
        AlertDialog.Builder(this)
            .setTitle("Xóa bản gốc khỏi thư mục nguồn?")
            .setMessage(
                "Việc này chỉ xóa tệp do bạn chọn qua SAF. Nếu game đang dùng đường dẫn cũ, game có thể không đọc được tệp nữa. " +
                    "Bản sao trên SD đã xác minh SHA-256 nhưng không tự thay đường dẫn game. Bạn có chắc muốn tiếp tục?"
            )
            .setNegativeButton("Giữ bản gốc", null)
            .setPositiveButton("Tiếp tục") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Xác nhận lần cuối")
                    .setMessage("Chỉ xóa khi bạn chắc chắn tệp không còn được game hoặc ứng dụng khác sử dụng.")
                    .setNegativeButton("Không xóa", null)
                    .setPositiveButton("Xóa bản gốc") { _, _ ->
                        val original = DocumentFile.fromSingleUri(this, Uri.parse(record.sourceUri))
                        val size = original?.length()?.coerceAtLeast(0L) ?: record.bytes
                        val deleted = original?.delete() == true
                        if (deleted) {
                            database.markSourceFreed(record.id, size)
                            toast("Đã xóa bản gốc. Dung lượng giải phóng được ghi vào báo cáo.")
                        } else {
                            toast("Android không cho phép xóa tệp nguồn. Bản gốc vẫn được giữ.")
                        }
                        refreshAll()
                    }.show()
            }.show()
    }

    private fun scanFolder(root: DocumentFile): FolderScan {
        val pending = ArrayDeque<Pair<DocumentFile, Int>>()
        pending.add(root to 0)
        val files = mutableListOf<GameFile>()
        var total = 0L
        var truncated = false
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (current.second > 8) continue
            val children = try { current.first.listFiles() } catch (_: Exception) { emptyArray() }
            for (child in children) {
                if (child.isDirectory) pending.add(child to (current.second + 1))
                else {
                    if (files.size >= 500) {
                        truncated = true
                        break
                    }
                    val size = child.length().coerceAtLeast(0L)
                    total += size
                    files.add(GameFile(child.uri, child.name ?: "tệp-không-tên", size))
                }
            }
            if (truncated) break
        }
        return FolderScan(total, files.sortedBy { it.name.lowercase(Locale.ROOT) }, truncated)
    }

    private fun readPackageStats(pkg: String, uuid: java.util.UUID): String {
        return try {
            val manager = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val stats = manager.queryStatsForPackage(uuid, pkg, Process.myUserHandle())
            "Ứng dụng: " + formatBytes(stats.appBytes) +
                " • Dữ liệu: " + formatBytes(stats.dataBytes) +
                " • Cache: " + formatBytes(stats.cacheBytes)
        } catch (error: Exception) {
            "Không đọc được thống kê: " + (error.message ?: "quyền/hệ thống không hỗ trợ")
        }
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun chooseGameFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_GAME_TREE)
    }

    @Deprecated("Uses SAF result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_GAME_TREE && requestCode != REQUEST_RESTORE_TREE) return
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            if (requestCode == REQUEST_RESTORE_TREE) {
                val history = pendingRestoreHistoryId?.let { id -> database.getHistory().firstOrNull { it.id == id } }
                    ?: error("Không tìm thấy bản ghi khôi phục.")
                val id = database.enqueueJob(
                    history.packageName,
                    Uri.parse(history.destinationUri),
                    uri,
                    history.fileName,
                    history.bytes,
                    automatic = false
                )
                pendingRestoreHistoryId = null
                if (id <= 0L) error("Không thể tạo tác vụ khôi phục.")
                SmartGameWork.runTransferQueue(this)
                toast("Đã thêm bản sao vào hàng đợi khôi phục. Tệp gốc hiện tại không bị ghi đè.")
                refreshAll()
                return
            }

            prefs.edit().putString(GAME_TREE_PREFIX + pendingPackage, uri.toString()).apply()
            val game = games.firstOrNull { it.packageName == pendingPackage }
            if (game != null) {
                database.saveFolder(game.packageName, game.name, uri)
                scanGameFolder(game, uri, storagePrefs.getString(PREF_SD_TREE, null)?.let(Uri::parse))
                SmartGameWork.scanNow(this)
            } else {
                refreshAll()
            }
        } catch (error: Exception) {
            toast("Không thể xử lý thư mục: " + (error.message ?: "lỗi"))
        }
    }

    private fun addSection(title: String, subtitle: String) {
        page.addView(makeText(title, 18f, ink, true))
        page.addView(makeText(subtitle, 11f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(10) })
    }

    private fun addCard(content: LinearLayout.() -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.WHITE, 18, line)
            elevation = dp(2).toFloat()
        }
        val previous = activeCard
        activeCard = card
        card.content()
        activeCard = previous
        page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
    }

    private fun addButton(label: String, fill: Int, foreground: Int, action: () -> Unit) {
        val button = makeText(label, 11f, foreground, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(fill, 12)
            isClickable = true
            setOnClickListener { action() }
        }
        (activeCard ?: page).addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
    }

    private fun addPageButton(label: String, fill: Int, foreground: Int, action: () -> Unit) {
        val button = makeText(label, 11f, foreground, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(fill, 12)
            setOnClickListener { action() }
        }
        page.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.coerceAtLeast(0L).toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) { value /= 1024.0; index++ }
        return String.format(Locale.getDefault(), if (index == 0) "%.0f %s" else "%.2f %s", value, units[index])
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        try { unregisterReceiver(transferReceiver) } catch (_: Exception) {}
        worker.shutdown()
        super.onDestroy()
    }
}
