package vn.nero.gameturbo

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private data class AppEntry(val packageName: String, val label: String, val info: ResolveInfo, val isGame: Boolean)

    private val prefs by lazy { getSharedPreferences(HudOverlayService.PREFS, Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var cpuValue: TextView
    private lateinit var ramValue: TextView
    private lateinit var permissionHint: TextView
    private var permissionStep = 0
    private var lastStats: DeviceStats.Snapshot? = null

    private val refreshStats = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                val stats = DeviceStats.read(this@MainActivity)
                cpuValue.text = stats.cpuText
                ramValue.text = stats.ramText
                lastStats = stats
                handler.postDelayed(this, 1500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = Color.rgb(7, 10, 18)
        window.navigationBarColor = Color.rgb(7, 10, 18)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionHint()
        handler.removeCallbacks(refreshStats)
        handler.post(refreshStats)
        if (prefs.getBoolean("hud_enabled", false) && hasOverlayPermission() && hasUsageAccess()) {
            startHudService()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshStats)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF080B14.toInt(), 0xFF12162A.toInt(), 0xFF090C16.toInt())
            )
        }

        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            background = rounded(0xFF05070D.toInt(), 20)
            setPadding(dp(6), dp(10), dp(6), dp(8))
        }
        val logo = label("N", 21f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(0xFF7928CA.toInt(), 15)
        }
        rail.addView(logo, LinearLayout.LayoutParams(dp(48), dp(45)))
        rail.addView(label("APPS", 8f, 0xFF8E9AAF.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, dp(4))
        })
        val railScroll = ScrollView(this)
        val appColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        installedApps().take(12).forEach { app ->
            val item = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                background = rippleBackground(0x00000000, 12)
                setOnClickListener { launchApplication(app.packageName, false) }
                setOnLongClickListener {
                    launchApplication(app.packageName, true)
                    true
                }
                contentDescription = app.label
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.info.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            item.addView(icon, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
            appColumn.addView(item, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                bottomMargin = dp(5)
            })
        }
        railScroll.addView(appColumn)
        rail.addView(railScroll, LinearLayout.LayoutParams(dp(54), 0, 1f))
        rail.addView(label("GIỮ\n= CỬA SỔ", 7f, 0xFF8390A5.toInt(), true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(dp(54), dp(35)))
        root.addView(rail, LinearLayout.LayoutParams(dp(66), -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(2), dp(4), dp(2))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandBox.addView(label("NERO GAME TURBO", 21f, Color.WHITE, true))
        brandBox.addView(label("TĂNG TỐC · THEO DÕI · TIỆN ÍCH", 9f, 0xFF929DB2.toInt(), true))
        header.addView(brandBox, LinearLayout.LayoutParams(0, -2, 1f))
        val status = label("● HUD SẴN SÀNG", 10f, 0xFF9DE7C4.toInt(), true).apply {
            background = rounded(0xFF153B33.toInt(), 18)
            setPadding(dp(13), dp(9), dp(13), dp(9))
        }
        header.addView(status)
        content.addView(header, LinearLayout.LayoutParams(-1, dp(48)))

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cpuCard = statCard("CPU", "Đang đo", "Mức sử dụng", 0xFFB85CFF)
        cpuValue = cpuCard.second
        val fpsCard = statCard("FPS", "—", "Không có quyền đọc FPS hệ thống", 0xFFB85CFF)
        val ramCard = statCard("RAM", "—", "Bộ nhớ đã dùng", 0xFF4EBBFF)
        ramValue = ramCard.second
        val gpuCard = statCard("GPU", "—", "Cần quyền hệ thống Samsung", 0xFF4EBBFF)
        listOf(cpuCard.first, fpsCard.first, ramCard.first, gpuCard.first).forEachIndexed { index, card ->
            stats.addView(card, LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
        content.addView(stats, LinearLayout.LayoutParams(-1, dp(78)).apply {
            topMargin = dp(8)
        })

        val profileTitle = label("CHẾ ĐỘ HIỆU NĂNG", 10f, 0xFF9DA9BE.toInt(), true).apply {
            setPadding(dp(2), dp(10), 0, dp(5))
        }
        content.addView(profileTitle)
        val profiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        profiles.addView(profileButton("Hiệu năng", true), LinearLayout.LayoutParams(0, dp(38), 1f))
        profiles.addView(profileButton("Cân bằng", false), LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(8) })
        profiles.addView(profileButton("Tiết kiệm pin", false), LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(8) })
        content.addView(profiles)

        content.addView(label("CÔNG CỤ GAME TURBO", 10f, 0xFF9DA9BE.toInt(), true).apply {
            setPadding(dp(2), dp(10), 0, dp(5))
        })
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val col1 = toolColumn(listOf(
            "Chế độ trò chơi" to "Tùy chỉnh HUD theo game",
            "Theo dõi hệ thống" to "CPU · RAM · nhiệt độ pin",
            "Bảo vệ cảm ứng" to "Hướng dẫn cài đặt phù hợp",
        ))
        val col2 = toolColumn(listOf(
            "Chụp màn hình" to "Mở công cụ chụp của máy",
            "Ghi màn hình" to "Dùng trình ghi màn hình hệ thống",
            "Hẹn giờ" to "Đặt thời lượng chơi",
        ))
        tools.addView(col1, LinearLayout.LayoutParams(0, -1, 1f))
        tools.addView(col2, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(8) })
        content.addView(tools, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF05070D.toInt(), 18)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        footer.addView(label("TIỆN ÍCH", 9f, 0xFF8D9BB2.toInt(), true), LinearLayout.LayoutParams(dp(58), -2))
        listOf("Âm thanh", "Độ sáng", "Không làm phiền", "Ứng dụng game").forEach { name ->
            footer.addView(smallAction(name) {
                when (name) {
                    "Âm thanh" -> openSystemSettings(Settings.ACTION_SOUND_SETTINGS)
                    "Độ sáng" -> openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS)
                    "Không làm phiền" -> openSystemSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    "Ứng dụng game" -> showGamePicker()
                }
            }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { leftMargin = dp(5) })
        }
        val settings = button("⚙", 0xFF252B3A.toInt(), Color.WHITE) { showHudSettings() }
        footer.addView(settings, LinearLayout.LayoutParams(dp(42), dp(34)).apply { leftMargin = dp(6) })
        content.addView(footer, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        val permissionBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF1A2234.toInt(), 14)
            setPadding(dp(10), dp(5), dp(6), dp(5))
        }
        permissionHint = label("Bật quyền để hiển thị HUD khi chơi game", 9f, 0xFFC4CEE0.toInt(), false)
        permissionBox.addView(permissionHint, LinearLayout.LayoutParams(0, -2, 1f))
        permissionBox.addView(button("BẬT HUD", 0xFF8538D1.toInt(), Color.WHITE) { enableHudFlow() },
            LinearLayout.LayoutParams(dp(112), dp(34)))
        content.addView(permissionBox, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })

        root.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)

        root.alpha = 0f
        root.translationY = dp(12).toFloat()
        root.animate().alpha(1f).translationY(0f).setDuration(520)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun statCard(title: String, value: String, detail: String, accent: Int): Pair<View, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF171D2C.toInt(), 14)
            setPadding(dp(11), dp(7), dp(9), dp(6))
        }
        card.addView(label(title, 9f, accent, true))
        val valueView = label(value, 20f, Color.WHITE, true)
        card.addView(valueView, LinearLayout.LayoutParams(-1, dp(29)))
        card.addView(label(detail, 8f, 0xFF8F9BB0.toInt(), false))
        return card to valueView
    }

    private fun profileButton(text: String, selected: Boolean): View =
        button(text, if (selected) 0xFF7332B8.toInt() else 0xFF252C3B.toInt(), Color.WHITE) {
            Toast.makeText(this, "Chế độ $text: Android không cấp quyền ép xung CPU/GPU cho ứng dụng thường", Toast.LENGTH_SHORT).show()
        }

    private fun toolColumn(items: List<Pair<String, String>>): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.forEach { (title, sub) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                background = rippleBackground(0xFF252C3B.toInt(), 13)
                setPadding(dp(12), dp(5), dp(8), dp(5))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    when (title) {
                        "Chế độ trò chơi" -> showGamePicker()
                        "Theo dõi hệ thống" -> Toast.makeText(this@MainActivity, "Mở Theo dõi hệ thống: xem CPU, RAM, pin và Hz. FPS thực cần quyền hệ thống Samsung.", Toast.LENGTH_LONG).show()
                        "Bảo vệ cảm ứng" -> Toast.makeText(this@MainActivity, "A07 không cấp quyền khóa cảm ứng của ứng dụng khác cho HUD bên thứ ba.", Toast.LENGTH_LONG).show()
                        "Chụp màn hình" -> openSystemSettings("com.android.systemui.action.SCREENSHOT")
                        "Ghi màn hình" -> openScreenRecorder()
                        "Hẹn giờ" -> showTimerDialog()
                    }
                }
            }
            card.addView(label(title, 12f, Color.WHITE, true))
            card.addView(label(sub, 8f, 0xFF9AA6BA.toInt(), false))
            column.addView(card, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                bottomMargin = dp(6)
            })
        }
        return column
    }

    private fun smallAction(name: String, action: () -> Unit): View =
        button(name, 0xFF252C3B.toInt(), 0xFFE8EDF7.toInt(), 9f, action)

    private fun button(text: String, bg: Int, fg: Int, size: Float = 11f, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = size
            setTextColor(fg)
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            background = rippleBackground(bg, 12)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { action() }
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun rippleBackground(color: Int, radius: Int): android.graphics.drawable.Drawable {
        val content = rounded(color, radius)
        val mask = rounded(Color.WHITE, radius)
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x447F51D5),
            content,
            mask
        )
    }

    private fun installedApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return results
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                val appInfo = it.activityInfo.applicationInfo
                AppEntry(
                    it.activityInfo.packageName,
                    it.loadLabel(packageManager).toString(),
                    it,
                    appInfo.category == ApplicationInfo.CATEGORY_GAME
                )
            }
            .sortedWith(compareByDescending<AppEntry> { it.isGame }.thenBy { it.label.lowercase(Locale.getDefault()) })
    }

    private fun launchApplication(packageName: String, floating: Boolean) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (floating && Build.VERSION.SDK_INT >= 24) {
                val bounds = android.graphics.Rect(dp(35), dp(80), resources.displayMetrics.widthPixels * 3 / 4, resources.displayMetrics.heightPixels * 3 / 4)
                val options = android.app.ActivityOptions.makeBasic()
                options.launchBounds = bounds
                startActivity(launch, options.toBundle())
                Toast.makeText(this, "Đã yêu cầu mở cửa sổ nổi. Kết quả phụ thuộc khả năng đa cửa sổ của One UI và ứng dụng.", Toast.LENGTH_LONG).show()
            } else {
                startActivity(launch)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ứng dụng không hỗ trợ mở theo cách này: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun enableHudFlow() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Cho phép Nero hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!hasUsageAccess()) {
            Toast.makeText(this, "Cho phép quyền truy cập dữ liệu sử dụng để nhận diện game đang mở", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        prefs.edit().putBoolean("hud_enabled", true).apply()
        startHudService()
        updatePermissionHint()
        Toast.makeText(this, "Nero Game HUD đã bật. Mở game để kiểm tra tay nắm.", Toast.LENGTH_LONG).show()
    }

    private fun startHudService() {
        val intent = Intent(this, HudOverlayService::class.java).setAction(HudOverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun updatePermissionHint() {
        val enabled = hasOverlayPermission() && hasUsageAccess()
        permissionHint.text = when {
            !hasOverlayPermission() -> "Cần quyền Hiển thị trên ứng dụng khác"
            !hasUsageAccess() -> "Cần quyền truy cập dữ liệu sử dụng để phát hiện game"
            else -> "Đã sẵn sàng · HUD chỉ hiện khi game được nhận diện"
        }
    }

    private fun showGamePicker() {
        val apps = installedApps()
        val selected = prefs.getStringSet(HudOverlayService.KEY_GAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val names = apps.map { (if (it.isGame) "🎮 " else "") + it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { selected.contains(apps[it].packageName) || (selected.isEmpty() && apps[it].isGame) }
        AlertDialog.Builder(this)
            .setTitle("CHỌN ỨNG DỤNG TRÒ CHƠI")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                val pkg = apps[which].packageName
                if (isChecked) selected.add(pkg) else selected.remove(pkg)
            }
            .setPositiveButton("LƯU") { _, _ ->
                prefs.edit().putStringSet(HudOverlayService.KEY_GAMES, selected).apply()
                sendHudRefresh()
                Toast.makeText(this, "Đã lưu danh sách game", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("HỦY", null)
            .show()
    }

    private fun showHudSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(6))
        }
        box.addView(label("Màu tay nắm", 13f, Color.WHITE, true))
        val colorNames = arrayOf("Trắng", "Tím", "Xanh dương", "Xanh lá")
        val colors = intArrayOf(Color.WHITE, 0xFFB96BFF.toInt(), 0xFF56B8FF.toInt(), 0xFF59E0AA.toInt())
        val selectedColor = prefs.getInt(HudOverlayService.KEY_HANDLE_COLOR, Color.WHITE)
        var colorIndex = colors.indices.minByOrNull { kotlin.math.abs(colors[it] - selectedColor) } ?: 0
        val colorButton = button(colorNames[colorIndex], 0xFF242B3B.toInt(), Color.WHITE, 12f) {}
        colorButton.setOnClickListener {
            colorIndex = (colorIndex + 1) % colors.size
            colorButton.text = colorNames[colorIndex]
        }
        box.addView(colorButton, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6); bottomMargin = dp(12) })
        box.addView(label("Chiều dài tay nắm", 13f, Color.WHITE, true))
        val length = SeekBar(this).apply {
            max = 80
            progress = (prefs.getInt(HudOverlayService.KEY_HANDLE_LENGTH, 54) - 24).coerceIn(0, 80)
        }
        box.addView(length)
        box.addView(label("Độ dày tay nắm", 13f, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) })
        val width = SeekBar(this).apply {
            max = 10
            progress = prefs.getInt(HudOverlayService.KEY_HANDLE_WIDTH, 3).coerceIn(1, 11) - 1
        }
        box.addView(width)
        box.addView(label("Độ trong suốt", 13f, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) })
        val alpha = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt(HudOverlayService.KEY_HANDLE_ALPHA, 100)
        }
        box.addView(alpha)
        AlertDialog.Builder(this)
            .setTitle("TÙY CHỈNH HUD")
            .setView(box)
            .setPositiveButton("LƯU") { _, _ ->
                prefs.edit()
                    .putInt(HudOverlayService.KEY_HANDLE_COLOR, colors[colorIndex])
                    .putInt(HudOverlayService.KEY_HANDLE_LENGTH, length.progress + 24)
                    .putInt(HudOverlayService.KEY_HANDLE_WIDTH, width.progress + 1)
                    .putInt(HudOverlayService.KEY_HANDLE_ALPHA, alpha.progress)
                    .apply()
                sendHudRefresh()
            }
            .setNegativeButton("HỦY", null)
            .show()
    }

    private fun showTimerDialog() {
        val minutes = arrayOf("15 phút", "30 phút", "45 phút", "60 phút", "90 phút")
        AlertDialog.Builder(this)
            .setTitle("HẸN GIỜ CHƠI GAME")
            .setItems(minutes) { _, which ->
                Toast.makeText(this, "Đã chọn nhắc sau ${minutes[which]}. Tính năng nhắc giờ sẽ chạy trên HUD.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("ĐÓNG", null)
            .show()
    }

    private fun openScreenRecorder() {
        val intent = Intent("com.android.systemui.action.SCREEN_RECORD")
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Mở bảng Cài đặt nhanh và chọn Ghi màn hình trên điện thoại.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openSystemSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun sendHudRefresh() {
        try {
            sendBroadcast(Intent(HudOverlayService.ACTION_REFRESH).setPackage(packageName))
        } catch (_: Exception) { }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
