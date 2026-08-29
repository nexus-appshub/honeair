import re

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

target = """                                val fitCss = \"\"\"
                                    javascript:(function() {
                                        var style = document.createElement('style');
                                        style.innerHTML = 'html, body { margin: 0; padding: 0; width: 100vw !important; height: 100vh !important; background: #000 !important; overflow: hidden !important; display: flex !important; justify-content: center !important; align-items: center !important; } iframe, video { width: 100% !important; height: 100% !important; max-width: 100% !important; max-height: 100% !important; object-fit: contain !important; border: none !important; }';
                                        document.head.appendChild(style);
                                    })()
                                \"\"\".trimIndent()"""

replacement = """                                val fitCss = \"\"\"
                                    javascript:(function() {
                                        var style = document.createElement('style');
                                        style.innerHTML = 'html, body { margin: 0; padding: 0; width: 100% !important; height: 100% !important; background: #000 !important; overflow: hidden !important; } iframe, video { width: 100% !important; height: 100% !important; max-width: 100% !important; max-height: 100% !important; object-fit: contain !important; border: none !important; }';
                                        document.head.appendChild(style);
                                    })()
                                \"\"\".trimIndent()"""

new_content = content.replace(target, replacement)
if new_content == content:
    print("NO MATCH FOUND")
else:
    with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
        f.write(new_content)
    print("REPLACED SUCCESSFULLY")
