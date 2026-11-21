package com.tideus.link

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var resultCopyButton: TextView? = null
    private var resultOpenButton: TextView? = null
    private var startButton: TextView? = null
    private var savedFileUri: Uri? = null
    private var savedFileMime: String = "application/octet-stream"
    private var copiedResultText: String = ""
    private var isProcessing = false
    private val mainHandler = Handler(Looper.getMainLooper())

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
        val start = actionButton("✦  BẮT ĐẦU XỬ LÝ LINK  →", purple)
        start.textSize = 14f
        start.letterSpacing = 0.025f
        start.setPadding(dp(16), dp(17), dp(16), dp(17))
        start.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(139, 78, 255), Color.rgb(91, 91, 255), Color.rgb(49, 184, 218))
        ).apply { cornerRadius = dp(18).toFloat() }
        start.elevation = dp(7).toFloat()
        startButton = start
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

        gap(resultCard, 12)
        val resultActions = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        resultCopyButton = actionButton("Sao chép văn bản", panelAlt, cyan).apply {
            visibility = View.GONE
            textSize = 11f
            setOnClickListener { copyResult() }
        }
        resultOpenButton = actionButton("Mở tệp", panelAlt, green).apply {
            visibility = View.GONE
            textSize = 11f
            setOnClickListener { openSavedFile() }
        }
        resultActions.addView(resultCopyButton, LinearLayout.LayoutParams(0, dp(42), 1f))
        resultActions.addView(resultOpenButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            leftMargin = dp(8)
        })
        resultCard.addView(resultActions)
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
        if (isProcessing) return
        val raw = linkInput?.text?.toString()?.trim().orEmpty()
        if (raw.isBlank()) {
            progressStatus?.text = "Hãy dán một liên kết HTTPS trước."
            progressStatus?.setTextColor(red)
            resultStatus?.text = "Chưa có đường dẫn"
            resultStatus?.setTextColor(red)
            return
        }

        isProcessing = true
        savedFileUri = null
        copiedResultText = ""
        resultCopyButton?.visibility = View.GONE
        resultOpenButton?.visibility = View.GONE
        resultStatus?.text = "Đang xử lý liên kết"
        resultStatus?.setTextColor(amber)
        resultValue?.text = "Đang kết nối và phân tích trang đích…"
        startButton?.isEnabled = false
        startButton?.alpha = 0.65f
        startButton?.text = "ĐANG XỬ LÝ…"
        updateProgress(3, 0, "Đang chuẩn bị kết nối")

        Thread({
            try {
                val processor = LinkProcessor(applicationContext)
                val result = processor.process(raw) { percent, stepIndex, message ->
                    mainHandler.post { updateProgress(percent, stepIndex, message) }
                }
                mainHandler.post {
                    isProcessing = false
                    startButton?.isEnabled = true
                    startButton?.alpha = 1f
                    startButton?.text = "↻  XỬ LÝ LIÊN KẾT KHÁC"
                    progressBar?.progress = 100
                    progressPercent?.text = "100%"
                    progressStatus?.text = "Hoàn tất xử lý"
                    progressStatus?.setTextColor(green)
                    progressSteps.forEach { marker ->
                        marker.text = "✓"
                        marker.setTextColor(green)
                    }
                    resultStatus?.text = if (result.isFile) "Đã tải tệp xuống" else "Đã trích xuất văn bản"
                    resultStatus?.setTextColor(green)
                    copiedResultText = buildString {
                        append(result.pageTitle)
                        append("\n\n")
                        append(result.extractedText)
                        append("\n\nNguồn: ")
                        append(result.finalUrl)
                    }
                    resultValue?.text = copiedResultText
                    savedFileUri = result.savedUri
                    savedFileMime = result.contentType
                    resultCopyButton?.visibility = View.VISIBLE
                    resultOpenButton?.visibility = if (result.savedUri != null) View.VISIBLE else View.GONE
                    Toast.makeText(
                        this,
                        if (result.isFile) "Đã lưu tệp vào Downloads/TIDEUS LINK." else "Đã trích xuất văn bản thành công.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "Không thể xử lý liên kết này."
                mainHandler.post {
                    isProcessing = false
                    startButton?.isEnabled = true
                    startButton?.alpha = 1f
                    startButton?.text = "↻  THỬ LẠI"
                    progressStatus?.text = message
                    progressStatus?.setTextColor(red)
                    resultStatus?.text = "Không thể xử lý"
                    resultStatus?.setTextColor(red)
                    resultValue?.text = "Không có kết quả được tạo. $message"
                    resultCopyButton?.visibility = View.GONE
                    resultOpenButton?.visibility = View.GONE
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }, "tideus-link-processor").start()
    }

    private fun updateProgress(percent: Int, stepIndex: Int, message: String) {
        val safePercent = percent.coerceIn(0, 100)
        progressBar?.progress = safePercent
        progressPercent?.text = "$safePercent%"
        progressStatus?.text = message
        progressStatus?.setTextColor(cyan)
        progressSteps.forEachIndexed { index, marker ->
            when {
                index < stepIndex -> {
                    marker.text = "✓"
                    marker.setTextColor(green)
                }
                index == stepIndex -> {
                    marker.text = "●"
                    marker.setTextColor(cyan)
                }
                else -> {
                    marker.text = "○"
                    marker.setTextColor(textMuted)
                }
            }
        }
    }

    private fun copyResult() {
        if (copiedResultText.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TIDEUS LINK result", copiedResultText))
        Toast.makeText(this, "Đã sao chép kết quả.", Toast.LENGTH_SHORT).show()
    }

    private fun openSavedFile() {
        val uri = savedFileUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, savedFileMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Không tìm thấy ứng dụng mở loại tệp này.", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetProgress() {
        if (isProcessing) return
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
            "Kết quả trích xuất sẽ hiển thị tại đây. Văn bản có thể sao chép; tệp tải xuống sẽ lưu trong Downloads/TIDEUS LINK."
        resultCopyButton?.visibility = View.GONE
        resultOpenButton?.visibility = View.GONE
        savedFileUri = null
        copiedResultText = ""
        startButton?.isEnabled = true
        startButton?.alpha = 1f
        startButton?.text = "✦  BẮT ĐẦU XỬ LÝ LINK  →"
    }
}
