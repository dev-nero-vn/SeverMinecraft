package com.nero.sdstorage

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("sd_storage", Context.MODE_PRIVATE) }
    private var selectedTree: Uri? = null
    private var currentFolder: DocumentFile? = null
    private var currentTab = 0
    private lateinit var page: LinearLayout
    private lateinit var headerSubtitle: TextView
    private lateinit var navItems: List<TextView>
    private var activeCard: LinearLayout? = null

    private val pageBg = Color.rgb(244, 248, 255)
    private val blue = Color.rgb(16, 111, 240)
    private val blueDark = Color.rgb(7, 74, 177)
    private val ink = Color.rgb(19, 40, 72)
    private val muted = Color.rgb(103, 122, 151)
    private val green = Color.rgb(21, 176, 119)
    private val orange = Color.rgb(230, 145, 38)
    private val red = Color.rgb(211, 76, 76)
    private val line = Color.rgb(224, 233, 246)

    companion object {
        private const val REQUEST_SD_TREE = 2101
        private const val REQUEST_NOTIFICATIONS = 2102
        private const val PREF_TREE_URI = "tree_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTree = prefs.getString(PREF_TREE_URI, null)?.let(Uri::parse)
        window.statusBarColor = blueDark
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= 26) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        buildShell()
        showTab(0)
        AppUpdateManager(this).checkForUpdate(showNoUpdate = false)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        if (!hasRemovableSdCard()) {
            page.post {
                AlertDialog.Builder(this)
                    .setTitle("Chưa có thẻ SD")
                    .setMessage("Hãy gắn thẻ nhớ SD vào điện thoại để chọn nơi lưu và sử dụng các chức năng lưu trữ ngoài.")
                    .setPositiveButton("Đã hiểu", null)
                    .show()
            }
        }
    }

    private fun buildShell() {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(7, 94, 220), Color.rgb(35, 143, 250))
            ).apply {
                cornerRadii = floatArrayOf(0f, 0f, dp(24).toFloat(), dp(24).toFloat(),
                    dp(24).toFloat(), dp(24).toFloat(), 0f, 0f)
            }
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.sd_micro_tool_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Logo SD Micro Tool"
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        titleBlock.addView(makeText("SD Micro Tool", 20f, Color.WHITE, true))
        headerSubtitle = makeText("Bộ nhớ phụ thông minh • SD Card", 11f, Color.rgb(226, 240, 255))
        titleBlock.addView(headerSubtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))

        val statusChip = makeText("SD", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(Color.rgb(47, 155, 255), 30, Color.rgb(128, 197, 255))
        }
        header.addView(statusChip)
        shell.addView(header, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(17), dp(16), dp(18))
        }
        scroll.addView(page)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
        }
        val labels = listOf("⌂\\nTổng quan", "↓\\nTải về", "▤\\nTệp SD", "🎮\\nGame", "⚙\\nCài đặt")
        val items = mutableListOf<TextView>()
        labels.forEachIndexed { index, label ->
            val item = makeText(label, 11f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(7), dp(4), dp(7))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    when (index) {
                        3 -> startActivity(Intent(this@MainActivity, SmartGameStorageActivity::class.java))
                        4 -> showTab(3)
                        else -> showTab(index)
                    }
                }
            }
            items.add(item)
            nav.addView(item, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        navItems = items
        shell.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(shell)
    }

    private fun showTab(tab: Int) {
        currentTab = tab.coerceIn(0, 3)
        page.removeAllViews()
        val subtitles = listOf(
            "Quản lý bộ nhớ trong và thẻ SD",
            "Tải tệp trực tiếp vào thẻ nhớ",
            "Duyệt và quản lý tệp đã lưu trên SD",
            "Thiết lập lưu trữ và cập nhật ứng dụng"
        )
        headerSubtitle.text = subtitles[currentTab]
        navItems.forEachIndexed { index, item ->
            val selected = if (currentTab == 3) index == 4 else index == currentTab
            item.setTextColor(if (selected) blue else muted)
            item.background = if (selected) rounded(Color.rgb(232, 242, 255), 14) else null
        }
        when (currentTab) {
            0 -> buildHome()
            1 -> buildDownloadPage()
            2 -> buildFilesPage()
            else -> buildSettingsPage()
        }
    }

    private fun buildHome() {
        addSectionHeading("Không gian lưu trữ", "Theo dõi dung lượng thực tế trên từng bộ nhớ")

        val sd = getSelectedSdStats()
        val sdReady = selectedTree?.let {
            DocumentFile.fromTreeUri(this@MainActivity, it)?.canWrite()
        } == true

        addCard {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(8, 99, 223), Color.rgb(62, 169, 255))
            ).apply { cornerRadius = dp(22).toFloat() }

            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = makeText("SD", 25f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(65, 160, 255), 15, Color.rgb(135, 203, 255))
            }
            top.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))
            val titleBlock = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            titleBlock.addView(makeText("THẺ NHỚ SD", 12f, Color.rgb(226, 241, 255), true))
            titleBlock.addView(makeText(
                if (sdReady) "Đã kết nối và sẵn sàng" else "Chưa chọn thư mục lưu",
                15f, Color.WHITE, true
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            top.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
            val pill = makeText(if (sdReady) "ĐANG DÙNG" else "CHƯA SẴN SÀNG", 9f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(7), dp(8), dp(7))
                background = rounded(Color.argb(60, 255, 255, 255), 30)
            }
            top.addView(pill)
            addView(top)

            if (sd != null && sd[0] > 0) {
                val total = sd[0]
                val free = sd[1]
                val used = (total - free).coerceAtLeast(0)
                addView(makeText(formatBytes(free), 31f, Color.WHITE, true),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
                addView(makeText("CÒN TRỐNG TRÊN THẺ SD", 10f, Color.rgb(226, 241, 255), true),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
                val bar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = percentage(used, total)
                    progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(75, 255, 255, 255))
                }
                addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(14) })
                addView(makeText(formatBytes(used) + " đã dùng  •  " + formatBytes(total) + " tổng dung lượng",
                    11f, Color.rgb(230, 243, 255)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
                if (free < 256L * 1024L * 1024L) {
                    addView(makeText("Thẻ SD sắp hết dung lượng. Hãy giải phóng bớt tệp trước khi tải tiếp.",
                        11f, Color.WHITE, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                }
            } else {
                addView(makeText("Chọn thẻ SD để bắt đầu", 21f, Color.WHITE, true),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
                addView(makeText("Dữ liệu tải xuống sẽ được ghi trực tiếp vào thư mục SD bạn cấp quyền.",
                    12f, Color.rgb(230, 243, 255)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
            addActionButton(if (sdReady) "Mở khu vực tải xuống" else "Chọn thẻ SD ngay", Color.WHITE, blue) {
                if (sdReady) showTab(1) else chooseSdFolder()
            }
        }

        addSectionHeading("Bộ nhớ trên điện thoại", "Hai vùng lưu trữ được hiển thị riêng biệt")
        addStorageCard("Bộ nhớ trong", getInternalStats(), blue, "Android, ứng dụng và dữ liệu hệ thống", "📱")
        addStorageCard("Thẻ SD", sd, green, "Thư mục lưu trữ ngoài đã được cấp quyền", "💾")

        addSectionHeading("Truy cập nhanh", "Các thao tác thường dùng")
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(quickAction("↓", "Tải vào SD", "Ghi tệp trực tiếp", blue) { showTab(1) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        quick.addView(quickAction("▤", "Quản lý tệp", "Duyệt thẻ nhớ", green) { showTab(2) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        page.addView(quick, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        addCard {
            addView(makeText("SMART GAME STORAGE", 13f, blue, true))
            addView(makeText("Thống kê Roblox, Free Fire, Liên Quân và chuyển thủ công những tệp trong thư mục Android cho phép truy cập.",
                11f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            addActionButton("MỞ QUẢN LÝ TÀI NGUYÊN GAME", blue, Color.WHITE) {
                startActivity(Intent(this@MainActivity, SmartGameStorageActivity::class.java))
            }
        }

        addCard {
            addView(makeText("LƯU Ý VỀ ỨNG DỤNG VÀ GAME", 13f, ink, true))
            addView(makeText(
                "SD Micro Tool có thể ghi tài nguyên do chính ứng dụng tải vào SD. Android vẫn quản lý dữ liệu riêng của Roblox, Free Fire, Liên Quân và các ứng dụng khác; không thể tự ép chúng chuyển cache nếu game hoặc hệ thống không hỗ trợ.",
                12f, muted
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
    }

    private fun addStorageCard(title: String, stats: LongArray?, tint: Int, description: String, iconText: String) {
        addCard {
            val head = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = makeText(iconText, 20f, tint, true).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(239, 246, 255), 13)
            }
            head.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)))
            val block = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
            }
            block.addView(makeText(title, 15f, ink, true))
            block.addView(makeText(description, 10f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            head.addView(block, LinearLayout.LayoutParams(0, -2, 1f))
            addView(head)

            if (stats == null || stats.size < 2 || stats[0] <= 0L) {
                addView(makeText("Chưa đọc được dung lượng. Hãy kiểm tra thẻ và quyền truy cập.",
                    12f, orange, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
            } else {
                val total = stats[0]
                val free = stats[1]
                val used = (total - free).coerceAtLeast(0L)
                addView(makeText(formatBytes(used) + " / " + formatBytes(total), 19f, ink, true),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
                val bar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = percentage(used, total)
                    progressTintList = android.content.res.ColorStateList.valueOf(tint)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(232, 238, 248))
                }
                addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(9) })
                addView(makeText("Còn trống: " + formatBytes(free), 11f, muted),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            }
        }
    }

    private fun buildDownloadPage() {
        addSectionHeading("Tải trực tiếp vào SD", "Không tải hoàn chỉnh vào bộ nhớ trong rồi sao chép")

        val folder = selectedTree?.let { DocumentFile.fromTreeUri(this@MainActivity, it) }
        addCard {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val folderIcon = makeText("▣", 24f, blue, true).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(233, 243, 255), 14)
            }
            row.addView(folderIcon, LinearLayout.LayoutParams(dp(48), dp(48)))
            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), 0, 0, 0)
            }
            details.addView(makeText("THƯ MỤC NHẬN", 10f, muted, true))
            details.addView(makeText(folder?.name ?: "Chưa chọn thư mục SD", 14f, ink, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            addView(row)
            addActionButton("Chọn / đổi thư mục SD", Color.rgb(235, 244, 255), blue) { chooseSdFolder() }
        }

        val urlField = createField("Dán liên kết HTTPS của tệp", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val nameField = createField("Tên tệp (để trống để tự nhận diện)", InputType.TYPE_CLASS_TEXT)

        addCard {
            addView(makeText("LIÊN KẾT TẢI", 10f, muted, true))
            addView(urlField, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7) })
            addView(makeText("TÊN TỆP", 10f, muted, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(15) })
            addView(nameField, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7) })
            addActionButton("↓  TẢI THẲNG VÀO THẺ SD", blue, Color.WHITE) {
                val link = urlField.text.toString().trim()
                val fileName = nameField.text.toString().trim()
                when {
                    selectedTree == null -> {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Chưa chọn thẻ SD")
                            .setMessage("Chọn thư mục trên thẻ SD trước khi tải.")
                            .setPositiveButton("Chọn SD") { _, _ -> chooseSdFolder() }
                            .setNegativeButton("Đóng", null)
                            .show()
                    }
                    !link.startsWith("https://", true) -> toast("Chỉ hỗ trợ liên kết HTTPS an toàn.")
                    else -> {
                        val stats = getSelectedSdStats()
                        if (stats != null && stats[1] < 10L * 1024L * 1024L) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Thẻ SD không đủ dung lượng")
                                .setMessage("Thẻ SD còn " + formatBytes(stats[1]) + ". Hãy giải phóng dung lượng trước khi tải tiếp. Tệp sẽ không tự chuyển sang bộ nhớ trong.")
                                .setPositiveButton("Đóng", null)
                                .show()
                        } else {
                            val intent = Intent(this@MainActivity, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_DOWNLOAD
                                putExtra(DownloadService.EXTRA_URL, link)
                                putExtra(DownloadService.EXTRA_TREE, selectedTree.toString())
                                putExtra(DownloadService.EXTRA_NAME, fileName)
                            }
                            try {
                                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                                toast("Đã bắt đầu tải. File tạm được ghi trên thẻ SD.")
                                urlField.text.clear()
                                nameField.text.clear()
                            } catch (e: Exception) {
                                toast("Không thể bắt đầu tải: " + (e.message ?: "lỗi hệ thống"))
                            }
                        }
                    }
                }
            }
        }

        addCard {
            addView(makeText("CÁCH HOẠT ĐỘNG", 13f, ink, true))
            addInfoRow("1", "Tệp tạm .part được tạo trực tiếp trên SD.")
            addInfoRow("2", "Dữ liệu tải được ghi theo luồng, không tạo bản tải hoàn chỉnh trong bộ nhớ trong.")
            addInfoRow("3", "Khi hoàn tất, ứng dụng đổi tên tệp ngay trên SD.")
            addInfoRow("4", "Nếu thẻ đầy hoặc bị rút, tải sẽ báo lỗi; file tạm vẫn nằm trên SD nếu thẻ còn truy cập được.")
        }
    }

    private fun buildFilesPage() {
        addSectionHeading("Tệp trên thẻ SD", "Chỉ duyệt thư mục bạn đã chủ động cấp quyền")
        val root = selectedTree?.let { DocumentFile.fromTreeUri(this@MainActivity, it) }
        if (root == null) {
            addCard {
                addView(makeText("Chưa chọn thư mục thẻ SD", 16f, ink, true))
                addView(makeText("Chọn thư mục trên SD để xem và quản lý các tệp do bạn lưu tại đó.",
                    12f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                addActionButton("Chọn thư mục SD", blue, Color.WHITE) { chooseSdFolder() }
            }
            return
        }
        if (currentFolder == null) currentFolder = root
        addCard {
            addView(makeText("VỊ TRÍ ĐANG MỞ", 10f, muted, true))
            addView(makeText(currentFolder?.name ?: root.name ?: "Thẻ SD", 15f, ink, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            addActionButton(if (currentFolder == root) "Làm mới thư mục" else "Quay về thư mục gốc", Color.rgb(235, 244, 255), blue) {
                currentFolder = root
                showTab(2)
            }
        }

        val items = currentFolder?.listFiles()?.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }
                .thenBy { it.name?.lowercase(Locale.ROOT) }
        ) ?: emptyList()

        if (items.isEmpty()) {
            addCard {
                addView(makeText("Thư mục đang trống", 15f, ink, true))
                addView(makeText("Tệp tải về sẽ xuất hiện tại đây.", 12f, muted),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            }
        } else {
            items.forEach { item ->
                addCard {
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val icon = makeText(if (item.isDirectory) "📁" else "📄", 20f, blue, true).apply {
                        gravity = Gravity.CENTER
                        background = rounded(Color.rgb(239, 246, 255), 12)
                    }
                    row.addView(icon, LinearLayout.LayoutParams(dp(43), dp(43)))
                    val details = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(10), 0, 0, 0)
                    }
                    details.addView(makeText(item.name ?: "Không tên", 13f, ink, true))
                    details.addView(makeText(
                        if (item.isDirectory) "Thư mục" else formatBytes(item.length()),
                        10f, muted
                    ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(row)
                    if (item.isDirectory) {
                        addActionButton("Mở thư mục", Color.rgb(235, 244, 255), blue) {
                            currentFolder = item
                            showTab(2)
                        }
                    } else {
                        addActionButton("Xóa tệp này", Color.rgb(255, 239, 239), red) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Xóa tệp khỏi SD?")
                                .setMessage("Bạn có chắc muốn xóa " + (item.name ?: "tệp này") + " không?")
                                .setNegativeButton("Hủy", null)
                                .setPositiveButton("Xóa") { _, _ ->
                                    val ok = item.delete()
                                    toast(if (ok) "Đã xóa tệp khỏi SD." else "Không thể xóa tệp.")
                                    showTab(2)
                                }.show()
                        }
                    }
                }
            }
        }
    }

    private fun buildSettingsPage() {
        addSectionHeading("Cài đặt ứng dụng", "Quản lý thẻ nhớ, quyền truy cập và cập nhật")
        val selectedFolder = selectedTree?.let { DocumentFile.fromTreeUri(this@MainActivity, it)?.name }
        addCard {
            addView(makeText("LƯU TRỮ MẶC ĐỊNH", 10f, muted, true))
            addView(makeText(selectedFolder ?: "Chưa chọn thẻ SD", 15f, ink, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            addActionButton("Chọn / đổi thư mục SD", blue, Color.WHITE) { chooseSdFolder() }
            addActionButton("Gỡ quyền thư mục SD", Color.rgb(255, 239, 239), red) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Gỡ quyền thư mục?")
                    .setMessage("Ứng dụng sẽ không thể lưu vào thư mục SD hiện tại cho tới khi bạn chọn lại.")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Gỡ quyền") { _, _ ->
                        selectedTree?.let { uri ->
                            try {
                                contentResolver.releasePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                )
                            } catch (_: Exception) {}
                        }
                        prefs.edit().remove(PREF_TREE_URI).apply()
                        selectedTree = null
                        currentFolder = null
                        showTab(3)
                    }.show()
            }
        }

        addCard {
            addView(makeText("CẬP NHẬT SD MICRO TOOL", 14f, ink, true))
            addView(makeText("Kiểm tra phiên bản mới từ GitHub Releases. Trước khi mở trình cài đặt, ứng dụng kiểm tra package và chữ ký APK.",
                12f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            addActionButton("Kiểm tra cập nhật", blue, Color.WHITE) {
                AppUpdateManager(this@MainActivity).checkForUpdate(showNoUpdate = true)
            }
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "Không rõ"
            } catch (_: Exception) { "Không rõ" }
            addView(makeText("Phiên bản hiện tại: " + versionName, 11f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

        addCard {
            addView(makeText("QUYỀN RIÊNG TƯ & AN TOÀN", 14f, ink, true))
            addView(makeText("Ứng dụng chỉ truy cập thư mục bạn cấp quyền. Không tự ý đọc dữ liệu riêng của game hoặc ứng dụng khác. Chức năng đồng bộ đám mây tạm thời sẽ chỉ hoạt động khi máy chủ trung gian được cấu hình an toàn.",
                12f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }

        addCard {
            addView(makeText("SD MICRO TOOL", 13f, blue, true))
            addView(makeText("Phát triển bởi Dev Nero\nGiao diện tiếng Việt • Android 11 trở lên",
                11f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }
    }

    private fun createField(hintText: String, inputTypeValue: Int): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.rgb(139, 155, 179))
            setTextColor(ink)
            textSize = 13f
            setSingleLine(true)
            inputType = inputTypeValue
            background = rounded(Color.rgb(247, 250, 255), 13, line)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
    }

    private fun addSectionHeading(title: String, subtitle: String) {
        page.addView(makeText(title, 20f, ink, true))
        page.addView(makeText(subtitle, 11f, muted),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
                bottomMargin = dp(13)
            })
    }

    private fun addCard(content: LinearLayout.() -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(Color.WHITE, 19, line)
            elevation = dp(2).toFloat()
        }
        val previous = activeCard
        activeCard = card
        card.content()
        activeCard = previous
        page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun addActionButton(label: String, fillColor: Int, labelColor: Int, action: () -> Unit) {
        val button = makeText(label, 12f, labelColor, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(fillColor, 13, if (fillColor == Color.WHITE) Color.rgb(215, 231, 255) else null)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        (activeCard ?: page).addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun addInfoRow(number: String, message: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(7), 0, dp(7))
        }
        row.addView(makeText(number, 11f, blue, true), LinearLayout.LayoutParams(dp(23), -2))
        row.addView(makeText(message, 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        activeCard?.addView(row)
    }

    private fun quickAction(iconText: String, title: String, subtitle: String, tint: Int, action: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = rounded(Color.WHITE, 17, line)
            elevation = dp(1).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(makeText(iconText, 25f, tint, true))
            addView(makeText(title, 13f, ink, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            addView(makeText(subtitle, 10f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
        }
    }

    private fun chooseSdFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_SD_TREE)
    }

    @Deprecated("Uses SAF result callback for Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SD_TREE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (!isRemovableSdTree(uri)) {
            toast("Hãy chọn thư mục trên thẻ SD, không phải bộ nhớ trong.")
            return
        }
        try {
            val flags = data.flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            contentResolver.takePersistableUriPermission(uri, flags)
            selectedTree = uri
            currentFolder = DocumentFile.fromTreeUri(this, uri)
            prefs.edit().putString(PREF_TREE_URI, uri.toString()).apply()
            toast("Đã chọn thẻ SD làm nơi lưu.")
            showTab(currentTab)
        } catch (e: Exception) {
            toast("Không thể cấp quyền thư mục: " + (e.message ?: "lỗi"))
        }
    }

    private fun isRemovableSdTree(uri: Uri): Boolean {
        return try {
            val volumeId = DocumentsContract.getTreeDocumentId(uri).substringBefore(':')
            if (volumeId.equals("primary", true)) return false
            val manager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
            manager.storageVolumes.any {
                it.isRemovable && it.uuid?.equals(volumeId, true) == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasRemovableSdCard(): Boolean {
        return try {
            val manager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
            manager.storageVolumes.any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        } catch (_: Exception) {
            false
        }
    }

    private fun getInternalStats(): LongArray? {
        return try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            longArrayOf(stat.totalBytes, stat.availableBytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun getSelectedSdStats(): LongArray? {
        val uri = selectedTree ?: return null
        return try {
            val volumeId = DocumentsContract.getTreeDocumentId(uri).substringBefore(':')
            val manager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volume = manager.storageVolumes.firstOrNull {
                it.isRemovable && it.uuid?.equals(volumeId, true) == true
            } ?: return null
            val directory = volume.directory ?: return null
            val stat = StatFs(directory.absolutePath)
            longArrayOf(stat.totalBytes, stat.availableBytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun percentage(used: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((used.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format(
            Locale.getDefault(),
            if (index == 0) "%.0f %s" else "%.2f %s",
            value,
            units[index]
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        AppUpdateManager(this).resumePendingInstall()
        if (::page.isInitialized && currentTab == 0) showTab(0)
    }
}
