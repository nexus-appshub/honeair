with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Remove any prepended imports before package com.example
if "package com.example" in content:
    idx = content.find("package com.example")
    content = content[idx:]

# Ensure required imports are right after package com.example
imports_block = """package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
"""

content = content.replace("package com.example", imports_block)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

print("MainActivity header fixed successfully!")
