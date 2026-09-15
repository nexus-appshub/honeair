import re

with open("app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt", "r") as f:
    content = f.read()

new_func = """    fun isChannelPremium(channel: IptvChannel): Boolean {
        val config = _appControlConfig.value ?: return false
        val cleanName = channel.name.trim()
        val cleanUrl = channel.url.trim()
        val cleanGroup = channel.group.trim().lowercase()
        val cleanTvgId = channel.tvgId.trim()
        
        if (config.isLiveTvLockEnabled) {
            // Check individual channel flag from API
            if (channel.isPremium) return true

            // 1. Check if group/category is in specific Live TV premium categories (completely separate from movie/anime categories)
            if (cleanGroup.isNotBlank() && config.premiumLiveTvCategories.any { cleanGroup.contains(it.lowercase()) }) {
                return true
            }
            
            // 2. Check if channel name, URL, or tvgId matches any specific Live TV premium channel IDs/names
            if (config.premiumLiveTvIds.any {
                cleanName.contains(it, ignoreCase = true) ||
                cleanUrl.contains(it, ignoreCase = true) ||
                (cleanTvgId.isNotBlank() && cleanTvgId.equals(it.trim(), ignoreCase = true))
            }) {
                return true
            }
        }
"""

content = re.sub(r'    fun isChannelPremium\(channel: IptvChannel\): Boolean \{.*?(?=        // 3\. Fallback)', new_func, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt", "w") as f:
    f.write(content)
