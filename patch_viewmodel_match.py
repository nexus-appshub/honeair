import re

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'r') as f:
    content = f.read()

target = """        if (cleanItemTitle.isNotEmpty() && cleanChannelName.isNotEmpty()) {
            if (cleanChannelName == cleanItemTitle) return true
            
            if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                return true
            }
            
            val cleanItemTitleWithSpaces = normalizeMediaTitle(item.title)"""

replacement = """        if (cleanItemTitle.isNotEmpty() && cleanChannelName.isNotEmpty()) {
            if (cleanChannelName == cleanItemTitle) return true
            
            if (cleanItemTitle.length >= 6 && cleanChannelName.length >= 6) {
                if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                    return true
                }
            }
            
            val cleanItemTitleWithSpaces = normalizeMediaTitle(item.title)"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'w') as f:
    f.write(content)
