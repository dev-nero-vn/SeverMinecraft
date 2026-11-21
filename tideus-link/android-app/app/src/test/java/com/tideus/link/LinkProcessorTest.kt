package com.tideus.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.io.File
import java.nio.file.Files

class LinkProcessorTest {

    @Test
    fun rejectsNonHttpsAndCredentialUrls() {
        assertThrowsIOException { LinkProcessor.validateUrl("http://example.com") }
        assertThrowsIOException { LinkProcessor.validateUrl("file:///etc/passwd") }
        assertThrowsIOException { LinkProcessor.validateUrl("https://user:password@example.com/") }
    }

    @Test
    fun blocksPrivateAndLoopbackAddresses() {
        assertFalse(LinkProcessor.isPublicAddress(InetAddress.getByName("127.0.0.1")))
        assertFalse(LinkProcessor.isPublicAddress(InetAddress.getByName("10.0.0.2")))
        assertFalse(LinkProcessor.isPublicAddress(InetAddress.getByName("192.168.1.1")))
        assertTrue(LinkProcessor.isPublicAddress(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun extractsMainArticleAndRemovesNavigationAndScripts() {
        val html = """
            <!doctype html>
            <html>
              <head><title>Example article</title><meta name="description" content="A useful description"></head>
              <body>
                <nav>Navigation should not be extracted</nav>
                <main><article><h1>Article heading</h1><p>This is the readable article text that the application should collect from a public page.</p></article></main>
                <script>secretFakeContent()</script>
              </body>
            </html>
        """.trimIndent()
        val extracted = LinkProcessor.extractHtml(html, "https://example.com/article")
        assertEquals("Example article", extracted.title)
        assertTrue(extracted.text.contains("Article heading"))
        assertTrue(extracted.text.contains("A useful description"))
        assertFalse(extracted.text.contains("Navigation should not be extracted"))
        assertFalse(extracted.text.contains("secretFakeContent"))
    }

    @Test
    fun performsRealHttpsFetchAndExtractsPublicPage() {
        // Live smoke test: verifies DNS, TLS, HTTP retrieval and HTML extraction end-to-end.
        val processor = LinkProcessor()
        val result = processor.process("https://example.com") { _, _, _ -> }
        assertFalse(result.isFile)
        assertEquals("example.com", URI(result.finalUrl).host)
        assertTrue(result.extractedText.contains("Example Domain"))
    }

    @Test
    fun followsARealPublicHttpRedirect() {
        // httpbin responds with an HTTP redirect to the public example.com page.
        val processor = LinkProcessor()
        val redirect = "https://httpbin.org/redirect-to?url=https%3A%2F%2Fexample.com"
        val result = processor.process(redirect) { _, _, _ -> }
        assertFalse(result.isFile)
        assertEquals("example.com", URI(result.finalUrl).host)
        assertTrue(result.extractedText.contains("Example Domain"))
    }

    @Test
    fun downloadsARealPublicBinaryFile() {
        val directory = Files.createTempDirectory("tideus-link-test").toFile()
        val processor = LinkProcessor(fileSink = { bytes, name, _ ->
            val output = File(directory, name)
            output.writeBytes(bytes)
            null to output.absolutePath
        })
        val result = processor.process("https://httpbin.org/bytes/128") { _, _, _ -> }
        assertTrue(result.isFile)
        assertEquals(128L, File(result.savedLocation!!).length())
    }


    @Test
    fun detectsQuotedMetaRefreshRedirect() {
        val html = """
            <html><head>
              <meta http-equiv="refresh" content="0; url='/final/page?from=short'">
            </head><body>Redirecting</body></html>
        """.trimIndent()
        val redirect = LinkProcessor.extractPublicRedirect(html, "https://short.example/start")
        assertEquals("meta-refresh", redirect?.kind)
        assertEquals(
            "https://short.example/final/page?from=short",
            URI("https://short.example/start").resolve(redirect!!.target).toString()
        )
    }

    @Test
    fun detectsLiteralJavascriptLocationRedirects() {
        val assignmentHtml = """<html><script>window.location.href = "https://target.example/path?a=1";</script></html>"""
        val assignment = LinkProcessor.extractPublicRedirect(assignmentHtml, "https://short.example/")
        assertEquals("JavaScript tĩnh", assignment?.kind)
        assertEquals("https://target.example/path?a=1", assignment?.target)

        val replaceHtml = """<html><script>location.replace("../destination");</script></html>"""
        val replace = LinkProcessor.extractPublicRedirect(replaceHtml, "https://short.example/a/start")
        assertEquals("../destination", replace?.target)
    }

    @Test
    fun doesNotExecuteDynamicJavascriptOrDecodeHiddenTargets() {
        val html = """<html><script>window.location.href = atob("aHR0cHM6Ly9leGFtcGxlLmNvbQ==");</script></html>"""
        assertEquals(null, LinkProcessor.extractPublicRedirect(html, "https://short.example/"))
    }

    @Test
    fun detectsCaptchaLoginAndSubscriptionGatesWithoutBypassingThem() {
        val captcha = """<html><head><title>Verify</title></head><body><div class="g-recaptcha"></div></body></html>"""
        assertTrue(LinkProcessor.detectAccessGate(captcha)?.contains("CAPTCHA") == true)

        val login = """<html><body><form action="/login"><input type="password"></form></body></html>"""
        assertTrue(LinkProcessor.detectAccessGate(login)?.contains("đăng nhập") == true)

        val paywall = """<html><body>This article is for subscribers. Subscribe to continue.</body></html>"""
        assertTrue(LinkProcessor.detectAccessGate(paywall)?.contains("paywall") == true)
    }

    private fun assertThrowsIOException(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (_: IOException) {
            // Expected validation failure.
        }
    }
}
