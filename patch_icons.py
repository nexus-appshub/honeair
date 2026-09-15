import re

with open("app/src/main/java/com/example/ui/components/SubscriptionPlanModal.kt", "r") as f:
    content = f.read()

content = content.replace('androidx.compose.material.icons.Icons.Default.Info', 'Icons.Default.Info')
content = content.replace('androidx.compose.material.icons.Icons.Default.OpenInNew', 'Icons.Default.OpenInNew')

# Add imports if they don't exist
if 'import androidx.compose.material.icons.filled.Info' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.Close', 'import androidx.compose.material.icons.filled.Close\nimport androidx.compose.material.icons.filled.Info\nimport androidx.compose.material.icons.filled.OpenInNew')

with open("app/src/main/java/com/example/ui/components/SubscriptionPlanModal.kt", "w") as f:
    f.write(content)
