package com.example.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NativeAnimeScraperTest {

    @Test
    fun extractsNumericEpisodeIdFromPlayerContainer() {
        val html = """
            <html>
              <body>
                <div id="video-player-container" data-id="61271"></div>
              </body>
            </html>
        """.trimIndent()

        assertEquals(
            "61271",
            NativeAnimeScraper.extractEpisodeIdFromHtml(html)
        )
    }

    @Test
    fun prefersVidstreamThenHd() {
        val servers = listOf(
            MapperServer("Kiwi Sub", "kiwi-id", "kiwi-token"),
            MapperServer("hd-1", "hd-id", "hd-token"),
            MapperServer("vidstream-2", "vid-id", "vid-token")
        )

        val chosen = NativeAnimeScraper.chooseMapperServer(servers)

        assertNotNull(chosen)
        assertEquals("vidstream-2", chosen?.server)
        assertEquals("vid-id", chosen?.id)
    }

    @Test
    fun fallsBackToHdWhenVidstreamIsMissing() {
        val servers = listOf(
            MapperServer("other", "other-id", "other-token"),
            MapperServer("HD-1", "hd-id", "hd-token")
        )

        val chosen = NativeAnimeScraper.chooseMapperServer(servers)

        assertEquals("HD-1", chosen?.server)
    }

    @Test
    fun parsesStructuredTrustWatchJson() {
        val json = """
            {
              "status": 200,
              "result": {
                "sources": [
                  {
                    "file": "https://cdn.example.test/master.m3u8?token=abc",
                    "type": "hls"
                  }
                ],
                "tracks": [
                  {
                    "file": "https://cdn.example.test/en.vtt",
                    "label": "English",
                    "kind": "captions"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = NativeAnimeScraper.parseTrustWatchResponse(json)

        assertNotNull(response)
        assertEquals(200, response?.status)
        assertEquals(
            "https://cdn.example.test/master.m3u8?token=abc",
            response?.result?.sources?.firstOrNull()?.file
        )
        assertEquals(
            "English",
            response?.result?.tracks?.firstOrNull()?.label
        )
    }

    @Test
    fun rejectsInvalidTrustWatchJson() {
        assertNull(
            NativeAnimeScraper.parseTrustWatchResponse("<html>challenge</html>")
        )
    }
}
