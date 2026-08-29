import re

with open('app/src/main/java/com/example/ui/components/ExoPlayerView.kt', 'r') as f:
    content = f.read()

target = """        val httpDataSourceFactory = SmartNetworkBoosterEngine.createBoostedHttpDataSourceFactory()
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)"""

replacement = """        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/components/ExoPlayerView.kt', 'w') as f:
    f.write(content)
