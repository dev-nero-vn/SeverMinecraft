package com.nexora.gamespace

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min
import kotlin.math.sin

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("nexora_settings", MODE_PRIVATE) }
    private var effectsEnabled = true
    private lateinit var gameSection: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = Color.rgb(7, 8, 18)
        window.navigationBarColor = Color.rgb(7, 8, 18)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        effectsEnabled = prefs.getBoolean("effects_enabled", true)
        showSplash()
    }

    private fun showSplash() {
        setContentView(NeonSplashView(this, effectsEnabled) { showGameHub() })
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) showGameHub() }, 2800L)
    }

    private fun showGameHub() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(7, 8, 18))
        }
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(16))
            setBackgroundColor(Color.rgb(12, 13, 29))
        }
        rail.addView(text("NEXORA", 23f, CYAN, true))
        rail.addView(text("GAME SPACE", 10f, MUTED, true).apply { setPadding(0, dp(3), 0, dp(28)) })
        rail.addView(text("⌂  TRUNG TÂM", 12f, Color.WHITE, true))
        rail.addView(text("▦  THƯ VIỆN GAME", 12f, PURPLE, true).apply { setPadding(0, dp(18), 0, dp(18)) })
        rail.addView(text("EXTREME GAMING", 10f, MUTED, true))
        rail.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        rail.addView(text("NEXORA • 0.1.0", 10f, MUTED))
        root.addView(rail, LinearLayout.LayoutParams(dp(205), -1))

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(17), dp(22), dp(14))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(text("GAMING HUB", 23f, Color.WHITE, true))
        titleBlock.addView(text("Không gian tập trung cho trò chơi trên thiết bị", 11f, MUTED))
        top.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton(effectLabel(), PURPLE) {
            effectsEnabled = !effectsEnabled
            prefs.edit().putBoolean("effects_enabled", effectsEnabled).apply()
            Toast.makeText(this, if (effectsEnabled) "Đã bật hiệu ứng" else "Đã tắt hiệu ứng", Toast.LENGTH_SHORT).show()
            showGameHub()
        })
        top.addView(actionButton("QUÉT TRÒ CHƠI", CYAN) { populateGames() },
            LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(9) })
        main.addView(top)

        main.addView(View(this).apply { setBackgroundColor(Color.rgb(42, 39, 72)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(15)
                bottomMargin = dp(16)
            })

        gameSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gameSection.addView(text("THƯ VIỆN TRÒ CHƠI", 15f, CYAN, true))
        gameSection.addView(text(
            "Game được Android phân loại là trò chơi sẽ xuất hiện bên dưới. Chạm thẻ để mở.",
            11f, MUTED
        ).apply { setPadding(0, dp(4), 0, dp(12)) })
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(gameSection)
        }
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(main, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)
        populateGames()
    }

    private fun populateGames() {
        if (!::gameSection.isInitialized) return
        while (gameSection.childCount > 2) gameSection.removeViewAt(2)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        val games = resolved
            .filter { it.activityInfo.packageName != packageName }
            .filter { it.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (games.isEmpty()) {
            gameSection.addView(text(
                "Chưa tìm thấy game được Android phân loại. Một số trò chơi có thể không khai báo danh mục GAME hoặc bị giới hạn hiển thị bởi hệ thống.",
                13f, MUTED
            ).apply {
                setPadding(dp(18), dp(22), dp(18), dp(22))
                background = panelBackground()
            })
            return
        }

        val grid = android.widget.GridLayout(this).apply { columnCount = 3 }
        games.forEach { item ->
            val appName = item.loadLabel(packageManager).toString()
            val packageName = item.activityInfo.packageName
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(12), dp(10), dp(12))
                background = panelBackground()
                isClickable = true
                isFocusable = true
                setOnClickListener { launchGame(packageName, appName) }
            }
            val iconView = ImageView(this).apply {
                setImageDrawable(item.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            card.addView(iconView, LinearLayout.LayoutParams(dp(48), dp(48)))
            card.addView(text(appName, 13f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(0, dp(8), 0, dp(3))
            })
            card.addView(text("CHẠM ĐỂ MỞ", 9f, CYAN, true))
            val params = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = dp(118)
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(card, params)
        }
        gameSection.addView(grid)
    }

    private fun launchGame(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "Android không cung cấp lối mở cho $appName", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { startActivity(launchIntent) }.onFailure {
            Toast.makeText(this, "Không thể mở $appName trên thiết bị này", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actionButton(caption: String, tint: Int, onClick: () -> Unit) =
        text(caption, 10f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(tint)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun effectLabel() = if (effectsEnabled) "HIỆU ỨNG: BẬT" else "HIỆU ỨNG: TẮT"

    private fun panelBackground() = GradientDrawable().apply {
        cornerRadius = dp(15).toFloat()
        setColor(Color.rgb(17, 19, 39))
        setStroke(dp(1), Color.rgb(54, 46, 91))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val PURPLE = Color.rgb(168, 85, 247)
        private val CYAN = Color.rgb(34, 211, 238)
        private val MUTED = Color.rgb(145, 148, 177)
    }

    private class NeonSplashView(
        activity: Activity,
        private val animate: Boolean,
        private val onFinished: () -> Unit
    ) : View(activity) {
        private val started = SystemClock.uptimeMillis()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val points = Array(72) { index ->
            Pair(((index * 73) % 997) / 997f, ((index * 137 + 41) % 991) / 991f)
        }

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            isClickable = true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) onFinished()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val elapsed = (SystemClock.uptimeMillis() - started) / 1000f
            val pulse = if (animate) (sin(elapsed * 3.2f) + 1f) / 2f else 0.35f

            canvas.drawColor(Color.rgb(5, 6, 15))
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.rgb(28, 10, 53), Color.rgb(5, 7, 19), Color.rgb(4, 25, 39)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            paint.color = Color.argb(34, 135, 80, 255)
            paint.strokeWidth = 1f
            for (x in 0..w.toInt() step 76) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h, paint)
            for (y in 0..h.toInt() step 62) canvas.drawLine(0f, y.toFloat(), w, y.toFloat(), paint)

            val cx = w * 0.5f
            val cy = h * 0.42f
            val radius = min(w * 0.12f, h * 0.23f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f
            paint.color = Color.argb((70 + pulse * 100).toInt(), 34, 211, 238)
            canvas.drawCircle(cx, cy, radius * (1.35f + pulse * 0.12f), paint)
            paint.color = Color.argb((85 + pulse * 115).toInt(), 168, 85, 247)
            canvas.drawCircle(cx, cy, radius * 1.55f, paint)

            if (animate) {
                for (i in 0..3) {
                    paint.color = if (i % 2 == 0) Color.argb(125, 168, 85, 247)
                    else Color.argb(125, 34, 211, 238)
                    paint.strokeWidth = 2f
                    val sweep = ((elapsed * (90f + i * 18f) + i * 83f) % 360f)
                    canvas.drawArc(
                        cx - radius * (1.48f + i * 0.12f),
                        cy - radius * (1.48f + i * 0.12f),
                        cx + radius * (1.48f + i * 0.12f),
                        cy + radius * (1.48f + i * 0.12f),
                        sweep, 68f, false, paint
                    )
                }
                points.forEachIndexed { index, point ->
                    val drift = (elapsed * (0.07f + (index % 7) * 0.012f)) % 1f
                    val px = point.first * w
                    val py = ((point.second + drift) % 1f) * h
                    paint.style = Paint.Style.FILL
                    paint.color = if (index % 2 == 0) Color.argb(150, 34, 211, 238)
                    else Color.argb(150, 168, 85, 247)
                    canvas.drawCircle(px, py, 1.2f + (index % 3) * 0.55f, paint)
                }
            }

            val size = radius * 0.95f
            val left = cx - size * 0.47f
            val right = cx + size * 0.47f
            val top = cy - size * 0.52f
            val bottom = cy + size * 0.52f
            val mark = Path().apply {
                moveTo(left, bottom)
                lineTo(left, top)
                lineTo(right, bottom)
                lineTo(right, top)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = size * 0.12f
            paint.color = Color.argb(105, 168, 85, 247)
            canvas.drawPath(mark, paint)
            paint.strokeWidth = size * 0.065f
            paint.shader = LinearGradient(left, top, right, bottom,
                Color.rgb(168, 85, 247), Color.rgb(34, 211, 238), Shader.TileMode.CLAMP)
            canvas.drawPath(mark, paint)
            paint.shader = null

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            paint.textSize = min(w * 0.055f, h * 0.12f)
            paint.color = Color.argb(55, 34, 211, 238)
            canvas.drawText("NEXORA", cx + 2f, cy + radius * 1.95f + 2f, paint)
            paint.color = Color.WHITE
            canvas.drawText("NEXORA", cx, cy + radius * 1.95f, paint)
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textSize = min(w * 0.014f, h * 0.033f)
            paint.color = Color.rgb(150, 155, 190)
            canvas.drawText("EXTREME GAMING EXPERIENCE", cx, cy + radius * 2.35f, paint)

            val barWidth = min(w * 0.34f, 420f)
            val barLeft = cx - barWidth / 2f
            val barTop = h * 0.88f
            paint.color = Color.rgb(36, 34, 57)
            canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth, barTop + 5f, 4f, 4f, paint)
            paint.shader = LinearGradient(barLeft, barTop, barLeft + barWidth, barTop,
                Color.rgb(168, 85, 247), Color.rgb(34, 211, 238), Shader.TileMode.CLAMP)
            val progress = if (animate) (elapsed / 2.8f).coerceIn(0.08f, 1f) else 0.65f
            canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth * progress, barTop + 5f, 4f, 4f, paint)
            paint.shader = null

            if (animate && elapsed < 2.8f) postInvalidateDelayed(16L)
        }
    }
}
