package com.example.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object InAppHeadlessWebScraper {
    private const val TAG = "HeadlessWebScraper"

    class ScraperBridge(private val onCaptured: (String, String) -> Unit) {
        @JavascriptInterface
        fun onStreamCaptured(streamUrl: String, referer: String) {
            if (streamUrl.isNotBlank()) {
                onCaptured(streamUrl, referer)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractWebStream(
        context: Context,
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ScrapedStreamResult? = suspendCancellableCoroutine { continuation ->

        Handler(Looper.getMainLooper()).post {
            val isNumeric = tmdbId.all { it.isDigit() }
            val cleanId = tmdbId.trim()
            val effectiveImdb = if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt")) imdbId.trim() else null

            val providerUrls = mutableListOf<Pair<String, String>>()
            if (isTv) {
                providerUrls.add(Pair("https://vidlink.pro/tv/$cleanId/$season/$episode", "https://vidlink.pro/"))
                providerUrls.add(Pair("https://player.autoembed.cc/embed/tv/$cleanId/$season/$episode", "https://player.autoembed.cc/"))
                providerUrls.add(Pair("https://vidsrc.me/embed/tv?${if (isNumeric) "tmdb" else "imdb"}=$cleanId&season=$season&episode=$episode", "https://vidsrc.me/"))
                if (!effectiveImdb.isNullOrBlank()) {
                    providerUrls.add(Pair("https://vidsrc.me/embed/tv?imdb=$effectiveImdb&season=$season&episode=$episode", "https://vidsrc.me/"))
                    providerUrls.add(Pair("https://www.2embed.cc/embedtvfull/$effectiveImdb/$season/$episode", "https://www.2embed.cc/"))
                }
                providerUrls.add(Pair("https://vidrock.net/embed/tv/$cleanId/$season/$episode", "https://vidrock.net/"))
                providerUrls.add(Pair("https://www.2embed.cc/embedtv/$cleanId&s=$season&e=$episode", "https://www.2embed.cc/"))
                providerUrls.add(Pair("https://player.smashy.stream/tv/$cleanId?s=$season&e=$episode", "https://player.smashy.stream/"))
                providerUrls.add(Pair("https://multiembed.mov/?video_id=$cleanId&tmdb=1&s=$season&e=$episode", "https://multiembed.mov/"))
                providerUrls.add(Pair("https://player.videasy.net/tv/$cleanId/$season/$episode", "https://player.videasy.net/"))
                providerUrls.add(Pair("https://vidsrc.vip/embed/tv/$cleanId/$season/$episode", "https://vidsrc.vip/"))
                providerUrls.add(Pair("https://vidsrc.to/embed/tv/$cleanId/$season/$episode", "https://vidsrc.to/"))
                providerUrls.add(Pair("https://vidsrc.xyz/embed/tv/$cleanId/$season/$episode", "https://vidsrc.xyz/"))
                providerUrls.add(Pair("https://vidnest.fun/tv/$cleanId/$season/$episode", "https://vidnest.fun/"))
            } else {
                providerUrls.add(Pair("https://vidlink.pro/movie/$cleanId", "https://vidlink.pro/"))
                providerUrls.add(Pair("https://player.autoembed.cc/embed/movie/$cleanId", "https://player.autoembed.cc/"))
                providerUrls.add(Pair("https://vidsrc.me/embed/movie?${if (isNumeric) "tmdb" else "imdb"}=$cleanId", "https://vidsrc.me/"))
                if (!effectiveImdb.isNullOrBlank()) {
                    providerUrls.add(Pair("https://vidsrc.me/embed/movie?imdb=$effectiveImdb", "https://vidsrc.me/"))
                    providerUrls.add(Pair("https://www.2embed.cc/embed/$effectiveImdb", "https://www.2embed.cc/"))
                }
                providerUrls.add(Pair("https://vidrock.net/embed/movie/$cleanId", "https://vidrock.net/"))
                providerUrls.add(Pair("https://www.2embed.cc/embed/$cleanId", "https://www.2embed.cc/"))
                providerUrls.add(Pair("https://player.smashy.stream/movie/$cleanId", "https://player.smashy.stream/"))
                providerUrls.add(Pair("https://multiembed.mov/?video_id=$cleanId&tmdb=1", "https://multiembed.mov/"))
                providerUrls.add(Pair("https://player.videasy.net/movie/$cleanId", "https://player.videasy.net/"))
                providerUrls.add(Pair("https://vidsrc.vip/embed/movie/$cleanId", "https://vidsrc.vip/"))
                providerUrls.add(Pair("https://vidsrc.to/embed/movie/$cleanId", "https://vidsrc.to/"))
                providerUrls.add(Pair("https://vidsrc.xyz/embed/movie/$cleanId", "https://vidsrc.xyz/"))
                providerUrls.add(Pair("https://vidnest.fun/movie/$cleanId", "https://vidnest.fun/"))
            }

            var webView: WebView? = null
            var hasResumed = false
            var currentProviderIndex = 0
            val handler = Handler(Looper.getMainLooper())
            val capturedSubs = mutableListOf<SubtitleTrack>()

            fun cleanup() {
                try {
                    webView?.stopLoading()
                    webView?.loadUrl("about:blank")
                    webView?.destroy()
                    webView = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            fun completeWithResult(streamUrl: String, referer: String) {
                if (!hasResumed) {
                    hasResumed = true
                    Log.d(TAG, "Successfully intercepted stream via Headless Web: $streamUrl (Referer: $referer)")
                    cleanup()
                    if (continuation.isActive) {
                        val headers = mutableMapOf(
                            "Referer" to referer,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
                            "Origin" to referer.removeSuffix("/")
                        )
                        continuation.resume(
                            ScrapedStreamResult(
                                streamUrl = streamUrl,
                                referer = referer,
                                headers = headers,
                                subtitles = capturedSubs.distinctBy { it.url }
                            )
                        )
                    }
                }
            }

            var cycleRunnable: Runnable? = null

            fun tryNextProvider() {
                if (hasResumed) return
                if (currentProviderIndex >= providerUrls.size) {
                    if (!hasResumed) {
                        hasResumed = true
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                    return
                }

                val (targetUrl, ref) = providerUrls[currentProviderIndex]
                currentProviderIndex++
                Log.d(TAG, "Headless scraper loading provider [${currentProviderIndex}/${providerUrls.size}]: $targetUrl")

                try {
                    val customHeaders = mapOf(
                        "Referer" to ref,
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
                    )
                    webView?.loadUrl(targetUrl, customHeaders)
                } catch (e: Exception) {
                    Log.w(TAG, "Error loading provider $targetUrl: ${e.message}")
                }

                cycleRunnable?.let { handler.removeCallbacks(it) }
                cycleRunnable = Runnable {
                    if (!hasResumed) {
                        tryNextProvider()
                    }
                }
                handler.postDelayed(cycleRunnable!!, 3800)
            }

            val overallTimeoutRunnable = Runnable {
                if (!hasResumed) {
                    hasResumed = true
                    cycleRunnable?.let { handler.removeCallbacks(it) }
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
            handler.postDelayed(overallTimeoutRunnable, 22000)

            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
                    addJavascriptInterface(
                        ScraperBridge { url, ref ->
                            handler.post {
                                completeWithResult(url, if (ref.isNotBlank()) ref else "https://vidlink.pro/")
                            }
                        },
                        "AndroidScraperBridge"
                    )
                }

                val jsHook = """
                    (function() {
                        try {
                            if (window.__scraperHookInstalled) return;
                            window.__scraperHookInstalled = true;
                            
                            function report(url) {
                                if (url && (url.includes('.m3u8') || url.includes('.mp4')) && !url.includes('analytics') && !url.includes('doubleclick') && !url.includes('favicon')) {
                                    if (window.AndroidScraperBridge && window.AndroidScraperBridge.onStreamCaptured) {
                                        window.AndroidScraperBridge.onStreamCaptured(url, window.location.href);
                                    }
                                }
                            }

                            var origFetch = window.fetch;
                            if (origFetch) {
                                window.fetch = function() {
                                    var arg = arguments[0];
                                    if (typeof arg === 'string') { report(arg); }
                                    else if (arg && arg.url) { report(arg.url); }
                                    return origFetch.apply(this, arguments);
                                };
                            }

                            var origOpen = XMLHttpRequest.prototype.open;
                            XMLHttpRequest.prototype.open = function(method, url) {
                                if (typeof url === 'string') { report(url); }
                                return origOpen.apply(this, arguments);
                            };

                            var checkMedia = function() {
                                var vids = document.querySelectorAll('video, audio, source');
                                for (var i = 0; i < vids.length; i++) {
                                    var src = vids[i].src || vids[i].currentSrc;
                                    if (src) report(src);
                                }
                                var plays = document.querySelectorAll('.play-btn, #play, .jw-display-icon-container, [aria-label="Play"], button.vjs-big-play-button, .vjs-big-play-button, #player, .plyr__control--overlaid');
                                for (var j = 0; j < plays.length; j++) {
                                    try { plays[j].click(); } catch(e){}
                                }
                            };
                            setInterval(checkMedia, 500);
                        } catch(e) {}
                    })();
                """.trimIndent()

                webView?.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: ""
                        val lower = url.lowercase()

                        // Subtitles interception
                        if (lower.endsWith(".vtt") || lower.endsWith(".srt") || lower.contains("/subs/") || lower.contains("/subtitles/")) {
                            if (!lower.contains("preview") && !lower.contains("thumb")) {
                                capturedSubs.add(
                                    SubtitleTrack(
                                        url = url,
                                        label = "English",
                                        lang = "en",
                                        default = capturedSubs.isEmpty()
                                    )
                                )
                            }
                        }

                        // Filter unnecessary heavy telemetry/media
                        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".ttf") ||
                            lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.contains("google-analytics") ||
                            lower.contains("doubleclick") || lower.contains("googlesyndication")
                        ) {
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }

                        if ((url.contains(".m3u8", ignoreCase = true) || (url.contains(".mp4", ignoreCase = true) && !url.contains("google", ignoreCase = true))) &&
                            !url.contains("analytics", ignoreCase = true) && !url.contains("demo", ignoreCase = true) && !url.contains("favicon", ignoreCase = true)
                        ) {
                            handler.post {
                                val currentRef = if (currentProviderIndex > 0 && currentProviderIndex - 1 < providerUrls.size) {
                                    providerUrls[currentProviderIndex - 1].second
                                } else {
                                    "https://vidlink.pro/"
                                }
                                completeWithResult(url, currentRef)
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view?.evaluateJavascript(jsHook, null)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(jsHook, null)
                        view?.evaluateJavascript(
                            "(function() { var v = document.querySelector('video'); if(v) { v.play(); } })();",
                            null
                        )
                    }
                }

                tryNextProvider()

            } catch (e: Exception) {
                if (!hasResumed) {
                    hasResumed = true
                    cycleRunnable?.let { handler.removeCallbacks(it) }
                    handler.removeCallbacks(overallTimeoutRunnable)
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }

            continuation.invokeOnCancellation {
                cycleRunnable?.let { handler.removeCallbacks(it) }
                handler.removeCallbacks(overallTimeoutRunnable)
                handler.post { cleanup() }
            }
        }
    }
}
