import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# For M3uPlaylistsManagerSubPage
content = content.replace(
    "    val customPlaylistsState by viewModel.customPlaylists.collectAsState()\n    val context = LocalContext.current\n",
    "    val customPlaylistsState by viewModel.customPlaylists.collectAsState()\n    val context = LocalContext.current\n    val isDark = androidx.compose.foundation.isSystemInDarkTheme()\n    val textColor = if (isDark) Color.White else Color.Black\n"
)

# For LocalVideosManagerSubPage
content = content.replace(
    "fun LocalVideosManagerSubPage(\n    viewModel: StreamViewModel,\n    onBack: () -> Unit,\n    multiPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,\n    selectedQueueVideos: androidx.compose.runtime.snapshots.SnapshotStateList<java.io.File>,\n    onPlayFile: (java.io.File) -> Unit\n) {\n    val context = LocalContext.current",
    "fun LocalVideosManagerSubPage(\n    viewModel: StreamViewModel,\n    onBack: () -> Unit,\n    multiPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,\n    selectedQueueVideos: androidx.compose.runtime.snapshots.SnapshotStateList<java.io.File>,\n    onPlayFile: (java.io.File) -> Unit\n) {\n    val context = LocalContext.current\n    val isDark = androidx.compose.foundation.isSystemInDarkTheme()\n    val textColor = if (isDark) Color.White else Color.Black"
)

# For ArchivedChannelsSubPage
content = content.replace(
    "fun ArchivedChannelsSubPage(viewModel: StreamViewModel, onBack: () -> Unit) {\n    val context = LocalContext.current",
    "fun ArchivedChannelsSubPage(viewModel: StreamViewModel, onBack: () -> Unit) {\n    val context = LocalContext.current\n    val isDark = androidx.compose.foundation.isSystemInDarkTheme()\n    val textColor = if (isDark) Color.White else Color.Black"
)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
