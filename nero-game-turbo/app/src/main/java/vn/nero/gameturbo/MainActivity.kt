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
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(HudOverlayService.PREFS, Context.MODE_PRIVATE) }
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private var permissionStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = Color.rgb(7, 9, 17)
        window.navigationBarColor = Color.rgb(7, 9, 17)
        window.decorView.systemUiVisibility = 0
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        continuePermissionFlow()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(30), dp(28), dp(30))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF080B14.toInt(), 0xFF17112A.toInt(), 0xFF090C16.toInt())
            )
        }

        val logo = TextView(this).apply {
            text = "N"
            textSize = 42f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF9B43E8.toInt(), 0xFF5421A8.toInt())
            ).apply { cornerRadius = dp(25).toFloat() }
            elevation = dp(18).toFloat()
        }
        root.addView(logo, LinearLayout.LayoutParams(dp(88), dp(88)).apply {
            bottomMargin = dp(24)
        })

        val title = TextView(this).apply {
            text = "NERO GAME TURBO"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val subtitle = TextView(this).apply {
            text = "BẢNG ĐIỀU KHIỂN KHI CHƠI GAME"
            textSize = 10f
            setTextColor(0xFF9AA6BD.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.13f
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(9)
            bottomMargin = dp(44)
        })

        statusText = TextView(this).apply {
            text = "Đang kiểm tra quyền..."
            textSize = 12f
            setTextColor(0xFFBEC7D8.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(18)
        })

        toggleButton = Button(this).apply {
            text = "BẬT HUD"
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF9A45EB.toInt(), 0xFF6027C5.toInt())
            ).apply { cornerRadius = dp(18).toFloat() }
            elevation = dp(12).toFloat()
            setOnClickListener { toggleHud() }
        }
        root.addView(toggleButton, LinearLayout.LayoutParams(-1, dp(62)))

        val footer = TextView(this).apply {
            text = "Tay nắm HUD chỉ xuất hiện khi game được nhận diện"
            textSize = 10f
            setTextColor(0xFF7F8AA0.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(20)
        })

        setContentView(root)
        root.alpha = 0f
        root.scaleX = 0.96f
        root.scaleY = 0.96f
        root.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420).start()
    }

    private fun toggleHud() {
        if (prefs.getBoolean("hud_enabled", false)) {
            prefs.edit().putBoolean("hud_enabled", false).apply()
            stopService(Intent(this, HudOverlayService::class.java))
            permissionStep = 0
            refreshState()
            Toast.makeText(this, "Đã tắt Nero Game Turbo HUD", Toast.LENGTH_SHORT).show()
        } else {
            enableHudFlow()
        }
    }

    private fun enableHudFlow() {
        prefs.edit().putBoolean("hud_enabled", true).apply()
        when {
            !hasOverlayPermission() -> {
                permissionStep = 1
                Toast.makeText(this, "Cho phép Nero hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            !hasUsageAccess() -> {
                permissionStep = 2
                Toast.makeText(this, "Cho phép Nero truy cập dữ liệu sử dụng để nhận diện game", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            else -> {
                permissionStep = 0
                startHudService()
                Toast.makeText(this, "HUD đã bật. Mở game để sử dụng.", Toast.LENGTH_LONG).show()
            }
        }
        refreshState()
    }

    private fun continuePermissionFlow() {
        if (!prefs.getBoolean("hud_enabled", false)) return

        if (!hasOverlayPermission()) {
            refreshState()
            return
        }

        if (!hasUsageAccess()) {
            if (permissionStep == 1) {
                permissionStep = 2
                Toast.makeText(this, "Tiếp theo, bật quyền Truy cập dữ liệu sử dụng cho Nero", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            refreshState()
            return
        }

        permissionStep = 0
        startHudService()
        refreshState()
    }

    private fun startHudService() {
        val intent = Intent(this, HudOverlayService::class.java).setAction(HudOverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

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

    private fun refreshState() {
        if (!::statusText.isInitialized || !::toggleButton.isInitialized) return
        val enabled = prefs.getBoolean("hud_enabled", false)
        val overlay = hasOverlayPermission()
        val usage = hasUsageAccess()

        statusText.text = when {
            !enabled -> "HUD đang tắt"
            !overlay -> "Cần cấp quyền hiển thị trên ứng dụng khác"
            !usage -> "Cần cấp quyền truy cập dữ liệu sử dụng"
            else -> "HUD đã bật · Sẵn sàng nhận diện game"
        }
        toggleButton.text = if (enabled) "TẮT HUD" else "BẬT HUD"
        toggleButton.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (enabled) intArrayOf(0xFF1B9B75.toInt(), 0xFF13785F.toInt())
            else intArrayOf(0xFF9A45EB.toInt(), 0xFF6027C5.toInt())
        ).apply { cornerRadius = dp(18).toFloat() }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
