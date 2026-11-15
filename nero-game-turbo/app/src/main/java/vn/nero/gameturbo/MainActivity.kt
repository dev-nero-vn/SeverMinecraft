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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy {
        getSharedPreferences(HudOverlayService.PREFS, Context.MODE_PRIVATE)
    }
    private lateinit var toggleButton: Button
    private var permissionStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = Color.rgb(8, 10, 18)
        window.navigationBarColor = Color.rgb(8, 10, 18)
        window.decorView.systemUiVisibility = 0
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshButton()
        continuePermissionFlow()
    }

    private fun buildScreen() {
        val root = FrameLayout(this).apply {
            setBackground(GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF080B14.toInt(), 0xFF17112A.toInt(), 0xFF090C16.toInt())
            ))
        }

        toggleButton = Button(this).apply {
            text = "BẬT HUD"
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(28), dp(18), dp(28), dp(18))
            background = buttonBackground(false)
            elevation = dp(10).toFloat()
            setOnClickListener { toggleHud() }
        }
        val buttonWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        root.addView(toggleButton, FrameLayout.LayoutParams(buttonWidth, dp(58), Gravity.CENTER))

        setContentView(root)
        root.alpha = 0f
        root.scaleX = 0.985f
        root.scaleY = 0.985f
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).start()
    }

    private fun toggleHud() {
        if (prefs.getBoolean("hud_enabled", false)) {
            prefs.edit().putBoolean("hud_enabled", false).apply()
            stopService(Intent(this, HudOverlayService::class.java))
            permissionStep = 0
            refreshButton()
            Toast.makeText(this, "Đã tắt HUD", Toast.LENGTH_SHORT).show()
        } else {
            enableHudFlow()
        }
    }

    private fun enableHudFlow() {
        prefs.edit().putBoolean("hud_enabled", true).apply()
        when {
            !hasOverlayPermission() -> {
                permissionStep = 1
                Toast.makeText(this, "Hãy cho phép Nero hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            !hasUsageAccess() -> {
                permissionStep = 2
                Toast.makeText(this, "Hãy bật quyền truy cập dữ liệu sử dụng để nhận diện game", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            else -> {
                permissionStep = 0
                startHudService()
                Toast.makeText(this, "HUD đã bật. Hãy mở trò chơi.", Toast.LENGTH_SHORT).show()
            }
        }
        refreshButton()
    }

    private fun continuePermissionFlow() {
        if (!prefs.getBoolean("hud_enabled", false)) return
        if (!hasOverlayPermission()) {
            refreshButton()
            return
        }
        if (!hasUsageAccess()) {
            if (permissionStep == 1) {
                permissionStep = 2
                Toast.makeText(this, "Tiếp theo, bật quyền truy cập dữ liệu sử dụng cho Nero", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            refreshButton()
            return
        }
        permissionStep = 0
        startHudService()
        refreshButton()
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

    private fun refreshButton() {
        if (!::toggleButton.isInitialized) return
        val enabled = prefs.getBoolean("hud_enabled", false)
        toggleButton.text = if (enabled) "TẮT HUD" else "BẬT HUD"
        toggleButton.background = buttonBackground(enabled)
    }

    private fun buttonBackground(enabled: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        if (enabled) intArrayOf(0xFF159B76.toInt(), 0xFF11765D.toInt())
        else intArrayOf(0xFF9A45EB.toInt(), 0xFF6027C5.toInt())
    ).apply { cornerRadius = dp(18).toFloat() }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
