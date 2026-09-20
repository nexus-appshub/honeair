package com.example.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Diagnostic OkHttp LoggingInterceptor to inspect and validate request and response headers.
 * Specifically checks and enforces:
 *  - 'User-Agent': Standard modern Chrome Desktop User-Agent.
 *  - 'Referer': Correctly pointed to Anikoto watch page or stream source host.
 *  - 'Origin': Cross-origin / same-origin integrity headers.
 *  - Sec-CH-UA / Sec-Fetch headers to bypass 403 Forbidden / Cloudflare WAF checks.
 */
class DiagnosticLoggingInterceptor(
    private val tag: String = "OkHttp-Diagnostic",
    private val enforceBypassHeaders: Boolean = true
) : Interceptor {

    companion object {
        const val STANDARD_CHROME_DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val ANIKOTO_BASE_URL = "https://anikoto.cz"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // 1. Enforce standard Chrome Desktop User-Agent on all requests
        requestBuilder.header("User-Agent", STANDARD_CHROME_DESKTOP_UA)

        // 2. Enforce Referer header: default to https://anikoto.cz/ if not specifically set
        val requestUrl = originalRequest.url.toString()
        val currentReferer = originalRequest.header("Referer")

        if (currentReferer.isNullOrBlank()) {
            if (requestUrl.contains("megaplay.buzz") || requestUrl.contains("kryntal.top")) {
                requestBuilder.header("Referer", "https://megaplay.buzz/")
                requestBuilder.header("Origin", "https://megaplay.buzz")
            } else if (requestUrl.contains("vidnest")) {
                requestBuilder.header("Referer", "https://vidnest.fun/")
                requestBuilder.header("Origin", "https://vidnest.fun")
            } else {
                requestBuilder.header("Referer", "$ANIKOTO_BASE_URL/")
                requestBuilder.header("Origin", ANIKOTO_BASE_URL)
            }
        }

        // 3. Add modern browser Sec-Fetch & Sec-Ch-Ua client hints
        if (originalRequest.header("sec-ch-ua") == null && enforceBypassHeaders) {
            requestBuilder.header("sec-ch-ua", "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not=A?Brand\";v=\"24\"")
            requestBuilder.header("sec-ch-ua-mobile", "?0")
            requestBuilder.header("sec-ch-ua-platform", "\"Windows\"")
            requestBuilder.header("sec-fetch-dest", "empty")
            requestBuilder.header("sec-fetch-mode", "cors")
            requestBuilder.header("sec-fetch-site", "same-origin")
        }

        val request = requestBuilder.build()

        // 4. Diagnostic Logging of Outgoing Request & Headers
        val startNs = System.nanoTime()
        Log.d(tag, "================================================================================")
        Log.d(tag, "--> [OUTGOING REQUEST] ${request.method} ${request.url}")
        Log.d(tag, "--> Headers Inspection (${request.headers.size} headers):")
        
        var hasValidUserAgent = false
        var hasValidReferer = false

        for (i in 0 until request.headers.size) {
            val name = request.headers.name(i)
            val value = request.headers.value(i)
            val isHighlight = name.equals("User-Agent", ignoreCase = true) ||
                    name.equals("Referer", ignoreCase = true) ||
                    name.equals("Origin", ignoreCase = true) ||
                    name.startsWith("sec-", ignoreCase = true)

            if (name.equals("User-Agent", ignoreCase = true)) {
                hasValidUserAgent = value.contains("Chrome") && !value.contains("Mobile")
            }
            if (name.equals("Referer", ignoreCase = true)) {
                hasValidReferer = value.isNotBlank()
            }

            val prefix = if (isHighlight) "  ⭐️ [CRITICAL HEADER] " else "  • "
            Log.d(tag, "$prefix$name: $value")
        }

        // Diagnostic Check Summary
        if (hasValidUserAgent) {
            Log.d(tag, "  ✅ [STATUS] User-Agent is correctly set to Desktop Chrome.")
        } else {
            Log.w(tag, "  ⚠️ [WARNING] User-Agent may trigger bot detection / 403 Forbidden!")
        }

        if (hasValidReferer) {
            Log.d(tag, "  ✅ [STATUS] Referer header is active: ${request.header("Referer")}")
        } else {
            Log.w(tag, "  ⚠️ [WARNING] Referer is MISSING! Target server may reject with 403 Forbidden.")
        }
        Log.d(tag, "--> END OUTGOING REQUEST")

        // 5. Execute Call & Inspect Response
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            Log.e(tag, "<-- [REQUEST FAILED] ${e.javaClass.simpleName}: ${e.message}")
            Log.d(tag, "================================================================================")
            throw e
        }

        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val code = response.code
        val statusSymbol = if (response.isSuccessful) "✅" else "❌"

        Log.d(tag, "<-- [RESPONSE] $statusSymbol HTTP $code ${response.message} (${tookMs}ms)")
        Log.d(tag, "<-- Response Headers:")
        for (i in 0 until response.headers.size) {
            val name = response.headers.name(i)
            val value = response.headers.value(i)
            if (name.equals("Content-Type", ignoreCase = true) ||
                name.equals("Content-Length", ignoreCase = true) ||
                name.equals("Server", ignoreCase = true) ||
                name.startsWith("cf-", ignoreCase = true)
            ) {
                Log.d(tag, "  • $name: $value")
            }
        }

        if (code == 403) {
            Log.e(tag, "❌ [DIAGNOSTIC ERROR 403 FORBIDDEN DETECTED]")
            Log.e(tag, "❌ Request to ${request.url} was blocked by the host/WAF (Cloudflare/CDN anti-hotlinking).")
            Log.e(tag, "❌ Verify that Referer is set to the exact watch page URL or embed origin, and User-Agent is standard Chrome desktop.")
        } else if (code == 200) {
            Log.d(tag, "✅ [DIAGNOSTIC SUCCESS] Headers passed anti-bot/WAF validation successfully.")
        }

        Log.d(tag, "================================================================================")
        return response
    }
}
