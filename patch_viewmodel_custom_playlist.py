import re

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'r') as f:
    content = f.read()

add_custom_playlist = """    fun addCustomPlaylist(name: String, rawContent: String, source: String = "file", pathOrUrl: String = "") {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val filename = "custom_playlist_${java.util.UUID.randomUUID()}.m3u"
            val file = java.io.File(getApplication<android.app.Application>().filesDir, filename)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                file.writeText(rawContent)
            }
            val contentToStore = "file://${file.absolutePath}"
            db.customPlaylistDao().insertCustomPlaylist(
                com.example.data.database.CustomPlaylistEntity(
                    name = name,
                    source = source,
                    pathOrUrl = pathOrUrl,
                    rawContent = contentToStore
                )
            )
            loadPlaylists(forceRefresh = true)
        }
    }"""

content = re.sub(r'    fun addCustomPlaylist\(name: String, rawContent: String, source: String = "file", pathOrUrl: String = ""\) \{.*?        \}\n    \}', add_custom_playlist, content, flags=re.DOTALL)

delete_custom_playlist = """    fun deleteCustomPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val all = db.customPlaylistDao().getAllCustomPlaylists()
            val match = all.find { it.id == playlistId }
            if (match != null) {
                if (match.rawContent.startsWith("file://")) {
                    val file = java.io.File(match.rawContent.substring(7))
                    if (file.exists()) {
                        file.delete()
                    }
                }
                db.customPlaylistDao().deleteCustomPlaylist(match)
            }
            val currentPlaylist = _selectedPlaylist.value
            if (currentPlaylist != null && currentPlaylist.url == "custom://$playlistId") {
                clearSelectedPlaylist()
            }
            loadPlaylists(forceRefresh = true)
        }
    }"""

content = re.sub(r'    fun deleteCustomPlaylist\(playlistId: Long\) \{.*?        \}\n    \}', delete_custom_playlist, content, flags=re.DOTALL)

parse_channels = """                    val playlist = db.customPlaylistDao().getAllCustomPlaylists().find { it.id == id }
                    if (playlist != null) {
                        val content = if (playlist.rawContent.startsWith("file://")) {
                            val file = java.io.File(playlist.rawContent.substring(7))
                            if (file.exists()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { file.readText() }
                            } else ""
                        } else {
                            playlist.rawContent
                        }
                        com.example.data.network.IptvParser.parseChannels(content)
                    } else {
                        emptyList()
                    }"""

content = re.sub(r'                    val playlist = db\.customPlaylistDao\(\)\.getAllCustomPlaylists\(\)\.find \{ it\.id == id \}\n                    if \(playlist != null\) \{\n                        com\.example\.data\.network\.IptvParser\.parseChannels\(playlist\.rawContent\)\n                    \} else \{\n                        emptyList\(\)\n                    \}', parse_channels, content)

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'w') as f:
    f.write(content)

