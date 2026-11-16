package vn.nero.windowsvirtual

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("nero_windows_virtual", MODE_PRIVATE) }
    private lateinit var hostInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var desktop: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 15, 25))
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }

        root.addView(TextView(this).apply {
            text = "NERO WINDOWS VIRTUAL"
            textSize = 20f
            setTextColor(Color.WHITE)
        })

        root.addView(TextView(this).apply {
            text = "Windows 11 thật • Dữ liệu lưu trên máy chủ"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        })

        hostInput = EditText(this).apply {
            hint = "Địa chỉ host, ví dụ http://192.168.1.10:8765"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText(prefs.getString("host", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(hostInput, fieldParams())

        tokenInput = EditText(this).apply {
            hint = "API token của host"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(tokenInput, fieldParams())

        statusText = TextView(this).apply {
            text = "Chưa kết nối. Nhập địa chỉ và token của máy chủ riêng."
            textSize = 12f
            setTextColor(Color.rgb(180, 205, 235))
            setPadding(0, dp(5), 0, dp(5))
        }
        root.addView(statusText)

        val rowOne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowOne.addView(makeButton("Kết nối màn hình") { connectDesktop() }, buttonParams())
        rowOne.addView(makeButton("Khởi động bộ cài") { callApi("/api/start?mode=install") }, buttonParams())
        root.addView(rowOne)

        val rowTwo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowTwo.addView(makeButton("Khởi động Windows đã cài") { callApi("/api/start?mode=disk") }, buttonParams())
        rowTwo.addView(makeButton("Tắt máy ảo") { callApi("/api/stop") }, buttonParams())
        root.addView(rowTwo)

        desktop = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    return scheme != "https" && scheme != "http"
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        setStatus("Không tải được màn hình: " + error.description)
                    }
                }
            }
        }
        desktop.visibility = View.GONE
        root.addView(desktop, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun connectDesktop() {
        val base = hostBase() ?: return
        val token = tokenInput.text.toString().trim()
        if (token.isBlank()) {
            setStatus("Bạn chưa nhập API token.")
            return
        }
        prefs.edit().putString("host", base).apply()
        val encoded = URLEncoder.encode(token, "UTF-8")
        desktop.visibility = View.VISIBLE
        desktop.loadUrl(base + "/desktop?token=" + encoded)
        setStatus("Đang kết nối màn hình máy ảo…")
    }

    private fun callApi(path: String) {
        val base = hostBase() ?: return
        val token = tokenInput.text.toString().trim()
        if (token.isBlank()) {
            setStatus("Bạn chưa nhập API token.")
            return
        }
        setStatus("Đang gửi lệnh đến host…")
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    setRequestProperty("Authorization", "Bearer " + token)
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    outputStream.use { it.write(ByteArray(0)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                runOnUiThread {
                    setStatus(if (code in 200..299) "Host: " + body else "Lỗi HTTP " + code + ": " + body)
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Không gọi được host: " + e.message) }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun hostBase(): String? {
        val value = hostInput.text.toString().trim().trimEnd('/')
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            setStatus("Địa chỉ host phải bắt đầu bằng http:// hoặc https://")
            return null
        }
        return value
    }

    private fun setStatus(message: String) {
        if (::statusText.isInitialized) statusText.text = message
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            setOnClickListener { action() }
        }

    private fun fieldParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
    ).apply { topMargin = dp(3) }

    private fun buttonParams() = LinearLayout.LayoutParams(
        0, dp(48), 1f
    ).apply { marginEnd = dp(3) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        if (::desktop.isInitialized) {
            desktop.stopLoading()
            desktop.destroy()
        }
        executor.shutdownNow()
        super.onDestroy()
    }
}
