import re

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'r') as f:
    content = f.read()

target = """            // Contains match with safer length check
            if (cleanItemTitle.length >= 6 && cleanChannelName.length >= 6) {
                if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                    return true
                }
            }"""

replacement = """            // Contains match with safer length check
            if (cleanItemTitle.length >= 6 && cleanChannelName.length >= 6) {
                if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                    // Make sure it's not a sequel being matched by a shorter original movie
                    if (cleanChannelName.length > cleanItemTitle.length + 8 || cleanItemTitle.length > cleanChannelName.length + 8) {
                       // Do not return true directly, wait for word-level match which has sequel prevention 
                    } else {
                        return true
                    }
                }
            }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'w') as f:
    f.write(content)
