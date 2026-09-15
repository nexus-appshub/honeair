import re

with open("app/src/main/java/com/example/subscription/SubscriptionManager.kt", "r") as f:
    content = f.read()

new_func = """    fun checkUserSubscription(email: String?, uid: String?) {
        val cleanEmail = email?.trim()?.lowercase()
        recomputeStatus(cleanEmail, uid)
    }

    private fun recomputeStatus(email: String?, uid: String?) {"""

content = re.sub(r'    fun checkUserSubscription\(email: String\?, uid: String\?\).*?    private fun recomputeStatus\(email: String\?, uid: String\?\) \{', new_func, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/subscription/SubscriptionManager.kt", "w") as f:
    f.write(content)
