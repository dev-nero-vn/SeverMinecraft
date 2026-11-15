package vn.nero.gameturbo

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class HudOverlayService : Service() {
    companion object {
        const val PREFS = "nero_game_turbo_settings"
        const val KEY_GAMES = "selected_games"
        const val KEY_SHORTCUTS = "selected_shortcut_apps"
        const val KEY_HANDLE_COLOR = "handle_color"
        const val KEY_HANDLE_LENGTH = "handle_length"
        const val KEY_HANDLE_WIDTH = "handle_width"
        const val KEY_HANDLE_ALPHA = "handle_alpha"
        const val KEY_GAME_MODE = "game_mode"
        const val KEY_HUD_ENABLED = "hud_enabled"
        const val KEY_TOUCH_LOCK = "touch_lock"
        const val ACTION_START = "vn.nero.gameturbo.START"
        const val ACTION_REFRESH = "vn.nero.gameturbo.REFRESH"
        private const val CHANNEL_ID = "nero_game_turbo_hud"
        private const val NOTIFICATION_ID = 4107
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var windowManager: WindowManager
    private var gripView: FrameLayout? = null
    private var panelView: View? = null
    private var currentGamePackage: String? = null
    private var currentGameName = "Đang chờ trò chơi"
    private var panelOpen = false
    private val panelMetricViews = mutableListOf<CircularStatView>()
    private val modeChipViews = linkedMapOf<String, TextView>()
    private var cpuText = "—"
    private var ramText = "—"
    private var hzText = "—"

    private val monitor = object : Runnable {
        override fun run() {
            if (!prefs.getBoolean(KEY_HUD_ENABLED, false)) {
                stopSelf()
                return
            }

            detectForegroundGame()

            // Khi không có game: chỉ kiểm tra trạng thái thưa, không đọc CPU/RAM liên tục.
            // Khi game chạy nhưng HUD đóng: chỉ nhận diện game, không lấy mẫu thống kê.
            val delay = when {
                currentGamePackage == null -> 6000L
                !panelOpen -> 1800L
                else -> when (prefs.getString(KEY_GAME_MODE, "balanced")) {
                    "performance" -> 700L
                    "battery" -> 3000L
                    else -> 1400L
                }
            }

            if (currentGamePackage != null && panelOpen) {
                val stats = DeviceStats.read(this@HudOverlayService)
                cpuText = stats.cpuText
                ramText = stats.ramText
                hzText = stats.refreshText
                refreshPanelStats()
            }
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Đang chờ game"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.getBoolean(KEY_HUD_ENABLED, false)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_REFRESH -> {
                closePanel()
                removeGrip()
                currentGamePackage = null
                currentGameName = "Đang chờ trò chơi"
            }
            ACTION_START, null -> Unit
        }
        handler.removeCallbacks(monitor)
        handler.post(monitor)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(monitor)
        closePanel()
        removeGrip()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun detectForegroundGame() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            hideAllHud()
            return
        }
        if (!hasUsageAccess()) {
            hideAllHud()
            return
        }

        val now = System.currentTimeMillis()
        val events = try {
            (getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager)
                .queryEvents(now - 15_000L, now)
        } catch (_: Exception) {
            null
        }
        var latestPackage: String? = null
        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val resumed = if (Build.VERSION.SDK_INT >= 29) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                // Giữ cả launcher/System UI như sự kiện rời game để HUD được ẩn đúng lúc.
                if (resumed && !event.packageName.isNullOrBlank()) {
                    latestPackage = event.packageName
                }
            }
        }

        if (latestPackage == null) {
            // Game vẫn đang foreground nhưng không có event mới: giữ trạng thái hiện tại.
            if (currentGamePackage != null) return
            latestPackage = try {
                (getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager)
                    .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10_000L, now)
                    .asSequence()
                    .filter { it.packageName != packageName && it.lastTimeUsed >= now - 9_000L }
                    .maxByOrNull { it.lastTimeUsed }
                    ?.packageName
            } catch (_: Exception) { null }
        }
        if (latestPackage == null) return

        val gamePackages = prefs.getStringSet(KEY_GAMES, emptySet()) ?: emptySet()
        val isGame = gamePackages.contains(latestPackage) || isSystemGame(latestPackage)
        if (isGame) {
            if (currentGamePackage != latestPackage) {
                currentGamePackage = latestPackage
                currentGameName = applicationLabel(latestPackage)
                showGrip()
                updateNotification("Đang chơi: $currentGameName")
                Toast.makeText(this, "Đã nhận diện game: $currentGameName", Toast.LENGTH_SHORT).show()
            } else {
                showGrip()
            }
        } else if (currentGamePackage != null) {
            currentGamePackage = null
            currentGameName = "Đang chờ trò chơi"
            hideAllHud()
            updateNotification("Đang chờ game")
        }
    }

    private fun isIgnoredPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        return pkg == "com.android.systemui" ||
            pkg == "com.android.permissioncontroller" ||
            pkg == "com.google.android.permissioncontroller" ||
            pkg == "com.android.settings" ||
            pkg.startsWith("com.samsung.android.")
    }

    private fun isSystemGame(pkg: String): Boolean {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
            info.category == ApplicationInfo.CATEGORY_GAME
        } catch (_: Exception) {
            false
        }
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun showGrip() {
        if (gripView != null) return
        val color = prefs.getInt(KEY_HANDLE_COLOR, Color.WHITE)
        val length = prefs.getInt(KEY_HANDLE_LENGTH, 54).coerceIn(24, 104)
        val thickness = prefs.getInt(KEY_HANDLE_WIDTH, 3).coerceIn(1, 11)
        val alpha = prefs.getInt(KEY_HANDLE_ALPHA, 100).coerceIn(10, 100)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Tay nắm Nero Game Turbo. Vuốt sang phải để mở bảng."
        }
        val line = View(this).apply {
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(4).toFloat()
                setAlpha(255 * alpha / 100)
            }
            elevation = dp(4).toFloat()
        }
        val lineParams = FrameLayout.LayoutParams(dp(thickness), dp(length), Gravity.LEFT or Gravity.CENTER_VERTICAL)
        root.addView(line, lineParams)
        var downX = 0f
        var downY = 0f
        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - downX
                    if (deltaX > dp(8) || kotlin.math.abs(event.rawY - downY) < dp(18)) {
                        openPanel()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        val params = WindowManager.LayoutParams(
            dp(18),
            dp(length + 12),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = dp(66)
        }
        try {
            windowManager.addView(root, params)
            gripView = root
        } catch (_: Exception) {
            gripView = null
        }
    }

    private fun removeGrip() {
        gripView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        gripView = null
    }

    private fun hideAllHud() {
        closePanel()
        removeGrip()
    }

    private fun openPanel() {
        if (panelOpen) {
            closePanel()
            return
        }
        if (!prefs.getBoolean(KEY_HUD_ENABLED, false) || currentGamePackage == null) return
        val latestStats = DeviceStats.read(this)
        cpuText = latestStats.cpuText
        ramText = latestStats.ramText
        hzText = latestStats.refreshText
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(0xF20A0E1A.toInt(), 22)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            elevation = dp(18).toFloat()
        }

        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            background = rounded(0xFF05070D.toInt(), 17)
            setPadding(dp(4), dp(7), dp(4), dp(7))
        }
        val gameIcon = ImageView(this).apply {
            setImageDrawable(applicationIcon(currentGamePackage!!))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        rail.addView(gameIcon, LinearLayout.LayoutParams(dp(38), dp(38)).apply { bottomMargin = dp(7) })
        val allLauncherApps = launcherApps()
        val savedShortcuts = prefs.getStringSet(KEY_SHORTCUTS, emptySet()) ?: emptySet()
        // Trống nghĩa là hiện mọi ứng dụng. Khi người dùng chọn ở nút +, chỉ các ứng dụng đã chọn được ghim.
        val visibleApps = if (savedShortcuts.isEmpty()) allLauncherApps
            else allLauncherApps.filter { savedShortcuts.contains(it.activityInfo.packageName) }

        val appScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        visibleApps.forEach { app ->
            val tile = FrameLayout(this).apply {
                background = ripple(0x00000000, 10)
                isClickable = true
                isFocusable = true
                var downX = 0f
                var downY = 0f
                var holdReady = false
                var dragged = false
                val holdRunnable = Runnable {
                    holdReady = true
                    tile.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    tile.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                }
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            holdReady = false
                            dragged = false
                            handler.removeCallbacks(holdRunnable)
                            handler.postDelayed(holdRunnable, 380L)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (holdReady && !dragged &&
                                (event.rawX - downX > dp(28) || kotlin.math.abs(event.rawY - downY) > dp(28))) {
                                dragged = true
                                handler.removeCallbacks(holdRunnable)
                                launchApp(app.activityInfo.packageName, true)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(holdRunnable)
                            tile.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                            if (!dragged) launchApp(app.activityInfo.packageName, false)
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            handler.removeCallbacks(holdRunnable)
                            tile.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                            true
                        }
                        else -> true
                    }
                }
                contentDescription = app.loadLabel(packageManager).toString() + ". Vuốt kéo sang phải để mở cửa sổ nổi."
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            tile.addView(icon, FrameLayout.LayoutParams(dp(31), dp(31), Gravity.CENTER))
            appList.addView(tile, LinearLayout.LayoutParams(dp(42), dp(43)))
        }
        appScroll.addView(appList)
        rail.addView(appScroll, LinearLayout.LayoutParams(dp(44), 0, 1f))

        val addApp = text("+", 25f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = ripple(0xFF263044.toInt(), 11)
            contentDescription = "Thêm ứng dụng vào dải Game Turbo"
            setOnClickListener { showShortcutPicker() }
        }
        rail.addView(addApp, LinearLayout.LayoutParams(dp(40), dp(38)).apply { topMargin = dp(5) })
        val editApps = text("✎", 18f, 0xFFDDE5F3.toInt(), true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Chỉnh sửa ứng dụng trong dải Game Turbo"
            setOnClickListener { showShortcutPicker() }
        }
        rail.addView(editApps, LinearLayout.LayoutParams(dp(40), dp(34)))
        root.addView(rail, LinearLayout.LayoutParams(dp(52), -1))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(3), dp(4), dp(3))
        }
        val rightScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
        }
        val rightContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleGroup.addView(text("NERO GAME TURBO", 15f, Color.WHITE, true))
        titleGroup.addView(text(currentGameName.uppercase(Locale.getDefault()), 9f, 0xFF94A0B5.toInt(), true))
        header.addView(titleGroup, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("● ĐANG CHƠI", 9f, 0xFF79E2B3.toInt(), true).apply {
            background = rounded(0xFF15392F.toInt(), 13)
            setPadding(dp(9), dp(6), dp(9), dp(6))
        })
        val close = text("×", 23f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { closePanel() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(32), dp(32)))
        rightContent.addView(header, LinearLayout.LayoutParams(-1, dp(38)))

        panelMetricViews.clear()
        val statRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cpuGauge = CircularStatView(this, "CPU", cpuText, percentFrom(cpuText), 0xFFB45CFF.toInt())
        val fpsGauge = CircularStatView(this, "FPS", "—", null, 0xFF42D7F5.toInt())
        val ramGauge = CircularStatView(this, "RAM", ramText, percentFrom(ramText), 0xFF48D6A0.toInt())
        listOf(cpuGauge, fpsGauge, ramGauge).forEachIndexed { index, gauge ->
            panelMetricViews.add(gauge)
            statRow.addView(gauge, LinearLayout.LayoutParams(0, dp(84), 1f).apply {
                if (index > 0) leftMargin = dp(5)
            })
        }
        rightContent.addView(statRow, LinearLayout.LayoutParams(-1, dp(86)).apply { topMargin = dp(5) })
        rightContent.addView(text("CPU/RAM cập nhật theo mẫu đo. FPS thật cần quyền đo khung hình từ hệ thống.", 7f, 0xFF8290AA.toInt(), false),
            LinearLayout.LayoutParams(-1, dp(16)))

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeChipViews.clear()
        val modes = listOf(
            "Hiệu năng" to "performance",
            "Cân bằng" to "balanced",
            "Tiết kiệm pin" to "battery"
        )
        modes.forEachIndexed { index, entry ->
            val chip = actionChip(entry.first, entry.second) { selectGameMode(entry.second) }
            modeChipViews[entry.second] = chip
            modeRow.addView(chip, LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                if (index > 0) leftMargin = dp(5)
            })
        }
        rightContent.addView(modeRow, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(5) })
        updateModeChipStyles()

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, dp(4))
        }
        val toolRows = listOf(
            listOf("Quản lý game", "Theo dõi hệ thống"),
            listOf("Tùy chỉnh HUD", "Menu ứng dụng"),
            listOf("Tăng cường cảm ứng", "Bảo vệ cảm ứng"),
            listOf("Chụp màn hình", "Ghi màn hình"),
            listOf("Chặn thông báo", "Hẹn giờ"),
            listOf("Âm thanh", "Độ sáng"),
            listOf("Cửa sổ nổi", "Cài đặt Android"),
        )
        toolRows.forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { name ->
                val view = toolButton(name) { onToolSelected(name) }
                line.addView(view, LinearLayout.LayoutParams(0, 0, 1f).apply {
                    height = dp(41)
                    if (name == row[1]) leftMargin = dp(6)
                })
            }
            tools.addView(line, LinearLayout.LayoutParams(-1, dp(41)).apply { bottomMargin = dp(6) })
        }
        rightContent.addView(tools, LinearLayout.LayoutParams(-1, -2))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF05070D.toInt(), 14)
            setPadding(dp(7), dp(4), dp(7), dp(4))
        }
        listOf("Chụp", "Ghi", "Hẹn giờ", "Cài đặt").forEach { name ->
            footer.addView(text(name, 9f, 0xFFE6EAF4.toInt(), true).apply {
                gravity = Gravity.CENTER
                background = ripple(0xFF202738.toInt(), 10)
                setOnClickListener { onToolSelected(name) }
            }, LinearLayout.LayoutParams(0, dp(32), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        rightContent.addView(footer, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(6); bottomMargin = dp(6) })
        rightScroll.addView(rightContent, FrameLayout.LayoutParams(-1, -2))
        body.addView(rightScroll, LinearLayout.LayoutParams(-1, -1))
        root.addView(body, LinearLayout.LayoutParams(0, -1, 1f))

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        // Compact game-side panel: match the reference layout (about 45% width, nearly full height).
        // Bảng được giới hạn kích thước để không che quá nhiều phần game.
        // Nội dung bên trong cuộn độc lập theo chiều dọc.
        val width = dp(360).coerceAtMost(screenWidth - dp(20)).coerceAtLeast(dp(280))
        val height = (screenHeight * 0.82f).toInt().coerceAtLeast(dp(300)).coerceAtMost(screenHeight - dp(24))
        val params = WindowManager.LayoutParams(
            width, height, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = dp(8)
            y = ((screenHeight - height) / 2).coerceAtLeast(dp(8))
        }
        try {
            windowManager.addView(root, params)
            panelView = root
            panelOpen = true
            root.alpha = 0f
            root.translationX = -dp(14).toFloat()
            root.animate().alpha(1f).translationX(0f).setDuration(230).start()
        } catch (_: Exception) {
            panelOpen = false
            Toast.makeText(this, "Không thể mở bảng HUD. Hãy kiểm tra quyền hiển thị trên ứng dụng khác.", Toast.LENGTH_LONG).show()
        }
    }

    private fun closePanel() {
        panelView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        panelView = null
        panelOpen = false
        panelMetricViews.clear()
        modeChipViews.clear()
    }

    private fun refreshPanelStats() {
        if (panelMetricViews.size >= 3) {
            panelMetricViews[0].update(cpuText, percentFrom(cpuText))
            // FPS game khác không có API công khai dùng được ở ứng dụng thường.
            panelMetricViews[1].update("—", null)
            panelMetricViews[2].update(ramText, percentFrom(ramText))
        }
    }

    private fun percentFrom(value: String): Int? =
        value.removeSuffix("%").trim().toIntOrNull()?.coerceIn(0, 100)

    private fun selectGameMode(mode: String) {
        prefs.edit().putString(KEY_GAME_MODE, mode).apply()
        updateModeChipStyles()
        val message = when (mode) {
            "performance" -> "Đã chọn nhịp Hiệu năng: vòng đo HUD cập nhật nhanh hơn. Tối ưu CPU/GPU hệ thống vẫn do One UI quản lý."
            "battery" -> "Đã chọn Tiết kiệm pin: HUD giảm tần suất cập nhật để hạn chế hoạt động nền."
            else -> "Đã chọn Cân bằng: HUD dùng nhịp cập nhật tiêu chuẩn."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        handler.removeCallbacks(monitor)
        handler.post(monitor)
        if (mode == "battery") {
            showHudDialog(
                "TIẾT KIỆM PIN CỦA ĐIỆN THOẠI",
                "Nero đã giảm tần suất lấy mẫu HUD. Muốn bật Chế độ tiết kiệm pin thật của Android, mở phần cài đặt pin.",
                "MỞ CÀI ĐẶT PIN"
            ) { startActivitySafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
        } else if (mode == "performance") {
            val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (Build.VERSION.SDK_INT >= 21 && power.isPowerSaveMode) {
                showHudDialog(
                    "CHẾ ĐỘ HIỆU NĂNG",
                    "Điện thoại đang bật tiết kiệm pin. Mở cài đặt pin để tắt nếu bạn muốn hệ thống ưu tiên hiệu năng.",
                    "MỞ CÀI ĐẶT PIN"
                ) { startActivitySafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
            }
        }
    }

    private fun updateModeChipStyles() {
        val selected = prefs.getString(KEY_GAME_MODE, "balanced")
        modeChipViews.forEach { (mode, chip) ->
            val active = mode == selected
            val color = when (mode) {
                "performance" -> 0xFF9D4CFF.toInt()
                "battery" -> 0xFF168B68.toInt()
                else -> 0xFF2875C9.toInt()
            }
            chip.background = rounded(if (active) color else 0xFF252C3B.toInt(), 11)
            chip.setTextColor(if (active) Color.WHITE else 0xFF9BA8BD.toInt())
            chip.alpha = if (active) 1f else 0.82f
        }
    }

    private fun actionChip(name: String, mode: String, action: () -> Unit): TextView =
        text(name, 9f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = ripple(0xFF252C3B.toInt(), 11)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                }
                false
            }
            setOnClickListener { action() }
            post { updateModeChipStyles() }
        }

    private fun toolButton(name: String, action: () -> Unit): View {
        val glyph = when (name) {
            "Quản lý game" -> "▣"
            "Theo dõi hệ thống" -> "◉"
            "Tùy chỉnh HUD" -> "⚙"
            "Menu ứng dụng" -> "▦"
            "Tăng cường cảm ứng" -> "✥"
            "Bảo vệ cảm ứng" -> "⛨"
            "Chụp màn hình" -> "▧"
            "Ghi màn hình" -> "●"
            "Chặn thông báo" -> "◌"
            "Hẹn giờ" -> "◷"
            "Âm thanh" -> "♫"
            "Độ sáng" -> "☼"
            "Cửa sổ nổi" -> "▣"
            else -> "⌘"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ripple(0xFF202A3C.toInt(), 13)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.86f).setDuration(75).start()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(125).start()
                    }
                }
                false
            }
            addView(TextView(this@HudOverlayService).apply {
                text = glyph
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(0xFFB98AFF.toInt())
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(-1, dp(22)))
            addView(TextView(this@HudOverlayService).apply {
                text = name
                textSize = 9f
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(0xFFE7ECF6.toInt())
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(-1, -2))
            setOnClickListener { action() }
        }
    }

    private fun onToolSelected(name: String) {
        when (name) {
            "Menu ứng dụng" -> showShortcutPicker()
            "Quản lý game", "Chế độ game" -> showGamePicker()
            "Tùy chỉnh HUD" -> showHudSettings()
            "Cửa sổ nổi" -> showHudDialog(
                "MỞ ỨNG DỤNG DẠNG CỬA SỔ",
                "Giữ biểu tượng ứng dụng ở dải bên trái, kéo sang phải rồi thả. Nero gửi yêu cầu cửa sổ tự do cho Android; One UI chỉ mở pop-up nếu ứng dụng và thiết bị hỗ trợ."
            )
            "Theo dõi hệ thống" -> showStatsDetails()
            "Tăng cường cảm ứng" -> showHudDialog(
                "ĐỘ NHẠY CẢM ỨNG",
                "Mở cài đặt Màn hình để kiểm tra tùy chọn Độ nhạy cảm ứng của thiết bị. Android không cho ứng dụng bên thứ ba tự thay đổi bộ điều khiển cảm ứng của game."
            ) { startActivitySafely(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            "Bảo vệ cảm ứng" -> showHudDialog(
                "BẢO VỆ CẢM ỨNG",
                "Android không cấp quyền để Nero chặn hoặc thay đổi mọi thao tác chạm bên trong game khác. Bạn có thể dùng tính năng chống chạm nhầm có sẵn trong game hoặc One UI."
            )
            "Chụp màn hình", "Chụp" -> showHudDialog(
                "CHỤP MÀN HÌNH",
                "Trên Galaxy, nhấn đồng thời phím Nguồn và Giảm âm lượng. Bạn cũng có thể dùng nút Chụp màn hình trong Bảng điều khiển nhanh."
            )
            "Ghi màn hình", "Ghi" -> showHudDialog(
                "GHI MÀN HÌNH",
                "Mở Bảng điều khiển nhanh của Samsung, chọn Ghi màn hình rồi xác nhận bắt đầu ghi. Android yêu cầu xác nhận riêng cho mỗi phiên ghi màn hình."
            )
            "Chặn thông báo", "Chế độ im lặng" -> toggleDoNotDisturb()
            "Hẹn giờ" -> startActivitySafely(
                Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                    .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, 30 * 60)
                    .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Nero Game Turbo")
            )
            "Đổi giọng nói" -> showHudDialog(
                "ĐỔI GIỌNG NÓI",
                "Đổi giọng nói trực tiếp trong mọi game cần xử lý âm thanh ở cấp hệ thống hoặc hỗ trợ riêng từ game. Nero không giả lập tính năng này."
            ) { startActivitySafely(Intent(Settings.ACTION_SOUND_SETTINGS)) }
            "Âm thanh" -> startActivitySafely(Intent(Settings.ACTION_SOUND_SETTINGS))
            "Độ sáng" -> startActivitySafely(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "Cài đặt Android", "Cài đặt" -> startActivitySafely(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun showHudDialog(
        title: String,
        message: String,
        actionLabel: String = "ĐÃ HIỂU",
        action: (() -> Unit)? = null
    ) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(actionLabel) { _, _ -> action?.invoke() }
            .setNegativeButton(if (action != null) "ĐÓNG" else null, null)
            .create()
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            dialog.show()
        } catch (_: Exception) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showStatsDetails() {
        val body = "CPU: $cpuText\nRAM: $ramText\nTần số màn hình: $hzText\nFPS: Chưa có quyền đo khung hình của game khác.\n\nCPU và RAM sẽ cập nhật khi bảng HUD đang mở."
        showHudDialog("THEO DÕI HỆ THỐNG", body)
    }

    private fun toggleDoNotDisturb() {
        if (Build.VERSION.SDK_INT < 23) {
            showHudDialog("CHẾ ĐỘ IM LẶNG", "Thiết bị chưa hỗ trợ điều khiển chế độ Không làm phiền từ Nero.")
            return
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) {
            showHudDialog(
                "CẤP QUYỀN KHÔNG LÀM PHIỀN",
                "Cho phép Nero truy cập chính sách thông báo để bật hoặc tắt chế độ Không làm phiền.",
                "MỞ CÀI ĐẶT"
            ) { startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            return
        }
        try {
            val enabling = manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE
            manager.setInterruptionFilter(
                if (enabling) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            Toast.makeText(
                this,
                if (enabling) "Đã bật Không làm phiền." else "Đã tắt Không làm phiền.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: SecurityException) {
            showHudDialog("KHÔNG LÀM PHIỀN", "Android từ chối quyền điều khiển chế độ Không làm phiền.")
        }
    }

    private fun showGamePicker() {
        val apps = launcherApps()
        val selected = (prefs.getStringSet(KEY_GAMES, emptySet()) ?: emptySet()).toMutableSet()
        if (selected.isEmpty()) {
            apps.filter { isSystemGame(it.activityInfo.packageName) }
                .forEach { selected.add(it.activityInfo.packageName) }
        }
        val labels = apps.map { app ->
            val name = app.loadLabel(packageManager).toString()
            if (isSystemGame(app.activityInfo.packageName)) "$name  ·  Game" else name
        }.toTypedArray()
        val checked = BooleanArray(apps.size) { selected.contains(apps[it].activityInfo.packageName) }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("TRÒ CHƠI CỦA TÔI")
            .setMessage("Chọn game để Nero tự hiện tay nắm khi game đang chạy.")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].activityInfo.packageName
                if (isChecked) selected.add(pkg) else selected.remove(pkg)
            }
            .setPositiveButton("LƯU GAME") { _, _ ->
                prefs.edit().putStringSet(KEY_GAMES, selected.toSet()).apply()
                closePanel()
                openPanel()
                Toast.makeText(this, "Đã cập nhật danh sách game", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("CHỌN GAME HỆ THỐNG") { _, _ ->
                val systemGames = apps.filter { isSystemGame(it.activityInfo.packageName) }
                    .map { it.activityInfo.packageName }.toSet()
                prefs.edit().putStringSet(KEY_GAMES, systemGames).apply()
                closePanel()
                openPanel()
            }
            .setNegativeButton("HỦY", null)
            .create()
        try {
            if (Build.VERSION.SDK_INT >= 26) dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được danh sách game.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHudSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        val currentLength = prefs.getInt(KEY_HANDLE_LENGTH, 54).coerceIn(24, 104)
        val currentWidth = prefs.getInt(KEY_HANDLE_WIDTH, 3).coerceIn(1, 11)
        val currentAlpha = prefs.getInt(KEY_HANDLE_ALPHA, 100).coerceIn(10, 100)
        val lengthValue = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; text = "Chiều dài: $currentLength" }
        val lengthSeek = android.widget.SeekBar(this).apply {
            max = 80
            progress = currentLength - 24
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    lengthValue.text = "Chiều dài: ${progress + 24}"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        val widthValue = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; text = "Độ dày: $currentWidth" }
        val widthSeek = android.widget.SeekBar(this).apply {
            max = 10
            progress = currentWidth - 1
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    widthValue.text = "Độ dày: ${progress + 1}"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        val alphaValue = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; text = "Độ đậm: $currentAlpha%" }
        val alphaSeek = android.widget.SeekBar(this).apply {
            max = 90
            progress = currentAlpha - 10
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    alphaValue.text = "Độ đậm: ${progress + 10}%"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        content.addView(lengthValue); content.addView(lengthSeek)
        content.addView(widthValue); content.addView(widthSeek)
        content.addView(alphaValue); content.addView(alphaSeek)
        content.addView(TextView(this).apply {
            text = "MÀU TAY NẮM"
            setTextColor(0xFFBFC8D8.toInt())
            textSize = 11f
            setPadding(0, dp(12), 0, dp(8))
        })
        var chosenColor = prefs.getInt(KEY_HANDLE_COLOR, Color.WHITE)
        val colors = listOf(
            "Trắng" to Color.WHITE,
            "Tím Nero" to 0xFFB36BFF.toInt(),
            "Xanh Neon" to 0xFF38DDF8.toInt(),
            "Đỏ" to 0xFFFF5B75.toInt(),
            "Vàng" to 0xFFFFD166.toInt()
        )
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colors.forEach { (label, color) ->
            colorRow.addView(TextView(this).apply {
                text = "●\n$label"
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(color)
                setPadding(dp(3), dp(7), dp(3), dp(7))
                background = rounded(0xFF202A3C.toInt(), 9)
                setOnClickListener { chosenColor = color }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        content.addView(colorRow)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("TÙY CHỈNH NERO HUD")
            .setView(content)
            .setPositiveButton("ÁP DỤNG") { _, _ ->
                prefs.edit()
                    .putInt(KEY_HANDLE_LENGTH, lengthSeek.progress + 24)
                    .putInt(KEY_HANDLE_WIDTH, widthSeek.progress + 1)
                    .putInt(KEY_HANDLE_ALPHA, alphaSeek.progress + 10)
                    .putInt(KEY_HANDLE_COLOR, chosenColor)
                    .apply()
                removeGrip()
                showGrip()
            }
            .setNegativeButton("HỦY", null)
            .create()
        try {
            if (Build.VERSION.SDK_INT >= 26) dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được cài đặt HUD.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNotice(title: String, message: String) {
        Toast.makeText(this, "$title: $message", Toast.LENGTH_LONG).show()
    }

    private fun startActivitySafely(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(intent) } catch (_: Exception) {
            Toast.makeText(this, "Thiết bị không hỗ trợ mục này.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchApp(pkg: String, floating: Boolean) {
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        closePanel()
        try {
            if (floating && Build.VERSION.SDK_INT >= 24) {
                val w = resources.displayMetrics.widthPixels
                val h = resources.displayMetrics.heightPixels
                val rect = Rect(dp(28), dp(50), (w * 0.74f).toInt(), (h * 0.74f).toInt())
                val options = ActivityOptions.makeBasic()
                options.launchBounds = rect
                startActivity(launch, options.toBundle())
                Toast.makeText(this, "Đã gửi yêu cầu cửa sổ nổi. Nếu ứng dụng không hỗ trợ đa cửa sổ, One UI sẽ mở toàn màn hình.", Toast.LENGTH_LONG).show()
            } else {
                startActivity(launch)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được ứng dụng.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showShortcutPicker() {
        val apps = launcherApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy ứng dụng có thể mở.", Toast.LENGTH_SHORT).show()
            return
        }
        val current = (prefs.getStringSet(KEY_SHORTCUTS, emptySet()) ?: emptySet()).toMutableSet()
        val selected = current.toMutableSet()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(5))
        }
        content.addView(text("Chọn ứng dụng để ghim vào dải biểu tượng bên trái. Có thể chọn nhiều ứng dụng.", 11f, 0xFFB9C4D9.toInt(), false)
            .apply { setPadding(0, 0, 0, dp(9)) })
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        apps.forEach { app ->
            val pkg = app.activityInfo.packageName
            val checked = selected.contains(pkg)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(if (checked) 0xFF25365A.toInt() else 0xFF141D2C.toInt(), 11)
                setPadding(dp(9), dp(7), dp(8), dp(7))
            }
            row.addView(ImageView(this).apply {
                setImageDrawable(app.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
            row.addView(text(app.loadLabel(packageManager).toString(), 11f, Color.WHITE, true),
                LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(9) })
            val check = android.widget.CheckBox(this).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    if (checked) 0xFFAA70FF.toInt() else 0xFF8090AA.toInt()
                )
                isChecked = checked
                setOnCheckedChangeListener { _, on ->
                    if (on) selected.add(pkg) else selected.remove(pkg)
                    row.background = rounded(if (on) 0xFF25365A.toInt() else 0xFF141D2C.toInt(), 11)
                    buttonTintList = android.content.res.ColorStateList.valueOf(
                        if (on) 0xFFAA70FF.toInt() else 0xFF8090AA.toInt()
                    )
                }
            }
            row.addView(check)
            row.setOnClickListener { check.isChecked = !check.isChecked }
            rows.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        }
        scroll.addView(rows)
        content.addView(scroll, LinearLayout.LayoutParams(-1, dp(360)))
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("THÊM ỨNG DỤNG")
            .setView(content)
            .setPositiveButton("LƯU ỨNG DỤNG") { _, _ ->
                prefs.edit().putStringSet(KEY_SHORTCUTS, selected.toSet()).apply()
                closePanel()
                openPanel()
                Toast.makeText(this, "Đã lưu ${selected.size} ứng dụng vào dải HUD.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("HIỆN TẤT CẢ") { _, _ ->
                prefs.edit().remove(KEY_SHORTCUTS).apply()
                closePanel()
                openPanel()
            }
            .setNegativeButton("HỦY", null)
            .create()
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            dialog.show()
            dialog.window?.setLayout(dp(340).coerceAtMost(resources.displayMetrics.widthPixels - dp(24)), -2)
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được danh sách ứng dụng.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launcherApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return apps.filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
    }

    private fun applicationIcon(pkg: String): android.graphics.drawable.Drawable {
        return try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) {
            getDrawable(android.R.drawable.sym_def_app_icon)!!
        }
    }

    private fun applicationLabel(pkg: String): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) { pkg }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Nero Game Turbo", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Nero Game Turbo")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Nero Game Turbo")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun ripple(color: Int, radius: Int): android.graphics.drawable.Drawable =
        android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x557E4ACB),
            rounded(color, radius),
            rounded(Color.WHITE, radius)
        )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
