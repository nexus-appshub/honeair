import re

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

target = """                                val adBlockList = listOf(
                                    "popunder", "propeller", "exoclick", "onclick", "popads", "syndication", 
                                    "googleads", "doubleclick", "analytics", "tracker", "adsystem", 
                                    "chatbox", "bet365", "1xbet", "popup"
                                )
                                for (ad in adBlockList) {
                                    if (urlStrLower.contains(ad)) {
                                        return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                                    }
                                }"""

replacement = """                                val adBlockList = listOf(
                                    "popunder", "propeller", "exoclick", "onclick", "popads", "syndication", 
                                    "googleads", "doubleclick", "analytics", "tracker", "adsystem", 
                                    "chatbox", "bet365", "1xbet", "popup", "listats"
                                )
                                for (ad in adBlockList) {
                                    if (urlStrLower.contains(ad)) {
                                        return android.webkit.WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                                    }
                                }"""

new_content = content.replace(target, replacement)
with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.write(new_content)
