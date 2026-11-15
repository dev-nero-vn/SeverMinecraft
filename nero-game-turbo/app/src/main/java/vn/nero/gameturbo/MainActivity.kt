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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(HudOverlayService.PREFS, Context.MODE_PRIVATE) }
    private lateinit var root: FrameLayout
    private lateinit var toggleButton: Button
    private var permissionStep = 0
    private var returningFromPermissionSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = 0xFF080713.toInt()
        window.navigationBarColor = 0xFF080713.toInt()
        window.decorView.systemUiVisibility = 0
        showBrandIntro()
    }

    override fun onResume() {
        super.onResume()
        if (!::toggleButton.isInitialized) return
        refreshButton()

        if (prefs.getBoolean(HudOverlayService.KEY_HUD_ENABLED, false)) {
            when {
                !hasOverlayPermission() -> {
                    if (returningFromPermissionSettings) disableHud("Chưa cấp quyền hiển thị trên ứng dụng khác.")
                }
                !hasUsageAccess() -> {
                    if (permissionStep == 1) {
                        permissionStep = 2
                        requestUsagePermission()
                    } else if (returningFromPermissionSettings) {
                        disableHud("Chưa cấp quyền nhận diện trò chơi.")
                    }
                }
                else -> {
                    permissionStep = 0
                    returningFromPermissionSettings = false
                    startHudService()
                    refreshButton()
                }
            }
        } else {
            returningFromPermissionSettings = false
            permissionStep = 0
        }
    }

    private fun showBrandIntro() {
        val intro = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF080713.toInt(), 0xFF1C1034.toInt(), 0xFF071622.toInt())
            )
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.nero_logo_animated)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Nero"
        }
        center.addView(logo, LinearLayout.LayoutParams(dp(110), dp(110)))
        center.addView(android.widget.TextView(this).apply {
            text = "N E R O"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .2f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, 0)
        })
        intro.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(intro)
        (logo.drawable as? android.graphics.drawable.AnimatedVectorDrawable)?.start()

        intro.alpha = 0f
        intro.scaleX = .94f
        intro.scaleY = .94f
        intro.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340).start()

        intro.postDelayed({
            buildSingleButtonScreen()
            root.alpha = 0f
            root.scaleX = .98f
            root.scaleY = .98f
            setContentView(root)
            root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).start()
            refreshButton()
        }, 800L)
    }

    private fun buildSingleButtonScreen() {
        root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF080713.toInt(), 0xFF111327.toInt(), 0xFF080B14.toInt())
            )
        }

        toggleButton = Button(this).apply {
            textSize = 14f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(24), dp(15), dp(24), dp(15))
            elevation = dp(8).toFloat()
            setOnClickListener {
                animate().scaleX(.97f).scaleY(.97f).setDuration(70).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    toggleHud()
                }.start()
            }
        }
        val buttonWidth = (resources.displayMetrics.widthPixels * .70f).toInt().coerceAtLeast(dp(220))
        root.addView(toggleButton, FrameLayout.LayoutParams(buttonWidth, dp(56), Gravity.CENTER))
    }

    private fun toggleHud() {
        if (prefs.getBoolean(HudOverlayService.KEY_HUD_ENABLED, false)) {
            disableHud("Đã tắt HUD.")
            return
        }

        prefs.edit().putBoolean(HudOverlayService.KEY_HUD_ENABLED, true).apply()
        returningFromPermissionSettings = true
        continueEnableFlow()
    }

    private fun continueEnableFlow() {
        when {
            !hasOverlayPermission() -> {
                permissionStep = 1
                Toast.makeText(this, "Cho phép Nero hiển thị vạch HUD trên game.", Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    disableHud("Không mở được màn hình cấp quyền.")
                }
            }
            !hasUsageAccess() -> {
                permissionStep = 2
                requestUsagePermission()
            }
            else -> {
                permissionStep = 0
                returningFromPermissionSettings = false
                startHudService()
                refreshButton()
                Toast.makeText(this, "HUD đã bật. Mở game đã thêm để sử dụng.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestUsagePermission() {
        Toast.makeText(this, "Cho phép Nero nhận diện game đang mở.", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            disableHud("Không mở được màn hình quyền sử dụng.")
        }
    }

    private fun startHudService() {
        if (!prefs.getBoolean(HudOverlayService.KEY_HUD_ENABLED, false)) return
        val intent = Intent(this, HudOverlayService::class.java).setAction(HudOverlayService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } catch (_: Exception) {
            disableHud("Android không cho phép khởi chạy dịch vụ HUD.")
        }
    }

    private fun disableHud(message: String) {
        prefs.edit().putBoolean(HudOverlayService.KEY_HUD_ENABLED, false).apply()
        stopService(Intent(this, HudOverlayService::class.java))
        permissionStep = 0
        returningFromPermissionSettings = false
        refreshButton()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun refreshButton() {
        if (!::toggleButton.isInitialized) return
        val enabled = prefs.getBoolean(HudOverlayService.KEY_HUD_ENABLED, false)
        toggleButton.text = if (enabled) "TẮT HUD" else "BẬT HUD"
        toggleButton.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (enabled) intArrayOf(0xFF139B72.toInt(), 0xFF087052.toInt())
            else intArrayOf(0xFF9D4DFA.toInt(), 0xFF5634C8.toInt())
        ).apply {
            cornerRadius = dp(17).toFloat()
        }
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
