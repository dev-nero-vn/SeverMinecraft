package com.tideus.link

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Bitmap
import android.net.http.SslError
import java.net.URI
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("tideus_link", Context.MODE_PRIVATE) }
    private var webView: WebView? = null
    private var serverOrigin: String? = null
    private var serverUrl: String? = null
    private var root: FrameLayout? = null

    private val bg = Color.rgb(8, 13, 25)
    private val panel = Color.rgb(17, 26, 43)
    private val panel2 = Color.rgb(22, 34, 55)
    private val primaryTextColor = Color.rgb(237, 243, 255)
    private val muted = Color.rgb(145, 162, 187)
    private val cyan = Color.rgb(80, 214, 220)
    private val purple = Color.rgb(146, 112, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val saved = prefs.getString("server_url", null)
        showConnectScreen(saved)
    }

    private fun rounded(color: Int, radius: Float = 18f, stroke: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (stroke != 0) setStroke(1, stroke)
        }
    }

    private fun label(value: String, size: Float, color: Int = primaryTextColor, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun button(value: String, primary: Boolean = false): Button {
        return Button(this).apply {
            text = value
            textSize = 14f
            isAllCaps = false
            setTextColor(primaryTextColor)
            background = rounded(if (primary) Color.rgb(87, 80, 238) else panel2, 14f, if (primary) purple else Color.rgb(49, 67, 96))
            minHeight = dp(50)
            stateListAnimator = null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showConnectScreen(prefill: String? = null) {
        webView?.destroy()
        webView = null
        serverOrigin = null
        serverUrl = null

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }
        val logo = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_tideus_logo)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        column.addView(logo, LinearLayout.LayoutParams(dp(116), dp(116)).apply {
            bottomMargin = dp(16)
        })
        column.addView(label("TIDEUS LINK", 27f, primaryTextColor, true))
        column.addView(label("LINUX CONTROL CENTER", 11f, cyan, true).apply {
            letterSpacing = 0.16f
            setPadding(0, dp(5), 0, dp(26))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 22f, Color.rgb(43, 58, 83))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        card.addView(label("Kết nối máy chủ Linux", 19f, primaryTextColor, true))
        card.addView(label("Nhập địa chỉ TIDEUS LINK Control Center đã triển khai trên máy chủ của bạn.", 12f, muted).apply {
            setPadding(0, dp(8), 0, dp(18))
        })
        val urlInput = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(text)
            setHintTextColor(muted)
            hint = "https://tideus.example.com"
            setText(prefill ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            background = rounded(Color.rgb(8, 16, 30), 13f, Color.rgb(55, 77, 112))
            setPadding(dp(14), 0, dp(14), 0)
        }
        card.addView(label("ĐỊA CHỈ MÁY CHỦ", 10f, cyan, true).apply {
            setPadding(0, 0, 0, dp(8))
            letterSpacing = 0.12f
        })
        card.addView(urlInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        card.addView(button("Kết nối TIDEUS LINK", true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(14)
        }.also { params ->
            // Listener is attached below after the button is found.
        })
        val connectButton = card.getChildAt(card.childCount - 1) as Button
        connectButton.setOnClickListener {
            val normalized = normalizeServerUrl(urlInput.text.toString())
            if (normalized == null) {
                Toast.makeText(this, "Địa chỉ máy chủ không hợp lệ. Hãy dùng HTTPS hoặc IP mạng nội bộ.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.edit().putString("server_url", normalized).apply()
            openDashboard(normalized)
        }
        card.addView(label("Gợi ý: dùng HTTPS khi truy cập qua Internet. HTTP chỉ được chấp nhận cho localhost hoặc IP mạng LAN.", 11f, muted).apply {
            setPadding(0, dp(14), 0, 0)
        })
        column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        column.addView(label("Discord không được tích hợp trong phiên bản này.", 10f, muted).apply {
            setPadding(0, dp(22), 0, 0)
        })
        scroll.addView(column)
        root = FrameLayout(this).apply { addView(scroll) }
        setContentView(root)
    }

    private fun normalizeServerUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.length > 240) return null
        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        return try {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
            if (scheme != "https" && scheme != "http") return null
            if (scheme == "http" && !isPrivateHost(host)) return null
            uri.toASCIIString().trimEnd('/')
        } catch (_: Exception) {
            null
        }
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        val parts = host.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 169 && parts[1] == 254)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openDashboard(url: String) {
        val normalized = normalizeServerUrl(url)
        if (normalized == null) {
            showConnectScreen(url)
            return
        }
        serverUrl = normalized
        val uri = URI(normalized)
        serverOrigin = "${uri.scheme}://${uri.host}:${if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80}"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(11, 18, 32))
        }
        val back = button("‹")
        back.minWidth = dp(42)
        back.setOnClickListener { if (webView?.canGoBack() == true) webView?.goBack() else showConnectScreen(serverUrl) }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = TextView(this).apply {
            text = "TIDEUS LINK"
            textSize = 13f
            setTextColor(text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val change = button("Máy chủ")
        change.setOnClickListener { showConnectScreen(serverUrl) }
        toolbar.addView(change, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        layout.addView(toolbar)

        val browser = WebView(this)
        webView = browser
        browser.setBackgroundColor(bg)
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            builtInZoomControls = true
            displayZoomControls = false
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
        browser.webChromeClient = WebChromeClient()
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val next = request.url
                if (isAllowedDashboardNavigation(next.toString())) return false
                if (next.scheme == "https") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, next)) } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Không thể mở liên kết ngoài.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Đã chặn điều hướng ngoài máy chủ.", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                title.text = "TIDEUS LINK · Đang tải"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                title.text = "TIDEUS LINK"
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    Toast.makeText(this@MainActivity, "Không thể kết nối máy chủ. Kiểm tra địa chỉ và trạng thái Linux.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                Toast.makeText(this@MainActivity, "Chứng chỉ HTTPS không hợp lệ. Kết nối đã bị chặn.", Toast.LENGTH_LONG).show()
            }
        }
        browser.setDownloadListener(DownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            try {
                val downloadUri = Uri.parse(downloadUrl)
                if (downloadUri.scheme != "https" && !isAllowedDashboardNavigation(downloadUrl)) {
                    Toast.makeText(this, "Đã chặn tải xuống từ nguồn không an toàn.", Toast.LENGTH_LONG).show()
                    return@DownloadListener
                }
                val request = DownloadManager.Request(downloadUri)
                val cookie = CookieManager.getInstance().getCookie(downloadUrl)
                if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
                if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
                request.setMimeType(mimeType)
                request.setTitle(URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType))
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType))
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, "Đã đưa tệp vào hàng đợi tải xuống.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Không thể tải tệp này.", Toast.LENGTH_LONG).show()
            }
        })
        layout.addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root = FrameLayout(this).apply { addView(layout) }
        setContentView(root)
        browser.loadUrl(normalized)
    }

    private fun isAllowedDashboardNavigation(value: String): Boolean {
        return try {
            val uri = URI(value)
            val origin = "${uri.scheme}://${uri.host}:${if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80}"
            origin == serverOrigin && (uri.scheme == "https" || (uri.scheme == "http" && isPrivateHost(uri.host)))
        } catch (_: Exception) {
            false
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val browser = webView
        if (browser != null && browser.canGoBack()) {
            browser.goBack()
        } else if (browser != null) {
            showConnectScreen(serverUrl)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}
