import re

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'r') as f:
    content = f.read()

target = """            val sigItemWords = itemWords.filter { it.length > 2 }
            val sigChannelWords = channelWords.filter { it.length > 2 }
            
            if (sigItemWords.isNotEmpty() && sigChannelWords.isNotEmpty()) {
                if (sigItemWords.all { w -> 
                    sigChannelWords.any { cw -> 
                        w == cw || (cw.length >= 4 && w.contains(cw)) || (w.length >= 4 && cw.contains(w))
                    } 
                }) {
                    return true
                }
            }"""

replacement = """            val stopWords = setOf("the", "and", "for", "with", "of", "in", "to", "is", "on", "at")
            val sigItemWords = itemWords.filter { it.length > 2 && !stopWords.contains(it) }.toSet()
            val sigChannelWords = channelWords.filter { it.length > 2 && !stopWords.contains(it) }.toSet()
            
            if (sigItemWords.isNotEmpty() && sigChannelWords.isNotEmpty()) {
                val matchedWords = sigItemWords.filter { w -> 
                    sigChannelWords.any { cw -> 
                        w == cw || (cw.length >= 4 && w.contains(cw)) || (w.length >= 4 && cw.contains(w))
                    } 
                }
                
                val ratio = matchedWords.size.toFloat() / sigItemWords.size
                if (matchedWords.size == sigItemWords.size || (matchedWords.size >= 2 && ratio >= 0.59f)) {
                    return true
                }
            }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'w') as f:
    f.write(content)
