import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

target1 = """                                            if (currentServerIndex < embedServers.size - 1) {
                                                currentServerIndex += 1
                                                view?.post { 
                                                    val headers = mapOf("Referer" to embedServers[currentServerIndex].second)
                                                    view.loadUrl(embedServers[currentServerIndex].second, headers) 
                                                }
                                            } else {
                                                hasError = true
                                                isLoading = false
                                            }"""

replacement1 = """                                            if (currentServerIndex < embedServers.size - 1) {
                                                currentServerIndex += 1
                                            } else {
                                                hasError = true
                                                isLoading = false
                                            }"""

content = content.replace(target1, replacement1)

target2 = """                                        if (currentServerIndex < embedServers.size - 1) {
                                            currentServerIndex += 1
                                            val headers = mapOf("Referer" to embedServers[currentServerIndex].second)
                                            view?.loadUrl(embedServers[currentServerIndex].second, headers)
                                        } else {
                                            hasError = true
                                            isLoading = false
                                        }"""

replacement2 = """                                        if (currentServerIndex < embedServers.size - 1) {
                                            currentServerIndex += 1
                                        } else {
                                            hasError = true
                                            isLoading = false
                                        }"""

content = content.replace(target2, replacement2)

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
