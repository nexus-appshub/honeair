package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class SportsEvent(
    @DocumentId
    var id: String = "",
    
    var title: String = "",
    
    @get:PropertyName("sportCategory") @set:PropertyName("sportCategory")
    var sportCategory: String = "Cricket",
    
    var tournament: String = "",
    var status: String = "live",
    var startTime: String = "",
    var badgeText: String = "",
    var bannerUrl: String = "",
    var description: String = "",
    
    @get:PropertyName("isPinned") @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,
    
    var viewersCount: Any? = 0,
    var teamA: TeamInfo = TeamInfo(),
    var teamB: TeamInfo = TeamInfo(),
    var servers: List<SportsStreamServer> = emptyList()
) {
    val isLive: Boolean
        get() = status.equals("live", ignoreCase = true) || 
                status.equals("live now", ignoreCase = true) ||
                badgeText.contains("live", ignoreCase = true)
                
    val displayTime: String
        get() = startTime.ifEmpty { "Live Broadcast" }
        
    val displayTournament: String
        get() = tournament.ifEmpty { sportCategory.uppercase() }
}

@IgnoreExtraProperties
data class TeamInfo(
    var name: String = "",
    var logo: String = "",
    var score: String = ""
)

@IgnoreExtraProperties
data class SportsStreamServer(
    var name: String = "",
    var url: String = "",
    var referer: String? = null,
    var origin: String? = null
)
