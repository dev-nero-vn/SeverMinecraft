package com.tideus.link

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.URI

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
        val processor = LinkProcessor(ContextWrapper(null))
        val result = processor.process("https://example.com") { _, _, _ -> }
        assertFalse(result.isFile)
        assertEquals("example.com", URI(result.finalUrl).host)
        assertTrue(result.extractedText.contains("Example Domain"))
    }

    @Test
    fun followsARealPublicHttpRedirect() {
        // httpbin responds with an HTTP redirect to the public example.com page.
        val processor = LinkProcessor(ContextWrapper(null))
        val redirect = "https://httpbin.org/redirect-to?url=https%3A%2F%2Fexample.com"
        val result = processor.process(redirect) { _, _, _ -> }
        assertFalse(result.isFile)
        assertEquals("example.com", URI(result.finalUrl).host)
        assertTrue(result.extractedText.contains("Example Domain"))
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
