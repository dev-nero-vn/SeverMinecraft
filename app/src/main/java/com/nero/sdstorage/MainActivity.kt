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
import android.view.Gravity
import android.widget.EditText
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
    private lateinit var headerTitle: TextView
    private lateinit var headerSubtitle: TextView
    private val tabButtons = mutableListOf<TextView>()
    private val dark = Color.rgb(8, 18, 38)
    private val panel = Color.rgb(19, 35, 62)
    private val muted = Color.rgb(169, 187, 213)
    private val blue = Color.rgb(20, 112, 245)
    private val green = Color.rgb(38, 198, 139)
    private var activeCard: LinearLayout? = null

    companion object {
        private const val REQUEST_SD_TREE = 2101
        private const val REQUEST_NOTIFICATIONS = 2102
        private const val PREF_TREE_URI = "tree_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTree = prefs.getString(PREF_TREE_URI, null)?.let(Uri::parse)
        buildShell()
        showTab(0)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun buildShell() {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(5, 91, 218), Color.rgb(28, 131, 250))
            ).apply {
                setCornerRadii(floatArrayOf(0f, 0f, dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f))
            }
        }
        headerTitle = makeText("SD Storage Manager", 23f, Color.WHITE, true)
        headerSubtitle = makeText("Ổ lưu trữ phụ • Tải thẳng vào SD", 12f, Color.rgb(225, 239, 255))
        top.addView(headerTitle)
        top.addView(headerSubtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        shell.addView(top, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(20))
        }
        scroll.addView(page)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(7), dp(6), dp(7))
            setBackgroundColor(Color.rgb(13, 27, 49))
        }
        val labels = listOf("⌂\nTrang chủ", "↓\nTải về", "▤\nTệp", "⚙\nCài đặt")
        labels.forEachIndexed { index, label ->
            val item = makeText(label, 11f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                isClickable = true
                isFocusable = true
                setOnClickListener { showTab(index) }
            }
            tabButtons.add(item)
            nav.addView(item, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        shell.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(shell)
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        page.removeAllViews()
        val titles = listOf("Tổng quan bộ nhớ", "Tải trực tiếp vào SD", "Quản lý tệp trên SD", "Cài đặt & quyền")
        val subtitles = listOf(
            "Theo dõi riêng bộ nhớ trong và thẻ SD",
            "Tệp tải và file tạm được ghi vào SD",
            "Duyệt đúng thư mục thẻ nhớ đã cấp quyền",
            "Quản lý nơi lưu trữ và thông tin ứng dụng"
        )
        headerTitle.text = titles[tab]
        headerSubtitle.text = subtitles[tab]
        tabButtons.forEachIndexed { index, view ->
            view.setTextColor(if (index == tab) Color.WHITE else muted)
            view.background = if (index == tab) rounded(blue, 14) else null
        }
        when (tab) {
            0 -> buildHome()
            1 -> buildDownloadPage()
            2 -> buildFilesPage()
            else -> buildSettingsPage()
        }
    }

    private fun buildHome() {
        sectionTitle("Bộ nhớ thiết bị", "Số liệu hệ thống, không gộp hai ổ")
        addStorageCard("Bộ nhớ trong", getInternalStats(), Color.rgb(48, 139, 255),
            "Android, ứng dụng đã cài và dữ liệu hệ thống")
        addStorageCard("Thẻ SD", getSelectedSdStats(), green,
            if (selectedTree == null) "Chưa cấp quyền thư mục SD" else "Ổ lưu trữ phụ đã chọn")

        addCard {
            addView(makeText("LƯU TRỮ TRỰC TIẾP", 12f, green, true))
            val ready = selectedTree?.let { DocumentFile.fromTreeUri(this@MainActivity, it)?.canWrite() } == true
            addView(makeText(
                if (ready) "Thư mục SD đã sẵn sàng nhận tệp tải xuống." else "Chọn một thư mục trên thẻ SD để bắt đầu lưu trực tiếp.",
                15f, Color.WHITE, true
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(makeText("Ứng dụng tạo file tạm ngay trong thư mục SD rồi đổi tên sau khi tải xong. Không tải file hoàn chỉnh vào bộ nhớ trong để sao chép sang SD.", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            addActionButton(if (ready) "Mở tải xuống" else "Chọn thư mục thẻ SD", blue) {
                if (ready) showTab(1) else chooseSdFolder()
            }
        }
        addCard {
            addView(makeText("ỨNG DỤNG & GAME", 15f, Color.WHITE, true))
            addView(makeText("Game vẫn cài và chạy theo Android. Ứng dụng này không thể tự chuyển cache riêng của Roblox hoặc dữ liệu riêng của game khác nếu Android/game không cấp quyền.", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
        addCard {
            addView(makeText("NGUYÊN TẮC DUNG LƯỢNG", 15f, Color.WHITE, true))
            addView(makeText("APK của SD Storage Manager vẫn chiếm một ít bộ nhớ trong sau khi cài. Tệp tải xuống được ghi lên SD sẽ được tính vào thẻ SD, không được báo là dung lượng trong đã giảm.", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
    }

    private fun addStorageCard(title: String, stats: LongArray?, tint: Int, description: String) {
        addCard {
            addView(makeText(title, 15f, Color.WHITE, true))
            addView(makeText(description, 11f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            if (stats == null || stats.size < 2 || stats[0] <= 0L) {
                addView(makeText("Chưa đọc được dung lượng. Kiểm tra thẻ và quyền truy cập.", 13f, Color.rgb(255, 202, 114)),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            } else {
                val total = stats[0]
                val free = stats[1]
                val used = (total - free).coerceAtLeast(0L)
                addView(makeText(formatBytes(used) + " đã dùng / " + formatBytes(total), 19f, Color.WHITE, true),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                val progress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    this.progress = ((used.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                    progressTintList = android.content.res.ColorStateList.valueOf(tint)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(47, 64, 89))
                }
                addView(progress, LinearLayout.LayoutParams(-1, dp(9)).apply { topMargin = dp(9) })
                addView(makeText("Còn trống: " + formatBytes(free), 12f, muted),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            }
        }
    }

    private fun buildDownloadPage() {
        sectionTitle("Tải thẳng vào thẻ SD", "Không lưu file hoàn chỉnh vào bộ nhớ trong trước")
        addCard {
            addView(makeText("THƯ MỤC NHẬN", 11f, green, true))
            val folder = selectedTree?.let { DocumentFile.fromTreeUri(this@MainActivity, it) }
            addView(makeText(folder?.name ?: "Chưa chọn thư mục SD", 16f, Color.WHITE, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addActionButton("Chọn / đổi thư mục SD", Color.rgb(40, 91, 155)) { chooseSdFolder() }
        }
        val url = EditText(this).apply {
            hint = "Dán liên kết HTTPS của tệp"
            setHintTextColor(Color.rgb(135, 157, 187))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            background = rounded(Color.rgb(27, 45, 72), 14)
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        val filename = EditText(this).apply {
            hint = "Tên tệp (để trống để tự nhận diện)"
            setHintTextColor(Color.rgb(135, 157, 187))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            background = rounded(Color.rgb(27, 45, 72), 14)
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        addCard {
            addView(makeText("LIÊN KẾT TẢI", 11f, muted, true))
            addView(url, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            addView(makeText("TÊN TỆP", 11f, muted, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            addView(filename, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            addActionButton("↓  TẢI TRỰC TIẾP VÀO SD", blue) {
                val link = url.text.toString().trim()
                val name = filename.text.toString().trim()
                if (selectedTree == null) {
                    toast("Hãy chọn thư mục thẻ SD trước")
                    chooseSdFolder()
                } else if (!link.startsWith("https://", true)) {
                    toast("Chỉ hỗ trợ liên kết HTTPS an toàn")
                } else {
                    val intent = Intent(this@MainActivity, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_DOWNLOAD
                        putExtra(DownloadService.EXTRA_URL, link)
                        putExtra(DownloadService.EXTRA_TREE, selectedTree.toString())
                        putExtra(DownloadService.EXTRA_NAME, name)
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                        toast("Đã bắt đầu tải. File tạm được ghi trên SD.")
                        url.text.clear()
                        filename.text.clear()
                    } catch (e: Exception) {
                        toast("Không thể bắt đầu tải: " + (e.message ?: "lỗi hệ thống"))
                    }
                }
            }
        }
        addCard {
            addView(makeText("CÁCH LƯU", 14f, Color.WHITE, true))
            addView(makeText("1. Chọn đúng thư mục thuộc thẻ SD.\n2. Dán liên kết HTTPS trực tiếp đến tệp.\n3. File .part được tạo trên SD và đổi tên khi hoàn tất.\n4. Nếu tải lỗi, file tạm vẫn ở SD để bạn kiểm tra hoặc xóa.", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(makeText("Đây là trình tải tệp của ứng dụng, không tự thay đổi vị trí cache riêng của Roblox hoặc game khác.", 12f, Color.rgb(255, 202, 114)),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        }
    }

    private fun buildFilesPage() {
        sectionTitle("Tệp trên thẻ SD", "Chỉ hiển thị thư mục bạn đã cấp quyền")
        val root = selectedTree?.let { DocumentFile.fromTreeUri(this@MainActivity, it) }
        if (root == null) {
            addCard {
                addView(makeText("Chưa chọn thư mục SD", 16f, Color.WHITE, true))
                addActionButton("Chọn thư mục SD", blue) { chooseSdFolder() }
            }
            return
        }
        if (currentFolder == null) currentFolder = root
        addCard {
            addView(makeText("THƯ MỤC HIỆN TẠI", 11f, green, true))
            addView(makeText(currentFolder?.name ?: root.name ?: "Thẻ SD", 15f, Color.WHITE, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            addActionButton(if (currentFolder == root) "Về thư mục gốc SD" else "Quay về thư mục gốc", blue) {
                currentFolder = root
                showTab(2)
            }
        }
        val items = currentFolder?.listFiles()?.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase(Locale.ROOT) }
        ) ?: emptyList()
        if (items.isEmpty()) {
            addCard { addView(makeText("Thư mục này chưa có tệp.", 13f, muted)) }
        } else {
            items.forEach { item ->
                addCard {
                    val symbol = if (item.isDirectory) "📁" else "📄"
                    addView(makeText(symbol + "  " + (item.name ?: "Không tên"), 14f, Color.WHITE, true))
                    addView(makeText(if (item.isDirectory) "Thư mục" else formatBytes(item.length()), 11f, muted),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    if (item.isDirectory) {
                        addActionButton("Mở thư mục", Color.rgb(40, 91, 155)) {
                            currentFolder = item
                            showTab(2)
                        }
                    } else {
                        addActionButton("Xóa tệp này", Color.rgb(123, 55, 65)) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Xóa tệp?")
                                .setMessage("Bạn có chắc muốn xóa " + (item.name ?: "tệp này") + " khỏi thẻ SD không?")
                                .setNegativeButton("Hủy", null)
                                .setPositiveButton("Xóa") { _, _ ->
                                    val ok = item.delete()
                                    toast(if (ok) "Đã xóa tệp khỏi SD" else "Không thể xóa tệp")
                                    showTab(2)
                                }.show()
                        }
                    }
                }
            }
        }
    }

    private fun buildSettingsPage() {
        sectionTitle("Cài đặt", "Quyền truy cập do Android kiểm soát")
        addCard {
            addView(makeText("THƯ MỤC LƯU MẶC ĐỊNH", 11f, green, true))
            addView(makeText(selectedTree?.let { DocumentFile.fromTreeUri(this@MainActivity, it)?.name } ?: "Chưa chọn", 13f, Color.WHITE),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addActionButton("Chọn thư mục thẻ SD", blue) { chooseSdFolder() }
            addActionButton("Gỡ quyền thư mục đã chọn", Color.rgb(123, 55, 65)) {
                selectedTree?.let { uri ->
                    try {
                        contentResolver.releasePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    } catch (_: Exception) {}
                }
                prefs.edit().remove(PREF_TREE_URI).apply()
                selectedTree = null
                currentFolder = null
                toast("Đã gỡ quyền thư mục")
                showTab(3)
            }
        }
        addCard {
            addView(makeText("GIỚI HẠN ANDROID", 15f, Color.WHITE, true))
            addView(makeText("Ứng dụng chỉ ghi vào thư mục SD bạn cấp quyền. Android vẫn giữ APK và dữ liệu riêng của ứng dụng trong vùng riêng. Không thể ép game khác đổi nơi lưu cache hoặc di chuyển dữ liệu riêng của chúng.", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
        addCard {
            addView(makeText("PHIÊN BẢN 1.0.0", 14f, Color.WHITE, true))
            addView(makeText("SD Storage Manager • Dev Nero\nBản đầu để kiểm thử, chưa phải bản phát hành Google Play.", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    private fun chooseSdFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
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
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            selectedTree = uri
            currentFolder = DocumentFile.fromTreeUri(this, uri)
            prefs.edit().putString(PREF_TREE_URI, uri.toString()).apply()
            toast("Đã chọn thẻ SD làm nơi lưu")
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
            manager.storageVolumes.any { it.isRemovable && it.uuid?.equals(volumeId, true) == true }
        } catch (_: Exception) { false }
    }

    private fun getInternalStats(): LongArray? {
        return try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            longArrayOf(stat.totalBytes, stat.availableBytes)
        } catch (_: Exception) { null }
    }

    private fun getSelectedSdStats(): LongArray? {
        val uri = selectedTree ?: return null
        return try {
            val volumeId = DocumentsContract.getTreeDocumentId(uri).substringBefore(':')
            val manager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volume = manager.storageVolumes.firstOrNull {
                it.isRemovable && it.uuid?.equals(volumeId, true) == true
            } ?: return null
            if (Build.VERSION.SDK_INT < 30) return null
            val directory = volume.directory ?: return null
            val stat = StatFs(directory.absolutePath)
            longArrayOf(stat.totalBytes, stat.availableBytes)
        } catch (_: Exception) { null }
    }

    private fun addCard(content: LinearLayout.() -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(panel, 18, Color.rgb(39, 57, 84))
        }
        val previous = activeCard
        activeCard = card
        card.content()
        activeCard = previous
        page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun sectionTitle(title: String, subtitle: String) {
        page.addView(makeText(title, 19f, Color.WHITE, true))
        page.addView(makeText(subtitle, 12f, muted), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
            bottomMargin = dp(13)
        })
    }

    private fun addActionButton(label: String, color: Int, action: () -> Unit) {
        val button = makeText(label, 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(color, 13)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(11) }
        (activeCard ?: page).addView(button, params)
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

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.getDefault(), if (index == 0) "%.0f %s" else "%.2f %s", value, units[index])
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        if (::page.isInitialized && currentTab == 0) showTab(0)
    }
}
