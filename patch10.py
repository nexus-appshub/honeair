import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

target_update = """                    update = { webView ->
                        webViewRef = webView
                        if (webView.url != currentEmbedUrl) {
                            isLoading = true
                            hasError = false
                            val headers = mapOf("Referer" to currentEmbedUrl)
                            webView.loadUrl(currentEmbedUrl, headers)
                        }
                    },"""

replacement_update = """                    update = { webView ->
                        webViewRef = webView
                    },"""

content = content.replace(target_update, replacement_update)

target_androidview = """                AndroidView(
                    factory = { ctx ->"""

replacement_androidview = """                LaunchedEffect(currentEmbedUrl) {
                    webViewRef?.let { webView ->
                        isLoading = true
                        hasError = false
                        val headers = mapOf("Referer" to currentEmbedUrl)
                        webView.loadUrl(currentEmbedUrl, headers)
                    }
                }
                
                AndroidView(
                    factory = { ctx ->"""

content = content.replace(target_androidview, replacement_androidview)

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
