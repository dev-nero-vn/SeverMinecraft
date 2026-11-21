package com.tideus.link

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Locale

data class LinkProcessResult(
    val pageTitle: String,
    val finalUrl: String,
    val contentType: String,
    val extractedText: String,
    val isFile: Boolean,
    val fileName: String? = null,
    val savedLocation: String? = null,
    val savedUri: Uri? = null
)

class LinkProcessor(
    private val context: Context? = null,
    private val fileSink: ((ByteArray, String, String) -> Pair<Uri?, String>)? = null
) {

    fun process(
        rawUrl: String,
        onProgress: (percent: Int, stepIndex: Int, message: String) -> Unit
    ): LinkProcessResult {
        onProgress(5, 0, "Đang kiểm tra đường dẫn")
        var current = validateUrl(rawUrl)
        onProgress(12, 0, "Đường dẫn hợp lệ, kiểm tra máy chủ đích")

        var response: HttpURLConnection? = null
        var redirects = 0
        val visitedUrls = mutableSetOf<String>()
        try {
            while (true) {
                if (!visitedUrls.add(current.normalize().toString())) {
                    throw IOException("Phát hiện vòng lặp chuyển hướng. Đã dừng để tránh tải lặp vô hạn.")
                }
                validatePublicHost(current.host)
                onProgress(18 + redirects * 3, 1, "Đang kết nối ${current.host}")

                val connection = (URL(current.toString()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    useCaches = false
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain,application/json,application/xml,*/*")
                    setRequestProperty("Accept-Language", "vi,en-US;q=0.8,en;q=0.6")
                    setRequestProperty("Connection", "close")
                }
                response = connection
                val status = connection.responseCode

                if (status in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    response = null
                    if (location.isNullOrBlank()) throw IOException("Máy chủ chuyển hướng nhưng không cung cấp địa chỉ đích.")
                    if (redirects >= MAX_REDIRECTS) throw IOException("Liên kết chuyển hướng quá nhiều lần.")
                    current = validateUrl(current.resolve(location).toString())
                    redirects += 1
                    onProgress(20 + redirects * 3, 1, "Đã theo chuyển hướng $redirects/$MAX_REDIRECTS")
                    continue
                }

                if (status !in 200..299) {
                    connection.disconnect()
                    response = null
                    val message = when (status) {
                        401, 403 -> "Trang yêu cầu đăng nhập hoặc quyền truy cập (HTTP $status). TIDEUS LINK không vượt cơ chế bảo vệ này."
                        404 -> "Không tìm thấy nội dung tại đường dẫn này (HTTP 404)."
                        429 -> "Máy chủ giới hạn yêu cầu (HTTP 429). Hãy thử lại sau."
                        else -> "Máy chủ trả về HTTP $status."
                    }
                    throw IOException(message)
                }

                val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
                if (declaredLength > MAX_DOWNLOAD_BYTES) {
                    connection.disconnect()
                    response = null
                    throw IOException("Tệp vượt quá giới hạn ${MAX_DOWNLOAD_MB} MB của phiên bản này.")
                }

                val contentTypeHeader = connection.contentType
                val contentType = contentTypeHeader
                    ?.substringBefore(";")
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()
                val disposition = connection.getHeaderField("Content-Disposition").orEmpty()
                onProgress(52, 2, "Đã kết nối. Đang nhận nội dung")

                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_DOWNLOAD_BYTES) {
                            throw IOException("Nội dung vượt quá giới hạn ${MAX_DOWNLOAD_MB} MB.")
                        }
                        output.write(buffer, 0, count)
                        val percent = 55 + ((total.toLong() * 30L) / MAX_DOWNLOAD_BYTES).toInt().coerceIn(0, 30)
                        onProgress(percent, 2, "Đang nhận dữ liệu ${(total / 1024)} KB")
                    }
                    output.toByteArray()
                }
                connection.disconnect()
                response = null
                onProgress(88, 3, "Đang phân tích nội dung")

                val isHtml = contentType.contains("text/html") ||
                    contentType.contains("application/xhtml+xml") ||
                    looksLikeHtml(bytes)
                val isText = isHtml || contentType.startsWith("text/") ||
                    contentType.contains("json") ||
                    contentType.contains("xml") ||
                    contentType.contains("javascript")

                if (isText && !disposition.contains("attachment", ignoreCase = true)) {
                    val charset = parseCharset(contentTypeHeader)
                    val rawText = String(bytes, charset)
                    if (isHtml) {
                        val accessGate = detectAccessGate(rawText)
                        if (accessGate != null) throw IOException(accessGate)

                        val clientRedirect = extractPublicRedirect(rawText, current.toString())
                        if (clientRedirect != null) {
                            if (redirects >= MAX_REDIRECTS) {
                                throw IOException("Liên kết có quá nhiều bước chuyển hướng tự động.")
                            }
                            current = validateUrl(current.resolve(clientRedirect.target).toString())
                            redirects += 1
                            onProgress(
                                (24 + redirects * 7).coerceAtMost(50),
                                1,
                                "Đã nhận diện chuyển hướng ${clientRedirect.kind} ($redirects/$MAX_REDIRECTS)"
                            )
                            continue
                        }

                        val extracted = extractHtml(rawText, current.toString())
                        onProgress(100, 3, "Đã trích xuất văn bản trang web")
                        return LinkProcessResult(
                            pageTitle = extracted.title,
                            finalUrl = current.toString(),
                            contentType = contentType.ifBlank { "text/html" },
                            extractedText = extracted.text,
                            isFile = false
                        )
                    }
                    val cleaned = rawText.replace("\u0000", "").trim().take(MAX_EXTRACTED_CHARS)
                    if (cleaned.isBlank()) throw IOException("Trang trả về nội dung văn bản trống.")
                    onProgress(100, 3, "Đã trích xuất văn bản")
                    return LinkProcessResult(
                        pageTitle = current.host,
                        finalUrl = current.toString(),
                        contentType = contentType.ifBlank { "text/plain" },
                        extractedText = cleaned,
                        isFile = false
                    )
                }

                val fileName = safeFileName(
                    extractFileName(disposition).ifBlank {
                        current.path.substringAfterLast('/').ifBlank { "tideus-download.bin" }
                    }
                )
                val mimeType = contentType.ifBlank { "application/octet-stream" }
                val saved = saveToDownloads(bytes, fileName, mimeType)
                onProgress(100, 3, "Đã lưu tệp vào thư mục Tải xuống")
                return LinkProcessResult(
                    pageTitle = fileName,
                    finalUrl = current.toString(),
                    contentType = mimeType,
                    extractedText = "Đã tải tệp: $fileName\nDung lượng: ${bytes.size / 1024} KB\nVị trí: ${saved.second}",
                    isFile = true,
                    fileName = fileName,
                    savedLocation = saved.second,
                    savedUri = saved.first
                )
            }
        } finally {
            response?.disconnect()
        }
    }

    private fun saveToDownloads(bytes: ByteArray, fileName: String, mimeType: String): Pair<Uri?, String> {
        fileSink?.let { return it(bytes, fileName, mimeType) }
        val appContext = context ?: throw IOException("Không có bộ lưu tệp trên thiết bị.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TIDEUS LINK")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Không thể tạo tệp trong thư mục Tải xuống.")
            try {
                appContext.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: throw IOException("Không thể ghi nội dung tệp đã tải.")
                val completed = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                appContext.contentResolver.update(uri, completed, null, null)
                return uri to "Downloads/TIDEUS LINK/$fileName"
            } catch (error: Exception) {
                appContext.contentResolver.delete(uri, null, null)
                throw error
            }
        }

        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "downloads")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Không thể tạo thư mục lưu tệp.")
        }
        val file = File(directory, fileName)
        file.writeBytes(bytes)
        return null to file.absolutePath
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 18_000
        private const val MAX_REDIRECTS = 8
        private const val MAX_DOWNLOAD_MB = 12
        private const val MAX_DOWNLOAD_BYTES = MAX_DOWNLOAD_MB * 1024 * 1024
        private const val MAX_EXTRACTED_CHARS = 12_000
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36 TIDEUS-LINK/0.5"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)


        internal data class HtmlExtraction(val title: String, val text: String)
        internal data class RedirectCandidate(val target: String, val kind: String)

        /**
         * Follows only destinations exposed as literal public URLs in standard
         * meta-refresh or simple inline location assignments. It does not run
         * page JavaScript, submit forms, solve challenges, or use browser cookies.
         */
        internal fun extractPublicRedirect(html: String, baseUrl: String): RedirectCandidate? {
            val document = Jsoup.parse(html, baseUrl)
            val refresh = document.selectFirst("meta[http-equiv=refresh][content]")
            if (refresh != null) {
                val refreshContent = refresh.attr("content")
                val quoted = Regex("""(?i)\\burl\\s*=\\s*['"]([^'"]+)['"]""")
                    .find(refreshContent)?.groupValues?.getOrNull(1)
                val unquoted = Regex("""(?i)\\burl\\s*=\\s*([^;]+)""")
                    .find(refreshContent)?.groupValues?.getOrNull(1)
                val target = (quoted ?: unquoted)?.trim()?.trim('"', '\\'')
                if (!target.isNullOrBlank()) {
                    return RedirectCandidate(target, "meta-refresh")
                }
            }

            val inlineScripts = document.select("script:not([src])")
                .joinToString("\\n") { script ->
                    script.data().ifBlank { script.html() }
                }

            val assignmentPatterns = listOf(
                Regex("""(?is)(?:window\\s*\\.\\s*)?(?:document\\s*\\.\\s*)?location(?:\\s*\\.\\s*href)?\\s*=\\s*(['"])(.*?)\\1"""),
                Regex("""(?is)(?:window\\s*\\.\\s*)?(?:document\\s*\\.\\s*)?location\\s*\\.\\s*(?:assign|replace)\\s*\\(\\s*(['"])(.*?)\\1\\s*\\)""")
            )
            for (pattern in assignmentPatterns) {
                val match = pattern.find(inlineScripts) ?: continue
                val target = match.groupValues.getOrNull(2)
                    ?.replace("\\\\/", "/")
                    ?.replace("\\\\u0026", "&", ignoreCase = true)
                    ?.replace("\\\\x3d", "=", ignoreCase = true)
                    ?.trim()
                    .orEmpty()
                if (target.isNotBlank()) return RedirectCandidate(target, "JavaScript tĩnh")
            }
            return null
        }

        /**
         * Reports common access gates. This is detection only: no attempt is
         * made to bypass CAPTCHA, authentication, subscription, or anti-bot controls.
         */
        internal fun detectAccessGate(html: String): String? {
            val document = Jsoup.parse(html)
            val title = document.title().lowercase(Locale.ROOT)
            if (title.contains("just a moment") ||
                title.contains("attention required") ||
                title.contains("checking your browser")) {
                return "Trang đang yêu cầu kiểm tra trình duyệt/chống bot. Hãy mở bằng trình duyệt và tự hoàn tất xác minh."
            }

            if (document.select(
                    "iframe[src*=recaptcha], iframe[src*=hcaptcha], .g-recaptcha, .h-captcha, [id*=captcha], input[name*=captcha]"
                ).isNotEmpty()) {
                return "Trang có CAPTCHA hoặc bước xác minh người dùng. TIDEUS LINK không tự giải CAPTCHA; hãy xác minh thủ công trong trình duyệt."
            }

            if (document.select("input[type=password]").isNotEmpty()) {
                return "Trang có biểu mẫu đăng nhập. Hãy đăng nhập trực tiếp trên trang chính thức; TIDEUS LINK không lấy hoặc vượt mật khẩu."
            }

            val bodyText = document.body()?.text().orEmpty().lowercase(Locale.ROOT)
            val paywallPhrases = listOf(
                "subscribe to continue",
                "subscribe to read",
                "subscription required",
                "this article is for subscribers",
                "purchase access to continue",
                "premium members only"
            )
            if (paywallPhrases.any { bodyText.contains(it) }) {
                return "Nội dung có dấu hiệu yêu cầu đăng ký hoặc trả phí. TIDEUS LINK không bỏ qua paywall."
            }

            val loginPhrases = listOf(
                "sign in to continue",
                "log in to continue",
                "login required to view"
            )
            if (loginPhrases.any { bodyText.contains(it) }) {
                return "Trang yêu cầu đăng nhập để xem nội dung. Hãy đăng nhập trực tiếp trên trang chính thức."
            }
            return null
        }

        internal fun validateUrl(raw: String): URI {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > 2048) throw IOException("Hãy nhập một đường dẫn hợp lệ.")
            val uri = try {
                URI(trimmed).normalize()
            } catch (_: Exception) {
                throw IOException("Đường dẫn không đúng định dạng URL.")
            }
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "https") throw IOException("Hiện chỉ hỗ trợ liên kết HTTPS an toàn.")
            if (uri.host.isNullOrBlank()) throw IOException("URL không có tên máy chủ hợp lệ.")
            if (uri.userInfo != null) throw IOException("Không chấp nhận URL chứa thông tin đăng nhập.")
            if (uri.port != -1 && uri.port != 443) throw IOException("Chỉ hỗ trợ cổng HTTPS tiêu chuẩn 443.")
            val host = uri.host.lowercase(Locale.ROOT).trimEnd('.')
            if (host == "localhost" || host.endsWith(".localhost") ||
                host.endsWith(".local") || host.endsWith(".internal") ||
                host.endsWith(".home") || host.endsWith(".lan")) {
                throw IOException("Địa chỉ máy chủ nội bộ không được phép.")
            }
            return uri
        }

        internal fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress ||
                address.isLinkLocalAddress || address.isSiteLocalAddress ||
                address.isMulticastAddress) return false

            val bytes = address.address
            if (address is Inet6Address && bytes.size == 16) {
                val mappedV4 = bytes.copyOfRange(0, 10).all { it.toInt() == 0 } &&
                    (bytes[10].toInt() and 0xff) == 0xff && (bytes[11].toInt() and 0xff) == 0xff
                if (mappedV4) {
                    val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                    return isPublicIpv4(v4.address)
                }
                val first = bytes[0].toInt() and 0xff
                if ((first and 0xfe) == 0xfc) return false
                if ((bytes[0].toInt() and 0xff) == 0x20 &&
                    (bytes[1].toInt() and 0xff) == 0x01 &&
                    (bytes[2].toInt() and 0xff) == 0x0d &&
                    (bytes[3].toInt() and 0xff) == 0xb8) return false
            }
            return if (bytes.size == 4) isPublicIpv4(bytes) else true
        }

        private fun isPublicIpv4(bytes: ByteArray): Boolean {
            if (bytes.size != 4) return false
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            val c = bytes[2].toInt() and 0xff
            return when {
                a == 0 || a == 10 || a == 127 || a >= 224 -> false
                a == 169 && b == 254 -> false
                a == 172 && b in 16..31 -> false
                a == 192 && b == 168 -> false
                a == 100 && b in 64..127 -> false
                a == 192 && b == 0 && c == 0 -> false
                a == 192 && b == 0 && c == 2 -> false
                a == 198 && b == 18 -> false
                a == 198 && b == 19 -> false
                a == 198 && b == 51 && c == 100 -> false
                a == 203 && b == 0 && c == 113 -> false
                else -> true
            }
        }

        internal fun validatePublicHost(host: String) {
            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (_: Exception) {
                throw IOException("Không phân giải được tên máy chủ.")
            }
            if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) }) {
                throw IOException("Máy chủ đích có địa chỉ riêng tư hoặc không an toàn; đã chặn yêu cầu.")
            }
        }

        internal fun extractHtml(html: String, baseUrl: String): HtmlExtraction {
            val document = Jsoup.parse(html, baseUrl)
            val title = document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.takeIf { it.isNotBlank() }
                ?: document.title().takeIf { it.isNotBlank() }
                ?: URI(baseUrl).host
                ?: "Trang web"

            document.select("script,style,noscript,svg,iframe,nav,footer,header,form,button,template").remove()
            val candidates = document.select("main,article,[role=main]").map { it.text().trim() }
                .filter { it.length > 40 }
            val mainText = candidates.maxByOrNull { it.length } ?: document.body()?.text().orEmpty()
            val description = document.selectFirst("meta[name=description]")?.attr("content")
                ?.takeIf { it.isNotBlank() }
                ?: document.selectFirst("meta[property=og:description]")?.attr("content")
                    ?.takeIf { it.isNotBlank() }
            val combined = buildString {
                append(title)
                if (!description.isNullOrBlank()) {
                    append("\n\n")
                    append(description)
                }
                if (mainText.isNotBlank() && !mainText.equals(title, true)) {
                    append("\n\n")
                    append(mainText)
                }
            }.trim().take(MAX_EXTRACTED_CHARS)
            if (combined.isBlank()) throw IOException("Không tìm thấy văn bản có thể trích xuất trên trang.")
            return HtmlExtraction(title, combined)
        }

        private fun looksLikeHtml(bytes: ByteArray): Boolean {
            val head = String(bytes, 0, minOf(bytes.size, 256), Charsets.UTF_8)
                .trimStart()
                .lowercase(Locale.ROOT)
            return head.startsWith("<!doctype html") || head.startsWith("<html") ||
                head.startsWith("<head") || head.startsWith("<body")
        }

        private fun parseCharset(contentType: String?): Charset {
            val charsetName = contentType
                ?.split(";")
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("charset=", true) }
                ?.substringAfter("=")
                ?.trim('"', '\'')
            return runCatching {
                if (charsetName.isNullOrBlank()) Charsets.UTF_8 else Charset.forName(charsetName)
            }.getOrDefault(Charsets.UTF_8)
        }

        private fun extractFileName(disposition: String): String {
            val encoded = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
                .find(disposition)?.groupValues?.getOrNull(1)
            if (!encoded.isNullOrBlank()) return runCatching {
                URLDecoder.decode(encoded, "UTF-8")
            }.getOrDefault(encoded)
            return Regex("filename=\"?([^\";]+)", RegexOption.IGNORE_CASE)
                .find(disposition)?.groupValues?.getOrNull(1).orEmpty()
        }

        private fun safeFileName(raw: String): String {
            val cleaned = raw.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._ -]"), "_")
                .trim()
                .take(100)
            return cleaned.ifBlank { "tideus-download.bin" }
        }
    }
}
