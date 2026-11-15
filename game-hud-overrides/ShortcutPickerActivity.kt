package vn.nero.gamehud

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ShortcutPickerActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(HudOverlayService.PREFS, MODE_PRIVATE) }
    private val selected = linkedSetOf<String>()
    private lateinit var list: LinearLayout
    private lateinit var countText: TextView
    private val apps by lazy { loadApps() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(prefs.getStringSet(HudOverlayService.KEY_FAVORITES, emptySet()) ?: emptySet())
        buildUi()
        renderApps()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(12))
            setBackgroundColor(0xFF080D14.toInt())
        }
        root.addView(TextView(this).apply {
            text = "ỨNG DỤNG NHANH"
            textSize = 24f
            setTextColor(0xFFF3F7FB.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        countText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF9BAABC.toInt())
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(countText)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "Tối đa 8 ứng dụng. Chạm vào một dòng để thêm hoặc bỏ."
            textSize = 12f
            setTextColor(0xFF718296.toInt())
            setPadding(0, dp(10), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "LƯU LỰA CHỌN"
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF07140F.toInt())
            background = rounded(0xFF69E0BD.toInt(), 14)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                prefs.edit().putStringSet(HudOverlayService.KEY_FAVORITES, selected.toSet()).apply()
                HudOverlayService.refreshRunningPanel()
                finish()
            }
        })
        setContentView(root)
    }

    private fun renderApps() {
        list.removeAllViews()
        countText.text = "Đã chọn ${selected.size}/8 ứng dụng"
        apps.forEach { app ->
            val packageId = app.activityInfo.packageName
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = rounded(if (selected.contains(packageId)) 0xFF1C3B3A.toInt() else 0xFF111B28.toInt(), 14)
                val icon = android.widget.ImageView(this@ShortcutPickerActivity).apply {
                    setImageDrawable(app.loadIcon(packageManager))
                }
                addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)))
                val label = TextView(this@ShortcutPickerActivity).apply {
                    text = app.loadLabel(packageManager).toString()
                    textSize = 15f
                    setTextColor(0xFFE7F1F9.toInt())
                    setPadding(dp(12), 0, dp(8), 0)
                }
                addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                val mark = TextView(this@ShortcutPickerActivity).apply {
                    text = if (selected.contains(packageId)) "✓" else "+"
                    textSize = 20f
                    setTextColor(if (selected.contains(packageId)) 0xFF69E0BD.toInt() else 0xFF9BAABC.toInt())
                }
                addView(mark)
                setOnClickListener {
                    if (selected.contains(packageId)) selected.remove(packageId)
                    else if (selected.size < 8) selected.add(packageId)
                    renderApps()
                }
            }
            val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            list.addView(row, params)
        }
    }

    private fun loadApps(): List<android.content.pm.ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return results.filter { it.activityInfo.packageName != this.packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
