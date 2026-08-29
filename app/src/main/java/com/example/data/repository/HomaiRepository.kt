package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.IptvChannel
import com.example.data.model.MediaItem
import com.example.data.network.GeminiClient
import com.example.data.network.GeminiContent
import com.example.data.network.GeminiPart
import com.example.data.network.GeminiRequest
import com.example.data.network.GeminiSystemInstruction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HomaiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val suggestedChannels: List<IptvChannel> = emptyList(),
    val suggestedMediaItems: List<MediaItem> = emptyList()
)

class HomaiRepository {

    companion object {
        val learnedInterests = java.util.concurrent.CopyOnWriteArrayList<String>()

        fun recordInterest(query: String) {
            val q = query.trim()
            if (q.isNotBlank() && q.length >= 3) {
                // Keep only unique elements in the learned history
                if (!learnedInterests.contains(q)) {
                    if (learnedInterests.size >= 15) {
                        learnedInterests.removeAt(0)
                    }
                    learnedInterests.add(q)
                }
            }
        }
    }

    private val apiService = GeminiClient.apiService

    private val systemInstructionText = """
        Your name is Homai. You are the official AI Assistant & Smart Guide for HomeAir TV app.
        
        CRITICAL GREETING RULE (MANDATORY):
        - NEVER use the greeting "Nomoskar" (নমস্কার) under any language (Bengali, English, etc.)!
        - Instead of "Nomoskar" or "নমস্কার", use cool, friendly greetings like "Yoo!", "Hey!", "হেয়!", "হ্যালো!" or similar.
        
        DEFAULT LANGUAGE RULE:
        - Speak in polite, helpful, clear ENGLISH by default.
        - If the user sends a message in Bengali (বাংলা), Banglish, or any other language, respond in that exact language warmly!

        KEY RESPONSIBILITIES & TROUBLESHOOTING GUIDANCE:
        1. Step-by-Step Movie/Show Downloading Guidance:
           If the user asks for help with downloading or how to download movies/shows, explain this exact step-by-step procedure clearly:
           - Step 1: While watching a Movie or TV show episode, tap the "Download" or "Downloader" button (cloud-down icon) on the screen.
           - Step 2: This opens the "In-App Downloader Window" (Downloader Modal) offering several server options:
             * Option 1: videodownloader.site (Automated Web Search & Fill - wait for the page to load, choose quality, and download).
             * Option 2: 02moviedownloader.site (Automated Web Search & Fill).
             * Option 3: VidSrc Downloader (Direct scraping helper).
             * Option 4: Direct Captured Stream (Direct high-speed in-app captured downloading).
           - Step 3: Choose your option/quality, wait for processing, and monitor the download progress in the notification bar or downloads section!

        2. Creator & Developer Credits:
           If the user asks "Who made you?", "Who created you?", "Who developed Home Air TV?", or "banaice ke" in any language, answer warmly and proudly:
           "Xubilas Web Dev Corp - Xubilas Web Dev Corp is a full-stack digital development agency based in Bangladesh. Founded and led by XWDC CEO and Founder, the agency focuses on custom web development, interactive web applications, and educational management solutions."

        3. Terms, Usage & Privacy Policy Analysis:
           If a user asks about privacy, logs, data security, ads, terms, or usage rules, analyze and reply precisely using these official details:
           - Free & Open Access: Home Air TV is a 100% free open-source media browser with no paywalls, commercial fees, or credit cards required.
           - Content Aggregation: We operate purely as an index/browser and do NOT host or store any media on our servers. Contents are fetched from public internet sources.
           - Zero Personal Data Collection: We do not require emails, mobile numbers, or account creation.
           - No Tracking or Logs: We do not track your search history, watch logs, or real-time location.
           - 100% Ad-Free Architecture: Runs completely clean with zero popups, malicious ads, or tracking networks.
           - Official Safety: Only download/update the APK from the official site (xubilasappshub.xubilaswebdevcorp.shop) or our official Telegram channel (https://t.me/HomeAirTv). Avoid fake external APKs.

        4. Playback & Stream Troubleshooting:
           - If a channel or media stream does not play on the first attempt, advise the user:
             "Please close the video player and tap the play button again, or select an alternative server/link if available."
           - Guide users on how to load M3U playlists, save channels to favorites, and discover channels or live matches.

        RESPONSE STYLE:
        - Do NOT include raw formatting characters like ** surrounding bold text if plain formatting works better, or keep bullet points clean.
        - Be friendly, clear, and encouraging. Always sign off or introduce yourself as Homai, HomeAir TV Assistant (using Yoo! or Hey! instead of Nomoskar).
    """.trimIndent()

    suspend fun sendMessage(
        history: List<HomaiMessage>,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val modelsToTry = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash")

        // Record user prompt as a learned interest to enrich AI service quality dynamically
        recordInterest(userPrompt)

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            val contentsList = mutableListOf<GeminiContent>()

            // Add recent conversation history for multi-turn context (last 10 messages)
            val recentHistory = history.takeLast(10)
            for (msg in recentHistory) {
                val role = if (msg.isUser) "user" else "model"
                contentsList.add(
                    GeminiContent(
                        role = role,
                        parts = listOf(GeminiPart(text = msg.text))
                    )
                )
            }

            // Add current user prompt
            contentsList.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = userPrompt))
                )
            )

            // Construct DYNAMIC system instructions by embedding learned interests
            val dynamicSystemInstructionText = if (learnedInterests.isNotEmpty()) {
                "$systemInstructionText\n\n[LEARNED USER INTERESTS & RECENT SEARCHES]\nThe user has searched for or asked about the following items: ${learnedInterests.joinToString(", ")}. Use this context to personalize your responses, suggest matching films/natoks/channels, and enrich your explanations!"
            } else {
                systemInstructionText
            }

            val request = GeminiRequest(
                contents = contentsList,
                systemInstruction = GeminiSystemInstruction(
                    parts = listOf(GeminiPart(text = dynamicSystemInstructionText))
                )
            )

            for (model in modelsToTry) {
                try {
                    val response = apiService.generateContent(model = model, apiKey = apiKey, request = request)
                    val candidateText = response.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text

                    if (!candidateText.isNullOrBlank()) {
                        return@withContext Result.success(candidateText)
                    }
                } catch (e: Exception) {
                    // Try next model if any
                }
            }
        }

        // Smart offline fallback response when API fails or key is missing
        Result.success(generateSmartOfflineResponse(userPrompt))
    }

    private fun generateSmartOfflineResponse(userPrompt: String): String {
        val lower = userPrompt.lowercase().trim()
        val isBengali = lower.any { it in '\u0980'..'\u09ff' } || lower.contains("kemon") || lower.contains("ki khobor") || lower.contains("valo") || lower.contains("achi") || lower.contains("bolo") || lower.contains("shuru")

        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") || lower.contains("yoo") || lower.contains("kemon") || lower.contains("ki khobor") || lower.contains("কেমন") || lower.contains("হ্যালো") || lower.contains("হাই") || lower.contains("হেয়") -> {
                if (isBengali) {
                    "Yoo! আমি Homai, আপনার HomeAir TV স্মার্ট অ্যাসিস্ট্যান্ট! 😊\n\nআজকে আপনি কি দেখতে চান? লাইভ স্পোর্টস, নতুন বাংলা বা হিন্দি মুভি, নাকি ফেভারিট কোনো এনিমে সিরিজ?"
                } else {
                    "Yoo! I am Homai, your official HomeAir TV Assistant! 🍿\n\nHow can I help you today? Looking for live sports, latest cinema, anime episodes, or streaming tips?"
                }
            }
            lower.contains("naruto") || lower.contains("anime") || lower.contains("এনিমে") || lower.contains("জুজুৎসু") || lower.contains("shippuden") || lower.contains("attack on titan") || lower.contains("one piece") || lower.contains("death note") || lower.contains("jujutsu") -> {
                if (isBengali) {
                    "⚡ Anime Hub এ আপনাকে স্বাগতম!\n\nHomeAir TV তে Naruto, Naruto Shippuden, Attack on Titan, Jujutsu Kaisen, One Piece সহ শত শত জনপ্রিয় এনিমে SUB ও DUB সার্ভারে পাওয়া যাচ্ছে।\n\nযেকোনো সিজন ও পর্ব দেখতে সরাসরি Anime Hub এ গিয়ে সার্চ করুন অথবা প্লেয়ারের সার্ভার লিস্ট থেকে আপনার পছন্দের SUB / DUB সার্ভার বেছে নিন!"
                } else {
                    "⚡ Welcome to Anime Hub on HomeAir TV!\n\nWe support rich SUB and DUB playback for top anime including Naruto, Naruto Shippuden, Attack on Titan, Jujutsu Kaisen, One Piece, and many more.\n\nYou can easily switch between episodes and select your favorite SUB or DUB servers directly in the player!"
                }
            }
            lower.contains("download") || lower.contains("ডাউনলোড") || lower.contains("down") -> {
                if (isBengali) {
                    "🎬 মুভি ও সিরিজ ডাউনলোড করার সহজ নিয়ম:\n\n1. প্লেয়ারে ভিডিও দেখার সময় স্ক্রিনের 'Download' (ক্লাউড ডাউন) বাটনে চাপ দিন।\n2. ডাউনলোডার উইন্ডোতে আপনার পছন্দের সার্ভার বেছে নিন:\n   • videodownloader.site বা 02moviedownloader.site (অটোমেটেড সার্চ ও ডাউনলোড)\n   • VidSrc Downloader\n   • Direct Captured Stream (হাই স্পিড ডাউনলোড)\n3. কোয়ালিটি নির্বাচন করে ডাউনলোড শুরু করুন এবং নোটিফিকেশন বারে প্রগ্রেস দেখুন!"
                } else {
                    "🎬 How to Download Movies & Shows Step-by-Step:\n\n1. Tap the 'Download' / cloud-down button on the player screen.\n2. Choose your preferred server in the Downloader Window:\n   • videodownloader.site or 02moviedownloader.site (Automated Web Search & Fill).\n   • VidSrc Downloader (Direct scraper helper).\n   • Direct Captured Stream (Direct high-speed stream capture).\n3. Select quality and monitor download progress in your notification bar!"
                }
            }
            lower.contains("who made") || lower.contains("created") || lower.contains("developer") || lower.contains("creator") || lower.contains("founder") || lower.contains("banaice") || lower.contains("বানাইছে") || lower.contains("বানিয়েছে") || lower.contains("xubilas") -> {
                "🚀 Developer Credits:\n\nXubilas Web Dev Corp - Xubilas Web Dev Corp is a full-stack digital development agency based in Bangladesh. Founded and led by XWDC CEO and Founder, the agency focuses on custom web development, interactive web applications, and educational management solutions."
            }
            lower.contains("privacy") || lower.contains("terms") || lower.contains("policy") || lower.contains("প্রাইভেসি") || lower.contains("নিরাপত্তা") || lower.contains("শর্ত") || lower.contains("safe") -> {
                "🛡️ Privacy Policy & Security Overview:\n\n• 100% Free & Open Access: No paywalls, commercial subscriptions, or credit cards required.\n• Zero Data Collection: No emails, mobile numbers, or account registrations needed.\n• No Tracking: Zero logs for your watch history, search terms, or location.\n• Ad-Free Experience: Clean interface with absolutely no popup ads.\n• Official Source: Always download/update from homeairtv.xubilaswebdevcorp.shop or Telegram t.me/HomeAirTv."
            }
            lower.contains("sport") || lower.contains("cricket") || lower.contains("football") || lower.contains("match") || lower.contains("খেলা") || lower.contains("ipl") || lower.contains("bpl") || lower.contains("t sports") -> {
                if (isBengali) {
                    "🏏 লাইভ স্পোর্টস ও ক্রিকেট চ্যানেলসমূহ:\n\nHomeAir TV তে T Sports, Star Sports 1 HD, GTV, Sony Sports Ten সহ সব শীর্ষ স্পোর্টস চ্যানেল উপভোগ করতে পারেন!\n\nহোমপেজের 'Live TV' ট্যাব থেকে Sports ক্যাটাগরি সিলেক্ট করুন।"
                } else {
                    "🏏 Live Sports & Cricket Channels:\n\nWatch live cricket, football, and international sports on T Sports, Star Sports 1 HD, GTV, and Sony Sports Ten channels!\n\nBrowse the Live TV tab or search directly for your match."
                }
            }
            lower.contains("server") || lower.contains("buffer") || lower.contains("play") || lower.contains("video") || lower.contains("চলছে না") || lower.contains("হচ্ছে না") || lower.contains("problem") || lower.contains("issue") || lower.contains("error") || lower.contains("keno") -> {
                if (isBengali) {
                    "📺 প্লেব্যাক ও সার্ভার টিপস:\n\n1. কোনো ভিডিও বা চ্যানেল চালু না হলে প্লেয়ার বন্ধ করে আবার প্লে বাটনে চাপ দিন।\n2. প্লেয়ারের উপরে 'Servers' তালিকা থেকে ভিন্ন কোনো সার্ভার (যেমন VidSrc, VidNest, English, ইত্যাদিতে) সুইচ করুন।\n3. আপনার ইন্টারনেট স্পিড ও ওয়াইফাই কানেকশন চেক করুন।"
                } else {
                    "📺 Playback & Server Troubleshooting:\n\n1. If a stream doesn't play immediately, close the player and tap play again.\n2. Tap the 'Servers' dropdown at the top of the player to switch between available high-speed servers (VidSrc, VidNest, English, etc.).\n3. Ensure your internet connection is active."
                }
            }
            lower.contains("movie") || lower.contains("cinema") || lower.contains("drama") || lower.contains("series") || lower.contains("মুভি") || lower.contains("নাটক") || lower.contains("ছবি") -> {
                if (isBengali) {
                    "🍿 সিনেমা ও সিরিজ গাইড:\n\nHomeAir TV তে বাংলা সিনেমা, নাটক, হিন্দি সিনেমা, সাউথ ইন্ডিয়ান ডাবড এবং কে-ড্রামা এর বিশাল কালেকশন রয়েছে।\n\nসার্চ বারে যেকোনো ১টি অক্ষর বা পুরো নাম লিখে সার্চ করলেই আপনার কাঙ্ক্ষিত মুভি খুঁজে পাবেন!"
                } else {
                    "🍿 Movies & Series Guide:\n\nDiscover thousands of Bangla Movies & Natok, Bollywood Hindi Cinema, South Dubbed, K-Dramas, and Hollywood blockbusters!\n\nUse the search bar with any character length to find your favorite title."
                }
            }
            lower.contains("m3u") || lower.contains("playlist") || lower.contains("add") -> {
                "📡 Custom M3U Playlist:\n\nGo to Settings -> M3U Playlist Manager -> Paste your playlist URL and tap Save to load custom IPTV channels instantly."
            }
            else -> {
                if (isBengali) {
                    "Yoo! আমি Homai, আপনার HomeAir TV স্মার্ট গাইড।\n\nলাইভ টিভি চ্যানেল, খেলা, নতুন মুভি, এনিমে সিরিজ অথবা প্লেয়ার নিয়ে যেকোনো প্রশ্ন আমাকে নির্দ্বিধায় করতে পারেন!"
                } else {
                    "Yoo! I am Homai, your official HomeAir TV Assistant.\n\nI am here to help you find live TV channels, sports matches, movies, anime, and download or stream tips. Ask me anything!"
                }
            }
        }
    }
}
