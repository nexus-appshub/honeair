import re

with open("app/src/main/java/com/example/ui/screens/UserProfileBottomSheet.kt", "r") as f:
    content = f.read()

content = content.replace('isRedeemActive -> "VIP Active (Promo Unlock)"', 'isRedeemActive -> "VIP Active ($redeemPlanName)"')
content = content.replace('val dateSubText = if (isRedeemActive) {', 'val dateSubText = if (isRedeemActive) {\n                                        val fmt = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())\n                                        "Plan: $redeemPlanName • Expires on: ${fmt.format(java.util.Date(redeemExpiry))}"')

# Also fix the duplicate format block
content = re.sub(r'val dateSubText = if \(isRedeemActive\) \{\s*val fmt = java\.text\.SimpleDateFormat\("dd MMM yyyy, HH:mm", java\.util\.Locale\.getDefault\(\)\)\s*"Plan: \$redeemPlanName • Expires on: \$\{fmt\.format\(java\.util\.Date\(redeemExpiry\)\)\}"\s*val fmt = java\.text\.SimpleDateFormat\("dd MMM yyyy, HH:mm", java\.util\.Locale\.getDefault\(\)\)\s*"Expires on: \$\{fmt\.format\(java\.util\.Date\(redeemExpiry\)\)\}"', 'val dateSubText = if (isRedeemActive) {\n                                        val fmt = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())\n                                        "Plan: $redeemPlanName • Expires on: ${fmt.format(java.util.Date(redeemExpiry))}"', content)


with open("app/src/main/java/com/example/ui/screens/UserProfileBottomSheet.kt", "w") as f:
    f.write(content)
