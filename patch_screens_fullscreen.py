import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

target = '''            com.example.ui.components.CinemetaWebViewPlayer(
                imdbId = item.imdbId ?: item.id,
                title = item.title,
                type = item.type,
                nativeStreamUrl = activeMediaStreamUrl,
                season = activeMediaSeason,
                episode = activeMediaEpisode,
                allMediaItems = allMedia,
                onSelectMedia = { selectedItem ->
                    viewModel.playMediaItem(selectedItem)
                },
                onClosePlayer = {
                    viewModel.clearActivePlayer()
                    onBackPress()
                },
                onFullScreenChange = { isFull ->
                    viewModel.setFullScreen(isFull)
                },
                modifier = Modifier.fillMaxSize(),
                isInPipMode = isInPipMode,
                onPlayingStateChanged = { playing ->
                    viewModel.setMediaPlaying(playing)
                }
            )'''

replacement = '''            com.example.ui.components.CinemetaWebViewPlayer(
                imdbId = item.imdbId ?: item.id,
                title = item.title,
                type = item.type,
                nativeStreamUrl = activeMediaStreamUrl,
                season = activeMediaSeason,
                episode = activeMediaEpisode,
                allMediaItems = allMedia,
                onSelectMedia = { selectedItem ->
                    viewModel.playMediaItem(selectedItem)
                },
                onClosePlayer = {
                    viewModel.clearActivePlayer()
                    onBackPress()
                },
                onFullScreenChange = { isFull ->
                    viewModel.setFullScreen(isFull)
                },
                modifier = Modifier.fillMaxSize(),
                isInPipMode = isInPipMode,
                isFullScreen = isFullScreen,
                onPlayingStateChanged = { playing ->
                    viewModel.setMediaPlaying(playing)
                }
            )'''

if target in content:
    content = content.replace(target, replacement)
    print("Patched Screens.kt successfully.")
else:
    print("Target not found in Screens.kt")

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
