package com.example.kitago

data class Goal(
    val id: String = "",
    val name: String = "",
    val targetGold: Double = 0.0,
    val savedGold: Double = 0.0,
    val deadline: String = "",
    val isCollaborative: Boolean = false,
    val creatorId: String = "",
    val creatorName: String = "",
    val collaboratorId: String = "",
    val collaboratorName: String = "",
    val collabStatus: String = "ACCEPTED", // PENDING, ACCEPTED
    val status: String = "ACTIVE" // ACTIVE, COMPLETED
)
