package com.example.ui.components

import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun MasterAnimeBrowserModal(
    url: String = "https://media.hmair.xyz",
    onDismiss: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Intercept hardware Back Button to navigate back in web history if possible
    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (customView != null) {
                // Fullscreen video/media view if requested by web content
                AndroidView(
                    factory = { customView!! },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Extremely small and thin Header bar containing ONLY Back, Refresh, and Close icons
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        color = Color(0xFF121214),
                        border = BorderStroke(0.5.dp, Color(0xFF2C2C2E))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Back Icon
                            IconButton(
                                onClick = {
                                    if (webViewRef?.canGoBack() == true) {
                                        webViewRef?.goBack()
                                    } else {
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Center: Refresh Icon
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Right: Close Icon
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Edge-to-Edge Embedded WebView Browser
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mediaPlaybackRequiresUserGesture = false
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(true)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 AppEmbedded/1.0"
                                }
                                isHapticFeedbackEnabled = true
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val reqUrl = request?.url?.toString() ?: return false
                                        if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                            view?.loadUrl(reqUrl)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: SslErrorHandler?,
                                        error: SslError?
                                    ) {
                                        handler?.proceed()
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onShowCustomView(
                                        view: android.view.View?,
                                        callback: CustomViewCallback?
                                    ) {
                                        customView = view
                                        customViewCallback = callback
                                    }

                                    override fun onHideCustomView() {
                                        customView = null
                                        customViewCallback?.onCustomViewHidden()
                                        customViewCallback = null
                                    }

                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: android.os.Message?
                                    ): Boolean {
                                        val targetWebView = view ?: return false
                                        val popWebView = WebView(targetWebView.context)
                                        popWebView.settings.javaScriptEnabled = true
                                        popWebView.settings.domStorageEnabled = true
                                        popWebView.webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                v: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val popUrl = request?.url?.toString()
                                                if (!popUrl.isNullOrBlank()) {
                                                    targetWebView.loadUrl(popUrl)
                                                }
                                                return true
                                            }
                                        }
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        if (transport != null) {
                                            transport.webView = popWebView
                                            resultMsg.sendToTarget()
                                        }
                                        return true
                                    }
                                }

                                loadUrl(url)
                                webViewRef = this
                            }
                        },
                        update = { view ->
                            webViewRef = view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun MasterAnimeBrowserScreen(
    url: String = "https://media.hmair.xyz",
    onBack: () -> Unit = {}
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (customView != null) {
            AndroidView(
                factory = { customView!! },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 72.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    color = Color(0xFF121214),
                    border = BorderStroke(0.5.dp, Color(0xFF2C2C2E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                if (webViewRef?.canGoBack() == true) {
                                    webViewRef?.goBack()
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mediaPlaybackRequiresUserGesture = false
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 AppEmbedded/1.0"
                            }
                            isHapticFeedbackEnabled = true
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val reqUrl = request?.url?.toString() ?: return false
                                    if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                        view?.loadUrl(reqUrl)
                                        return true
                                    }
                                    return false
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.proceed()
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onShowCustomView(
                                    view: android.view.View?,
                                    callback: CustomViewCallback?
                                ) {
                                    customView = view
                                    customViewCallback = callback
                                }

                                override fun onHideCustomView() {
                                    customView = null
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    val targetWebView = view ?: return false
                                    val popWebView = WebView(targetWebView.context)
                                    popWebView.settings.javaScriptEnabled = true
                                    popWebView.settings.domStorageEnabled = true
                                    popWebView.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val popUrl = request?.url?.toString()
                                            if (!popUrl.isNullOrBlank()) {
                                                targetWebView.loadUrl(popUrl)
                                            }
                                            return true
                                        }
                                    }
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    if (transport != null) {
                                        transport.webView = popWebView
                                        resultMsg.sendToTarget()
                                    }
                                    return true
                                }
                            }

                            loadUrl(url)
                            webViewRef = this
                        }
                    },
                    update = { view ->
                        webViewRef = view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

