sed -i '/private val streamCache/a \
    private val httpClient = okhttp3.OkHttpClient.Builder().connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS).readTimeout(3, java.util.concurrent.TimeUnit.SECONDS).build()\
\
    private suspend fun verifyStreamAlive(url: String, headers: Map<String, String>): Boolean = withContext(Dispatchers.IO) {\
        if (!url.startsWith("http")) return@withContext false\
        try {\
            val reqBuilder = Request.Builder().url(url)\
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }\
            val req = reqBuilder.build()\
            val resp = httpClient.newCall(req).execute()\
            val code = resp.code\
            if (code != 200 && code != 206) return@withContext false\
            val bodyStr = resp.body?.string() ?: ""\
            if (url.contains(".m3u8", ignoreCase = true)) {\
                if (!bodyStr.contains("#EXTM3U")) return@withContext false\
                if (bodyStr.contains("404") || bodyStr.contains("Video not found") || bodyStr.contains("error")) return@withContext false\
            }\
            return@withContext true\
        } catch (e: Exception) {\
            return@withContext false\
        }\
    }' app/src/main/java/com/example/scraper/UnifiedStreamManager.kt
