package com.example.kitago

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@Keep
@IgnoreExtraProperties
data class Contribution(
    val userId: String = "",
    val userName: String = "",
    val amount: Double = 0.0,
    val timestamp: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class Goal(
    val id: String = "",
    val name: String = "",
    val targetGold: Double = 0.0,
    val savedGold: Double = 0.0,
    val deadline: String = "",
    
    @get:PropertyName("isCollaborative")
    @set:PropertyName("isCollaborative")
    @field:PropertyName("isCollaborative")
    var isCollaborative: Boolean = false,
    
    val creatorId: String = "",
    val creatorName: String = "",
    
    // Map of UID to Status (PENDING, ACCEPTED, DECLINED)
    val collaboratorStatuses: Map<String, String> = emptyMap(),
    
    // Map of UID to Name for display
    val collaboratorNames: Map<String, String> = emptyMap(),
    
    val status: String = "ACTIVE", // ACTIVE, COMPLETED
    val streak: Int = 0,
    val lastContributionDate: String = "",
    
    // Collab streak: tracks when ALL accepted collaborators contribute daily
    val collabStreak: Int = 0,
    val collabLastFullDate: String = "",
    // Map of date -> Map of uid -> true (who contributed each day)
    val collabDailyContributors: Map<String, Map<String, Boolean>> = emptyMap(),

    // Map of contributionId to Contribution object
    val contributionHistory: Map<String, Contribution> = emptyMap()
)
