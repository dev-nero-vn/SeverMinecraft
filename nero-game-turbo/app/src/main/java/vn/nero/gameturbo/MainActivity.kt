package vn.nero.gameturbo

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(HudOverlayService.PREFS, Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var page = 0
    private var query = ""
    private var permissionDialogOpen = false
    private var permissionPrompted = false
    private var apps: List<ResolveInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = 0xFF080B14.toInt()
        window.navigationBarColor = 0xFF080B14.toInt()
        window.decorView.systemUiVisibility = 0
        apps = loadApps()
        showIntro()
    }

    override fun onResume() {
        super.onResume()
        // Sau khi quay lại từ Cài đặt Android, tiếp tục bước cấp quyền kế tiếp nếu còn thiếu.
        permissionPrompted = false
        permissionDialogOpen = false
        apps = loadApps()
        if (::root.isInitialized) render()
        handler.postDelayed({ ensureGameSpaceService() }, 300L)
    }

    private fun showIntro() {
        val intro = FrameLayout(this).apply {
            background = gradient(intArrayOf(0xFF080713.toInt(), 0xFF201039.toInt(), 0xFF071A29.toInt()), 0)
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.nero_logo_animated)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Biểu trưng Nero Game Space"
        }
        center.addView(logo, LinearLayout.LayoutParams(dp(116), dp(116)))
        center.addView(txt("N E R O", 23f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(3)) })
        center.addView(txt("GAME SPACE", 10f, 0xFFB9A7E8.toInt(), true).apply { gravity = Gravity.CENTER; letterSpacing = .22f })
        intro.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(intro)
        (logo.drawable as? android.graphics.drawable.AnimatedVectorDrawable)?.start()
        intro.alpha = 0f
        intro.scaleX = .94f
        intro.scaleY = .94f
        intro.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(360).start()
        handler.postDelayed({
            buildHome()
            setContentView(root)
            root.alpha = 0f
            root.translationY = dp(10).toFloat()
            root.animate().alpha(1f).translationY(0f).setDuration(260).start()
            ensureGameSpaceService()
        }, 850L)
    }

    private fun buildHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(11), dp(15), dp(10))
            background = gradient(intArrayOf(0xFF080B14.toInt(), 0xFF11182A.toInt(), 0xFF090D18.toInt()), 0)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.nero_logo_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), 0, 0, 0) }
        brand.addView(txt("NERO GAME SPACE", 16f, Color.WHITE, true))
        brand.addView(txt("TRUNG TÂM TRÒ CHƠI", 9f, 0xFF95A2BF.toInt(), true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(txt("● SẴN SÀNG", 9f, 0xFF7BE7B7.toInt(), true).apply {
            background = rounded(0xFF12382F.toInt(), 20); setPadding(dp(9), dp(7), dp(9), dp(7))
        })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(50)))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(intArrayOf(0xFF352158.toInt(), 0xFF1D2D4B.toInt(), 0xFF111A2C.toInt()), 18)
            setPadding(dp(15), dp(13), dp(15), dp(13))
            elevation = dp(4).toFloat()
        }
        hero.addView(txt("GAME SPACE • NERO ENGINE", 9f, 0xFFD0B8FF.toInt(), true))
        hero.addView(txt("Sẵn sàng vào trận.", 19f, Color.WHITE, true).apply { setPadding(0, dp(5), 0, dp(3)) })
        hero.addView(txt("Quản lý trò chơi, ứng dụng nhanh và công cụ trong trận.", 10f, 0xFFC0C9DA.toInt(), false))
        val heroButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        heroButtons.addView(button("＋ THÊM TRÒ CHƠI", true) { pickGames() }, LinearLayout.LayoutParams(0, dp(38), 1f))
        heroButtons.addView(button("TẤT CẢ ỨNG DỤNG", false) { setPage(1) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(7) })
        hero.addView(heroButtons, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(11) })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(0xFF121A29.toInt(), 13)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        tabs.addView(tab("TRÒ CHƠI", 0), LinearLayout.LayoutParams(0, dp(36), 1f))
        tabs.addView(tab("ỨNG DỤNG", 1), LinearLayout.LayoutParams(0, dp(36), 1f))
        tabs.addView(tab("CÀI ĐẶT", 2), LinearLayout.LayoutParams(0, dp(36), 1f))
        root.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        search = EditText(this).apply {
            hint = "Tìm trò chơi hoặc ứng dụng..."
            setHintTextColor(0xFF71809C.toInt())
            setTextColor(Color.WHITE)
            textSize = 12f
            singleLine = true
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(0xFF151E2E.toInt(), 11)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); render() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(10) })

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(16)) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        render()
    }

    private fun setPage(newPage: Int) {
        page = newPage
        query = ""
        if (::search.isInitialized) search.setText("")
        render()
        val tabs = root.getChildAt(2) as? LinearLayout ?: return
        for (i in 0 until tabs.childCount) {
            (tabs.getChildAt(i) as? TextView)?.apply {
                setTextColor(if (i == page) Color.WHITE else 0xFF95A2BB.toInt())
                background = if (i == page) rounded(0xFF7542BC.toInt(), 10) else null
            }
        }
    }

    private fun tab(title: String, target: Int) = txt(title, 9f, if (page == target) Color.WHITE else 0xFF95A2BB.toInt(), true).apply {
        gravity = Gravity.CENTER
        background = if (page == target) rounded(0xFF7542BC.toInt(), 10) else null
        setOnClickListener { setPage(target) }
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        when (page) {
            0 -> renderGames()
            1 -> renderAllApps()
            else -> renderSettings()
        }
    }

    private fun renderGames() {
        val selected = prefs.getStringSet(HudOverlayService.KEY_GAMES, emptySet()) ?: emptySet()
        val games = apps.filter { selected.contains(it.activityInfo.packageName) && matches(it) }
        list.addView(section("TRÒ CHƠI CỦA BẠN", "${games.size} trò chơi"))
        if (games.isEmpty()) {
            list.addView(info("Thư viện game đang trống", "Thêm các game đã cài trên điện thoại. Khi mở game từ Game Space, Nero sẽ nhận diện game và hiện tay nắm HUD."), itemParams())
            list.addView(button("＋ CHỌN GAME ĐÃ CÀI", true) { pickGames() }, itemParams())
            val suggestions = apps.filter { isGame(it.activityInfo.packageName) && matches(it) }
            if (suggestions.isNotEmpty()) {
                list.addView(section("GAME ĐƯỢC ANDROID NHẬN DIỆN", "${suggestions.size} mục"))
                suggestions.take(10).forEach { list.addView(appRow(it, true), itemParams()) }
            }
        } else {
            games.forEach { list.addView(gameRow(it), itemParams()) }
            list.addView(button("+ THÊM GAME KHÁC", false) { pickGames() }, itemParams())
        }
    }

    private fun renderAllApps() {
        val visible = apps.filter { matches(it) }
        list.addView(section("TẤT CẢ ỨNG DỤNG", "${visible.size} ứng dụng"))
        visible.forEach { app ->
            val isSelected = (prefs.getStringSet(HudOverlayService.KEY_GAMES, emptySet()) ?: emptySet()).contains(app.activityInfo.packageName)
            list.addView(appRow(app, isSelected), itemParams())
        }
    }

    private fun gameRow(app: ResolveInfo): View {
        val pkg = app.activityInfo.packageName
        val row = appCard(app)
        row.addView(smallButton("CHƠI", true) { launchGame(pkg) })
        row.addView(smallButton("⋯", false) { 
            android.app.AlertDialog.Builder(this)
                .setTitle(app.loadLabel(packageManager).toString())
                .setItems(arrayOf("Mở trò chơi", "Gỡ khỏi Game Space", "Thông tin ứng dụng")) { _, which ->
                    when (which) {
                        0 -> launchGame(pkg)
                        1 -> { toggleGame(pkg); render() }
                        2 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                    }
                }.show()
        }, LinearLayout.LayoutParams(-2, dp(32)).apply { leftMargin = dp(5) })
        row.setOnClickListener { launchGame(pkg) }
        return row
    }

    private fun appRow(app: ResolveInfo, selected: Boolean): View {
        val pkg = app.activityInfo.packageName
        val row = appCard(app)
        row.addView(smallButton("MỞ", true) { launchPackage(pkg) })
        row.addView(smallButton(if (selected) "✓ GAME" else "+ GAME", false) {
            toggleGame(pkg)
            render()
        }, LinearLayout.LayoutParams(-2, dp(32)).apply { leftMargin = dp(5) })
        row.setOnClickListener { launchPackage(pkg) }
        return row
    }

    private fun appCard(app: ResolveInfo): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF151F30.toInt(), 14)
            setPadding(dp(9), dp(8), dp(8), dp(8))
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(app.loadIcon(packageManager))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val textBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), 0, dp(5), 0) }
        textBlock.addView(txt(app.loadLabel(packageManager).toString(), 12f, Color.WHITE, true))
        textBlock.addView(txt(app.activityInfo.packageName, 8f, 0xFF8F9DB7.toInt(), false))
        row.addView(textBlock, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun renderSettings() {
        list.addView(section("CÀI ĐẶT GAME SPACE", "Tùy chỉnh trải nghiệm"))
        list.addView(info("Tay nắm Game HUD", "Vạch dọc cố định xuất hiện khi game trong thư viện được mở. Vuốt vào để mở bảng công cụ."), itemParams())
        addSlider("Chiều dài vạch", HudOverlayService.KEY_HANDLE_LENGTH, 24, 104, 54)
        addSlider("Độ dày vạch", HudOverlayService.KEY_HANDLE_WIDTH, 1, 11, 3)
        addSlider("Độ đậm", HudOverlayService.KEY_HANDLE_ALPHA, 10, 100, 100, "%")
        list.addView(section("MÀU TAY NẮM", ""))
        val colors = listOf("Trắng" to Color.WHITE, "Tím Nero" to 0xFFB36BFF.toInt(), "Xanh Neon" to 0xFF38DDF8.toInt(), "Đỏ" to 0xFFFF5B75.toInt(), "Vàng" to 0xFFFFD166.toInt())
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colors.forEach { (name, color) ->
            colorRow.addView(txt("●\n$name", 9f, color, true).apply {
                gravity = Gravity.CENTER
                background = rounded(0xFF18243A.toInt(), 9)
                setPadding(dp(2), dp(8), dp(2), dp(8))
                setOnClickListener {
                    prefs.edit().putInt(HudOverlayService.KEY_HANDLE_COLOR, color).apply()
                    refreshHud()
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        list.addView(colorRow, itemParams())
        list.addView(section("QUYỀN VÀ DỊCH VỤ", ""))
        list.addView(setting("Hiển thị trên ứng dụng khác", "Cần để hiện tay nắm trong game") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, itemParams())
        list.addView(setting("Truy cập dữ liệu sử dụng", "Cần để nhận diện game đang chạy") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }, itemParams())
        list.addView(setting("Làm mới bộ nhận diện game", "Quét lại trạng thái game đang mở") { refreshHud() }, itemParams())
        list.addView(info("Lưu ý về chỉ số", "CPU, RAM, pin và tần số màn hình đọc từ Android. FPS/GPU thật và tối ưu hệ thống phụ thuộc quyền Samsung/Android; Nero không hiển thị số liệu giả."), itemParams())
    }

    private fun addSlider(title: String, key: String, min: Int, max: Int, default: Int, suffix: String = "") {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF151F30.toInt(), 13)
            setPadding(dp(12), dp(8), dp(12), dp(7))
        }
        val initial = prefs.getInt(key, default).coerceIn(min, max)
        val value = txt("$title: $initial$suffix", 10f, Color.WHITE, true)
        val seek = android.widget.SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "$title: ${progress + min}$suffix"
                    if (fromUser) prefs.edit().putInt(key, progress + min).apply()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) { refreshHud() }
            })
        }
        card.addView(value)
        card.addView(seek)
        list.addView(card, itemParams())
    }

    private fun setting(title: String, sub: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF151F30.toInt(), 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(txt(title, 11f, Color.WHITE, true))
            addView(txt(sub, 8f, 0xFF94A2BD.toInt(), false))
            setOnClickListener { action() }
        }

    private fun pickGames() {
        val labels = apps.map { app ->
            val name = app.loadLabel(packageManager).toString()
            if (isGame(app.activityInfo.packageName)) "$name  • GAME" else name
        }.toTypedArray()
        val current = (prefs.getStringSet(HudOverlayService.KEY_GAMES, emptySet()) ?: emptySet()).toMutableSet()
        val checked = BooleanArray(apps.size) { current.contains(apps[it].activityInfo.packageName) }
        android.app.AlertDialog.Builder(this)
            .setTitle("THÊM TRÒ CHƠI VÀO THƯ VIỆN")
            .setMessage("Chọn các game đã cài trên điện thoại. Bạn có thể chọn nhiều game.")
            .setMultiChoiceItems(labels, checked) { _, which, on ->
                val pkg = apps[which].activityInfo.packageName
                if (on) current.add(pkg) else current.remove(pkg)
            }
            .setPositiveButton("LƯU THƯ VIỆN") { _, _ ->
                prefs.edit().putStringSet(HudOverlayService.KEY_GAMES, current.toSet()).apply()
                page = 0
                render()
                refreshTabs()
            }
            .setNeutralButton("CHỌN GAME ĐƯỢC NHẬN DIỆN") { _, _ ->
                val detected = apps.filter { isGame(it.activityInfo.packageName) }.map { it.activityInfo.packageName }.toSet()
                prefs.edit().putStringSet(HudOverlayService.KEY_GAMES, detected).apply()
                page = 0
                render()
                refreshTabs()
            }
            .setNegativeButton("HỦY", null)
            .show()
    }

    private fun toggleGame(pkg: String) {
        val set = (prefs.getStringSet(HudOverlayService.KEY_GAMES, emptySet()) ?: emptySet()).toMutableSet()
        if (!set.add(pkg)) set.remove(pkg)
        prefs.edit().putStringSet(HudOverlayService.KEY_GAMES, set).apply()
    }

    private fun launchGame(pkg: String) {
        ensureGameSpaceService()
        launchPackage(pkg)
    }

    private fun launchPackage(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "Ứng dụng không có màn hình mở.", Toast.LENGTH_SHORT).show()
            return
        }
        try { startActivity(intent) } catch (_: Exception) {
            Toast.makeText(this, "Không mở được ứng dụng.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureGameSpaceService() {
        if (isFinishing || permissionDialogOpen) return
        if (!hasOverlayPermission()) {
            if (!permissionPrompted) {
                permissionPrompted = true
                permissionDialogOpen = true
                android.app.AlertDialog.Builder(this)
                    .setTitle("Cấp quyền cho Game HUD")
                    .setMessage("Nero cần quyền hiển thị trên ứng dụng khác để hiện vạch HUD trong khi chơi.")
                    .setPositiveButton("CẤP QUYỀN") { _, _ ->
                        permissionDialogOpen = false
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    }
                    .setNegativeButton("ĐỂ SAU") { _, _ -> permissionDialogOpen = false }
                    .show()
            }
            return
        }
        if (!hasUsageAccess()) {
            if (!permissionPrompted) {
                permissionPrompted = true
                permissionDialogOpen = true
                android.app.AlertDialog.Builder(this)
                    .setTitle("Cho phép nhận diện game")
                    .setMessage("Quyền truy cập dữ liệu sử dụng giúp Nero biết game nào đang mở để chỉ hiện tay nắm đúng lúc.")
                    .setPositiveButton("MỞ CÀI ĐẶT") { _, _ ->
                        permissionDialogOpen = false
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    .setNegativeButton("ĐỂ SAU") { _, _ -> permissionDialogOpen = false }
                    .show()
            }
            return
        }
        permissionPrompted = false
        val intent = Intent(this, HudOverlayService::class.java).setAction(HudOverlayService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } catch (_: Exception) {}
    }

    private fun refreshHud() {
        if (!hasOverlayPermission() || !hasUsageAccess()) return
        val intent = Intent(this, HudOverlayService::class.java).setAction(HudOverlayService.ACTION_REFRESH)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } catch (_: Exception) {}
    }

    private fun hasOverlayPermission() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val result = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return result.filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
    }

    private fun isGame(pkg: String): Boolean = try {
        val info = if (Build.VERSION.SDK_INT >= 33) packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
        info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
    } catch (_: Exception) { false }

    private fun matches(app: ResolveInfo): Boolean =
        query.isBlank() || app.loadLabel(packageManager).toString().contains(query, true) || app.activityInfo.packageName.contains(query, true)

    private fun showHudToolsInfo() {
        android.app.AlertDialog.Builder(this)
            .setTitle("CÔNG CỤ TRONG TRẬN")
            .setMessage("Khi mở game đã thêm vào thư viện, vuốt từ vạch Nero ở cạnh trái để mở bảng công cụ, dải ứng dụng và thiết lập trong game.")
            .setPositiveButton("ĐÃ HIỂU", null)
            .show()
    }

    private fun refreshTabs() {
        val tabs = root.getChildAt(2) as? LinearLayout ?: return
        for (i in 0 until tabs.childCount) {
            (tabs.getChildAt(i) as? TextView)?.apply {
                setTextColor(if (i == page) Color.WHITE else 0xFF95A2BB.toInt())
                background = if (i == page) rounded(0xFF7542BC.toInt(), 10) else null
            }
        }
    }

    private fun section(title: String, detail: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(txt(title, 10f, 0xFFD6DEEF.toInt(), true), LinearLayout.LayoutParams(0, -2, 1f))
        addView(txt(detail, 8f, 0xFF8796B2.toInt(), false))
        setPadding(dp(2), dp(3), dp(2), dp(8))
    }

    private fun info(title: String, body: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(0xFF19253B.toInt(), 13)
        setPadding(dp(12), dp(11), dp(12), dp(11))
        addView(txt(title, 12f, Color.WHITE, true))
        addView(txt(body, 9f, 0xFFA7B4CB.toInt(), false).apply { setPadding(0, dp(5), 0, 0) })
    }

    private fun button(title: String, primary: Boolean, action: () -> Unit): TextView =
        txt(title, 9f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = if (primary) gradient(intArrayOf(0xFF994CFF.toInt(), 0xFF5B38C9.toInt()), 11) else rounded(0xFF27364F.toInt(), 11)
            setOnClickListener { action() }
        }

    private fun smallButton(title: String, primary: Boolean, action: () -> Unit): TextView =
        txt(title, 8f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(if (primary) 0xFF7444BA.toInt() else 0xFF29374F.toInt(), 8)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { action() }
        }

    private fun txt(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 3
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun gradient(colors: IntArray, radius: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        cornerRadius = dp(radius).toFloat()
    }

    private fun itemParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
