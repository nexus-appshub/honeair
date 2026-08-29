import re

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'r') as f:
    content = f.read()

target = """                if (matchedWords.size == sigItemWords.size || (matchedWords.size >= 2 && ratio >= 0.59f)) {
                    // Make sure the matched words aren't just part of a longer sequel title (e.g. "Avatar" matching "Avatar Fire and Ash")
                    // If the channel has >= 2 more significant words than the original title, it's likely a sequel
                    if (sigItemWords.size == 1 && sigChannelWords.size > 1) {
                        // Single word title like "Avatar" must not blindly match "Avatar 2" or "Avatar Fire and Ash"
                        // Skip
                    } else if (sigChannelWords.size <= sigItemWords.size + 1) {
                        return true
                    }
                }"""

replacement = """                if (matchedWords.size == sigItemWords.size || (matchedWords.size >= 2 && ratio >= 0.59f)) {
                    if (sigItemWords.size == 1 && sigChannelWords.size > 1) {
                        // Single word title like "Avatar" must not blindly match "Avatar 2" or "Avatar Fire and Ash"
                    } else if (sigChannelWords.size <= sigItemWords.size + 1) {
                        return true
                    }
                }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'w') as f:
    f.write(content)
