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
    private val panelMetricValues = mutableListOf<TextView>()
    private var cpuText = "—"
    private var ramText = "—"
    private var hzText = "—"

    private val monitor = object : Runnable {
        override fun run() {
            detectForegroundGame()
            val stats = DeviceStats.read(this@HudOverlayService)
            cpuText = stats.cpuText
            ramText = stats.ramText
            hzText = stats.refreshText
            if (panelOpen) refreshPanelStats()
            handler.postDelayed(this, 1100L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Đang chờ game"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                .queryEvents(now - 12_000L, now)
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
                if (resumed && !event.packageName.isNullOrBlank() && !isIgnoredPackage(event.packageName)) {
                    latestPackage = event.packageName
                }
            }
        }

        if (latestPackage == null) {
            latestPackage = try {
                (getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager)
                    .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 90_000L, now)
                    .asSequence()
                    .filter { !isIgnoredPackage(it.packageName) }
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
        if (currentGamePackage == null) return
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
        val visibleApps = if (savedShortcuts.isEmpty()) allLauncherApps.take(14)
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
                var dragged = false
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            dragged = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!dragged && event.rawX - downX > dp(22)) {
                                dragged = true
                                launchApp(app.activityInfo.packageName, true)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!dragged && kotlin.math.abs(event.rawX - downX) < dp(12) && kotlin.math.abs(event.rawY - downY) < dp(12)) {
                                launchApp(app.activityInfo.packageName, false)
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> true
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
        body.addView(header, LinearLayout.LayoutParams(-1, dp(38)))

        panelMetricValues.clear()
        val statRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        statRow.addView(statTile("CPU", cpuText), LinearLayout.LayoutParams(0, dp(58), 1f))
        statRow.addView(statTile("FPS", "—"), LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(5) })
        statRow.addView(statTile("RAM", ramText), LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(5) })
        statRow.addView(statTile("GPU", "—"), LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(5) })
        body.addView(statRow, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(5) })

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeRow.addView(actionChip("Hiệu năng", true) { showNotice("Chế độ hiệu năng", "Ứng dụng thường không thể tự ép xung CPU/GPU hoặc thay đổi Game Optimizing Service của Samsung.") }, LinearLayout.LayoutParams(0, dp(34), 1f))
        modeRow.addView(actionChip("Cân bằng", false) { showNotice("Cân bằng", "Đây là tùy chọn giao diện; chế độ hiệu năng hệ thống do Samsung quản lý.") }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { leftMargin = dp(6) })
        modeRow.addView(actionChip("Tiết kiệm pin", false) { startActivitySafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { leftMargin = dp(6) })
        body.addView(modeRow, LinearLayout.LayoutParams(-1, dp(34)).apply { topMargin = dp(6) })

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, dp(4))
        }
        val toolRows = listOf(
            listOf("Chế độ game", "Theo dõi hệ thống"),
            listOf("Menu ứng dụng", "Tăng cường cảm ứng"),
            listOf("Bảo vệ cảm ứng", "Chụp màn hình"),
            listOf("Ghi màn hình", "Chặn thông báo"),
            listOf("Đổi giọng nói", "Hẹn giờ"),
            listOf("Âm thanh", "Độ sáng"),
            listOf("Cài đặt Android", "Chế độ im lặng"),
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
        // Vùng công cụ bên phải phải cuộn dọc độc lập với dải ứng dụng bên trái.
        val toolsScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
        }
        toolsScroll.addView(tools, ScrollView.LayoutParams(-1, -2))
        body.addView(toolsScroll, LinearLayout.LayoutParams(-1, 0, 1f))

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
        body.addView(footer, LinearLayout.LayoutParams(-1, dp(38)))
        root.addView(body, LinearLayout.LayoutParams(0, -1, 1f))

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        // Compact game-side panel: match the reference layout (about 45% width, nearly full height).
        val width = (screenWidth * 0.46f).toInt().coerceAtLeast(dp(320)).coerceAtMost(screenWidth - dp(12))
        val height = (screenHeight * 0.94f).toInt().coerceAtLeast(dp(270)).coerceAtMost(screenHeight - dp(16))
        val params = WindowManager.LayoutParams(
            width, height, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = dp(8)
            y = dp(8)
        }
        try {
            windowManager.addView(root, params)
            panelView = root
            panelOpen = true
        } catch (_: Exception) {
            panelOpen = false
            Toast.makeText(this, "Không thể mở bảng HUD. Hãy kiểm tra quyền hiển thị trên ứng dụng khác.", Toast.LENGTH_LONG).show()
        }
    }

    private fun closePanel() {
        panelView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        panelView = null
        panelOpen = false
        panelMetricValues.clear()
    }

    private fun refreshPanelStats() {
        if (panelMetricValues.size >= 4) {
            panelMetricValues[0].text = cpuText
            panelMetricValues[1].text = "—"
            panelMetricValues[2].text = ramText
            panelMetricValues[3].text = "—"
        }
    }

    private fun statTile(title: String, value: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(0xFF171D2C.toInt(), 12)
            addView(text(title, 9f, 0xFFB96BFF.toInt(), true))
        }
        val valueView = text(value, 17f, Color.WHITE, true)
        card.addView(valueView)
        panelMetricValues.add(valueView)
        card.addView(text(when (title) { "FPS", "GPU" -> "Cần quyền hệ thống"; else -> "Theo dõi" }, 7f, 0xFF8D9AB0.toInt(), false))
        return card
    }

    private fun actionChip(name: String, selected: Boolean, action: () -> Unit): View =
        text(name, 9f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(if (selected) 0xFF7937BB.toInt() else 0xFF252C3B.toInt(), 18)
            setOnClickListener { action() }
        }

    private fun toolButton(name: String, action: () -> Unit): View =
        text(name, 10f, 0xFFE7ECF6.toInt(), true).apply {
            gravity = Gravity.CENTER
            background = ripple(0xFF252C3B.toInt(), 13)
            setOnClickListener { action() }
        }

    private fun onToolSelected(name: String) {
        when (name) {
            "Menu ứng dụng" -> showNotice("Ứng dụng", "Chạm logo để mở ứng dụng. Giữ logo để yêu cầu mở bằng cửa sổ nổi của One UI.")
            "Theo dõi hệ thống" -> showNotice("Theo dõi hệ thống", "CPU và RAM được đọc từ Android. FPS/GPU thực không được API Android công khai cho ứng dụng thường.")
            "Chế độ game" -> startActivitySafely(Intent(Settings.ACTION_SETTINGS))
            "Tăng cường cảm ứng", "Bảo vệ cảm ứng" -> showNotice(name, "Android không cấp cho ứng dụng bên thứ ba quyền điều khiển toàn bộ lớp cảm ứng của game khác.")
            "Chụp màn hình", "Chụp" -> showNotice("Chụp màn hình", "Dùng phím Nguồn + Giảm âm lượng hoặc công cụ Chụp màn hình của Samsung.")
            "Ghi màn hình", "Ghi" -> showNotice("Ghi màn hình", "Mở Bảng điều khiển nhanh của Samsung và chọn Ghi màn hình.")
            "Chặn thông báo" -> startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            "Hẹn giờ" -> startActivitySafely(
                Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                    .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, 30 * 60)
                    .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Nero Game Turbo")
            )
            "Đổi giọng nói" -> showNotice("Đổi giọng nói", "Đổi giọng nói trong ứng dụng game cần quyền âm thanh đặc quyền của hệ thống; Nero không giả lập nút này.")
            "Âm thanh" -> startActivitySafely(Intent(Settings.ACTION_SOUND_SETTINGS))
            "Độ sáng" -> startActivitySafely(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "Cài đặt Android", "Cài đặt" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Chế độ im lặng" -> startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
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
        val current = (prefs.getStringSet(KEY_SHORTCUTS, emptySet()) ?: emptySet()).toMutableSet()
        if (current.isEmpty()) {
            apps.take(14).forEach { current.add(it.activityInfo.packageName) }
        }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val checked = BooleanArray(apps.size) { current.contains(apps[it].activityInfo.packageName) }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("THÊM / CHỈNH SỬA ỨNG DỤNG")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].activityInfo.packageName
                if (isChecked) current.add(pkg) else current.remove(pkg)
            }
            .setPositiveButton("LƯU") { _, _ ->
                prefs.edit().putStringSet(KEY_SHORTCUTS, current.toSet()).apply()
                closePanel()
                openPanel()
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
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            dialog.show()
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
