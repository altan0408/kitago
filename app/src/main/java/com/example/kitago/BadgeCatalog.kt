package com.example.kitago

data class BadgeInfo(
    val key: String,
    val displayName: String,
    val description: String,
    val iconRes: Int
)

object BadgeCatalog {
    val allBadges = listOf(
        // Easy badges
        BadgeInfo("FIRST_DEPOSIT", "FIRST DEPOSIT", "Make your first vault deposit", android.R.drawable.btn_star_big_on),
        BadgeInfo("FIRST_QUEST", "FIRST QUEST", "Complete your first quest", android.R.drawable.btn_star_big_on),
        BadgeInfo("NOVICE_SAVER", "NOVICE SAVER", "Reach Level 5", android.R.drawable.btn_star_big_on),

        // Medium badges
        BadgeInfo("SKILLED_SAVER", "SKILLED SAVER", "Reach Level 10", android.R.drawable.btn_star_big_on),
        BadgeInfo("HOT_STREAK", "HOT STREAK", "Reach a 3-day contribution streak", android.R.drawable.btn_star_big_on),
        BadgeInfo("GOLD_HOARDER", "GOLD HOARDER", "Save ₱1,000 total across all quests", android.R.drawable.btn_star_big_on),
        BadgeInfo("FIVE_QUESTS", "QUEST HUNTER", "Complete 5 quests", android.R.drawable.btn_star_big_on),

        // Hard badges
        BadgeInfo("QUEST_MASTER", "QUEST MASTER", "Reach Level 20", android.R.drawable.btn_star_big_on),
        BadgeInfo("DRAGON_HOARDER", "DRAGON HOARDER", "Save ₱5,000 total across all quests", android.R.drawable.btn_star_big_on),
        BadgeInfo("LEGENDARY", "LEGENDARY", "Reach Level 40", android.R.drawable.btn_star_big_on),
        BadgeInfo("TEN_QUESTS", "VETERAN", "Complete 10 quests", android.R.drawable.btn_star_big_on),
        BadgeInfo("WEEK_STREAK", "IRON WILL", "Reach a 7-day contribution streak", android.R.drawable.btn_star_big_on),
    )

    fun getBadge(key: String): BadgeInfo? = allBadges.find { it.key == key }
}

