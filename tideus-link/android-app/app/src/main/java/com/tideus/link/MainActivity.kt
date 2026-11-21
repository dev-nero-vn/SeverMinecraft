package com.tideus.link

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.URI

class MainActivity : Activity() {

    private val bg = Color.rgb(8, 12, 25)
    private val panel = Color.rgb(17, 25, 43)
    private val panelAlt = Color.rgb(23, 34, 55)
    private val stroke = Color.rgb(42, 57, 84)
    private val textPrimary = Color.rgb(241, 245, 255)
    private val textMuted = Color.rgb(145, 162, 188)
    private val purple = Color.rgb(132, 89, 255)
    private val cyan = Color.rgb(70, 211, 229)
    private val green = Color.rgb(67, 211, 157)
    private val amber = Color.rgb(244, 190, 95)
    private val red = Color.rgb(247, 112, 127)

    private var linkInput: EditText? = null
    private var progressBar: ProgressBar? = null
    private var progressStatus: TextView? = null
    private var progressPercent: TextView? = null
    private var progressSteps: List<TextView> = emptyList()
    private var resultStatus: TextView? = null
    private var resultValue: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        renderHome()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 18, border: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (border != 0) setStroke(dp(1), border)
        }

    private fun label(
        value: String,
        size: Float,
        color: Int = textPrimary,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun vertical(): LinearLayout =
        LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun horizontal(): LinearLayout =
        LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun card(): LinearLayout = vertical().apply {
        background = rounded(panel, 20, stroke)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun actionButton(
        caption: String,
        fill: Int,
        foreground: Int = Color.WHITE
    ): TextView = label(caption, 13f, foreground, true).apply {
        gravity = Gravity.CENTER
        background = rounded(fill, 14)
        setPadding(dp(15), dp(13), dp(15), dp(13))
        isClickable = true
        isFocusable = true
    }

    private fun renderHome() {
        val root = vertical().apply { setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = vertical().apply {
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }

        val header = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_tideus_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panelAlt, 15, stroke)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brand = vertical().apply {
            setPadding(dp(12), 0, 0, 0)
            addView(label("TIDEUS LINK", 16f, textPrimary, true))
            addView(label("LINK PROCESSOR", 9f, cyan, true).apply {
                letterSpacing = 0.16f
                setPadding(0, dp(3), 0, 0)
            })
        }
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label("BETA", 9f, amber, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(54, 41, 22), 30)
            setPadding(dp(11), dp(7), dp(11), dp(7))
        })
        page.addView(header)

        gap(page, 25)
        page.addView(label("Xử lý liên kết", 25f, textPrimary, true))
        page.addView(label(
            "Dán đường dẫn để kiểm tra và xử lý liên kết được hỗ trợ.",
            12f, textMuted
        ).apply { setPadding(0, dp(5), 0, 0) })

        gap(page, 19)
        val inputCard = card()
        inputCard.addView(label("DÁN LINK CẦN BYPASS", 10f, cyan, true).apply {
            letterSpacing = 0.12f
        })
        gap(inputCard, 12)

        val inputShell = vertical().apply {
            background = rounded(Color.rgb(9, 16, 31), 14, stroke)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        val input = EditText(this).apply {
            hint = "https://example.com/your-link"
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 13f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = null
            setPadding(dp(2), dp(6), dp(2), dp(6))
        }
        linkInput = input
        inputShell.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val inputActions = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val paste = actionButton("Dán từ bộ nhớ", panelAlt, cyan)
        paste.textSize = 11f
        paste.setOnClickListener { pasteFromClipboard() }
        inputActions.addView(paste, LinearLayout.LayoutParams(0, dp(40), 1f))
        val clear = actionButton("Xóa", panelAlt, textMuted)
        clear.textSize = 11f
        clear.setOnClickListener {
            input.setText("")
            resetProgress()
        }
        inputActions.addView(clear, LinearLayout.LayoutParams(dp(76), dp(40)).apply {
            leftMargin = dp(8)
        })
        inputCard.addView(inputShell)
        gap(inputCard, 10)
        inputCard.addView(inputActions)
        page.addView(inputCard)

        gap(page, 13)
        val start = actionButton("BẮT ĐẦU BYPASS  →", purple)
        start.textSize = 14f
        start.setPadding(dp(16), dp(16), dp(16), dp(16))
        start.setOnClickListener { beginLinkCheck() }
        page.addView(start, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        gap(page, 20)
        val progressCard = card()
        val progressHeader = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        progressHeader.addView(label("TIẾN TRÌNH", 11f, textPrimary, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        progressPercent = label("0%", 12f, cyan, true)
        progressHeader.addView(progressPercent)
        progressCard.addView(progressHeader)
        gap(progressCard, 10)

        progressStatus = label("Đang chờ bạn dán link", 11f, textMuted)
        progressCard.addView(progressStatus)
        gap(progressCard, 10)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(35, 48, 70))
        }
        progressCard.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(7)
        ))
        gap(progressCard, 14)

        val steps = vertical()
        val stepLabels = listOf(
            "Kiểm tra định dạng liên kết",
            "Kết nối bộ xử lý",
            "Phân tích trang đích",
            "Trích xuất nội dung / tệp"
        )
        progressSteps = stepLabels.mapIndexed { index, title ->
            val row = horizontal().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7), 0, dp(7))
            }
            val marker = label("○", 16f, textMuted, true).apply {
                gravity = Gravity.CENTER
            }
            row.addView(marker, LinearLayout.LayoutParams(dp(28), dp(28)))
            row.addView(label(title, 11f, textMuted))
            steps.addView(row)
            marker
        }
        progressCard.addView(steps)
        page.addView(progressCard)

        gap(page, 16)
        val resultCard = card()
        resultCard.addView(label("KẾT QUẢ", 11f, textPrimary, true))
        gap(resultCard, 10)
        resultStatus = label("Chưa có kết quả", 12f, textMuted, true)
        resultCard.addView(resultStatus)
        gap(resultCard, 5)
        resultValue = label(
            "Kết quả xử lý sẽ hiển thị tại đây. Nội dung văn bản có thể sao chép; tệp được hỗ trợ sẽ có tùy chọn tải xuống.",
            11f, textMuted
        )
        resultValue?.setLineSpacing(dp(2).toFloat(), 1f)
        resultCard.addView(resultValue)
        page.addView(resultCard)

        gap(page, 14)
        page.addView(label(
            "Chỉ xử lý liên kết bạn có quyền truy cập. Nếu dịch vụ xử lý chưa được kết nối, ứng dụng sẽ báo rõ thay vì tạo kết quả giả.",
            10f, textMuted
        ))

        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val copiedText = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        } else ""
        if (copiedText.isBlank()) {
            Toast.makeText(this, "Bộ nhớ tạm chưa có liên kết.", Toast.LENGTH_SHORT).show()
            return
        }
        linkInput?.setText(copiedText.trim())
        linkInput?.setSelection(linkInput?.text?.length ?: 0)
    }

    private fun beginLinkCheck() {
        val raw = linkInput?.text?.toString()?.trim().orEmpty()
        val uri = runCatching { URI(raw) }.getOrNull()
        val valid = uri != null &&
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null

        if (!valid) {
            progressStatus?.text = "Link chưa hợp lệ. Hãy nhập URL bắt đầu bằng http:// hoặc https://."
            progressStatus?.setTextColor(red)
            resultStatus?.text = "Không thể bắt đầu"
            resultStatus?.setTextColor(red)
            resultValue?.text = "Kiểm tra lại đường dẫn rồi thử lại."
            progressBar?.progress = 0
            progressPercent?.text = "0%"
            progressSteps.forEach { marker ->
                marker.text = "○"
                marker.setTextColor(textMuted)
            }
            Toast.makeText(this, "Đường dẫn không hợp lệ.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar?.progress = 20
        progressPercent?.text = "20%"
        progressStatus?.text = "Link hợp lệ. Đang chờ kết nối bộ xử lý."
        progressStatus?.setTextColor(amber)
        progressSteps.forEachIndexed { index, marker ->
            if (index == 0) {
                marker.text = "✓"
                marker.setTextColor(green)
            } else {
                marker.text = "○"
                marker.setTextColor(textMuted)
            }
        }
        resultStatus?.text = "Chưa xử lý"
        resultStatus?.setTextColor(amber)
        resultValue?.text =
            "Ứng dụng đã kiểm tra định dạng link. Bộ xử lý bypass chưa được kết nối nên chưa thể phân tích trang đích hoặc trả nội dung / tệp."
        Toast.makeText(this, "Đã kiểm tra link. Bộ xử lý chưa được kết nối.", Toast.LENGTH_LONG).show()
    }

    private fun resetProgress() {
        progressBar?.progress = 0
        progressPercent?.text = "0%"
        progressStatus?.text = "Đang chờ bạn dán link"
        progressStatus?.setTextColor(textMuted)
        progressSteps.forEach { marker ->
            marker.text = "○"
            marker.setTextColor(textMuted)
        }
        resultStatus?.text = "Chưa có kết quả"
        resultStatus?.setTextColor(textMuted)
        resultValue?.text =
            "Kết quả xử lý sẽ hiển thị tại đây. Nội dung văn bản có thể sao chép; tệp được hỗ trợ sẽ có tùy chọn tải xuống."
    }
}
