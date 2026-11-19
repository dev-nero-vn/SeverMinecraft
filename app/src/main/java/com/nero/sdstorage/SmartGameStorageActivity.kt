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
    }

    private fun addGameCard(game: GameSpec, sdTree: Uri?) {
        val app = try { packageManager.getApplicationInfo(game.packageName, 0) }
        catch (_: PackageManager.NameNotFoundException) { null }
        val name = app?.let { packageManager.getApplicationLabel(it).toString() } ?: game.name
        val sourceTree = prefs.getString(GAME_TREE_PREFIX + game.packageName, null)?.let(Uri::parse)

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
            }
        }
    }

    private fun scanGameFolder(game: GameSpec, tree: Uri, sdTree: Uri?) {
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
                                addButton("Chuyển tệp sang SD", blue, Color.WHITE) {
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
                    "\n\nỨng dụng sẽ sao chép lên SD và xác minh SHA-256. Chỉ sau đó bạn mới được chọn xóa bản gốc."
            )
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Bắt đầu") { _, _ -> startTransfer(file, sdTree) }
            .show()
    }

    private fun startTransfer(file: GameFile, sdTree: Uri) {
        if (transferRunning) {
            toast("Đang có một tác vụ chuyển dữ liệu.")
            return
        }
        transferRunning = true
        paused = false
        addTransferPanel(file)
        val intent = Intent(this, GameTransferService::class.java).apply {
            action = GameTransferService.ACTION_START
            putExtra(GameTransferService.EXTRA_SOURCE_URI, file.uri.toString())
            putExtra(GameTransferService.EXTRA_TARGET_TREE, sdTree.toString())
            putExtra(GameTransferService.EXTRA_FILE_NAME, file.name)
            putExtra(GameTransferService.EXTRA_TOTAL_BYTES, file.size)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } catch (error: Exception) {
            transferRunning = false
            toast("Không khởi chạy được tác vụ: " + (error.message ?: "lỗi"))
        }
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
        if (requestCode != REQUEST_GAME_TREE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            prefs.edit().putString(GAME_TREE_PREFIX + pendingPackage, uri.toString()).apply()
            val game = games.firstOrNull { it.packageName == pendingPackage }
            val sdTree = storagePrefs.getString(PREF_SD_TREE, null)?.let(Uri::parse)
            if (game != null) scanGameFolder(game, uri, sdTree) else refreshAll()
        } catch (error: Exception) {
            toast("Không thể cấp quyền thư mục: " + (error.message ?: "lỗi"))
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
