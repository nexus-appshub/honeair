package com.example.ui.viewmodel

import com.example.data.network.IptvParser
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    try {
        val raw1 = IptvParser.fetchRawContent("https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/refs/heads/main/movies.m3u8")
        println("Fetched ${raw1.length} characters")
        val channels = IptvParser.parseChannels(raw1)
        println("Parsed ${channels.size} channels")
    } catch(e: Exception) {
        e.printStackTrace()
    }
}
