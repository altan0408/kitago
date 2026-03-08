package com.example.kitago

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

object DataManager {
    private const val MAX_LEVEL = 50
    private const val XP_PER_DEPOSIT = 50
    private const val XP_PER_CONTRIBUTION = 100
    private const val XP_STREAK_BONUS = 250
    private const val XP_GOAL_COMPLETED = 1500
    private const val XP_COLLAB_STREAK_BONUS = 300

    fun getXpNeededForLevel(level: Int): Int = level * 750

    fun syncUpdateBalance(amount: Double, isIncome: Boolean, onComplete: (Boolean) -> Unit = {}) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val balance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                currentData.child("balance").value = if (isIncome) balance + amount else balance - amount

                if (isIncome) {
                    val incomeRef = currentData.child("income_totals").child("VAULT_DEPOSIT")
                    val currentTotal = incomeRef.getValue(Double::class.java) ?: 0.0
                    incomeRef.value = currentTotal + amount
                    addXp(currentData, XP_PER_DEPOSIT)
                }
                return Transaction.success(currentData)
            }
            override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) { onComplete(c) }
        })
    }

<<<<<<< Updated upstream
    @Suppress("UNUSED_PARAMETER")
    fun syncAddTransaction(amount: Double, category: String, note: String, isIncome: Boolean, onComplete: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)
=======
    fun syncAddTransaction(amount: Double, category: String, note: String, isIncome: Boolean, onComplete: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val balance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                currentData.child("balance").value = if (isIncome) balance + amount else balance - amount

                val path = if (isIncome) "income_totals" else "expense_totals"
                val catRef = currentData.child(path).child(category)
                catRef.value = (catRef.getValue(Double::class.java) ?: 0.0) + amount

                if (isIncome) addXp(currentData, XP_PER_DEPOSIT)
                return Transaction.success(currentData)
            }
            override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) { onComplete(c) }
        })
    }

    fun handleContribution(amount: Double, goalId: String, goal: Goal, onComplete: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userName = FirebaseAuth.getInstance().currentUser?.displayName ?: "User"
        val db = FirebaseDatabase.getInstance().reference
>>>>>>> Stashed changes

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val balance = currentData.child("balance").getValue(Double::class.java) ?: 0.0

                // Prevent expenses exceeding available balance
                if (!isIncome && balance < amount) {
                    return Transaction.abort()
                }

                currentData.child("balance").value = if (isIncome) balance + amount else balance - amount

                val totalNode = if (isIncome) "income_totals" else "expense_totals"
                val catRef = currentData.child(totalNode).child(category)
                val currentTotal = catRef.getValue(Double::class.java) ?: 0.0
                catRef.value = currentTotal + amount

                addXp(currentData, if (isIncome) XP_PER_DEPOSIT else 20)
                return Transaction.success(currentData)
            }
            override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {
                onComplete(c)
            }
        })
    }

    fun handleContribution(amount: Double, goalId: String, goal: Goal, onComplete: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference

        // Fetch the user's display name from DB for accuracy
        db.child("users").child(uid).child("username").get().addOnSuccessListener { nameSnap ->
            val userName = nameSnap.getValue(String::class.java) ?: "Adventurer"

            val transactionId = db.push().key ?: "c_${System.currentTimeMillis()}"
            val contribution = Contribution(uid, userName, amount, System.currentTimeMillis())

            val updates = hashMapOf<String, Any?>()
            val newSavedTotal = goal.savedGold + amount
            updates["goals/$goalId/savedGold"] = newSavedTotal
            updates["goals/$goalId/contributionHistory/$transactionId"] = contribution

            if (newSavedTotal >= goal.targetGold && goal.status != "COMPLETED") {
                updates["goals/$goalId/status"] = "COMPLETED"
            }

            // Track collab daily contributor
            if (goal.isCollaborative) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = sdf.format(Date())
                updates["goals/$goalId/collabDailyContributors/$today/$uid"] = true
            }

            db.child("users").child(uid).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val balance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                    if (balance < amount) return Transaction.abort()

                    currentData.child("balance").value = balance - amount

                    val totalSavedEver = currentData.child("totalSavedGold").getValue(Double::class.java) ?: 0.0
                    currentData.child("totalSavedGold").value = totalSavedEver + amount

                    if (newSavedTotal >= goal.targetGold && goal.status != "COMPLETED") {
                        addXp(currentData, XP_GOAL_COMPLETED)
                        val wins = currentData.child("wins").getValue(Int::class.java) ?: 0
                        currentData.child("wins").value = wins + 1
                    }

                    handleStreakAndXp(currentData, goalId)
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (committed) {
                        db.updateChildren(updates).addOnCompleteListener { task ->
                            if (task.isSuccessful && goal.isCollaborative) {
                                updateCollabStreak(goalId, goal)
                            }
                            onComplete(task.isSuccessful)
                        }
                    } else {
                        onComplete(false)
                    }
                }
            })
        }
    }

    /**
     * Updates the collaborative streak for a goal.
     * Checks if ALL accepted collaborators have contributed today.
     * If yes, increments the collab streak (or starts it at 1 if broken).
     */
    private fun updateCollabStreak(goalId: String, goal: Goal) {
        val db = FirebaseDatabase.getInstance().reference
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())

        val acceptedCollaborators = goal.collaboratorStatuses.filter { it.value == "ACCEPTED" }.keys

        db.child("goals").child(goalId).child("collabDailyContributors").child(today)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val todayContributors = snapshot.children.mapNotNull { it.key }.toSet()
                    val allContributed = acceptedCollaborators.all { it in todayContributors }

                    if (allContributed && acceptedCollaborators.size >= 2) {
                        // Check if yesterday also had a full day
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DATE, -1)
                        val yesterday = sdf.format(cal.time)

                        db.child("goals").child(goalId).get().addOnSuccessListener { goalSnap ->
                            val lastFullDate = goalSnap.child("collabLastFullDate").getValue(String::class.java) ?: ""
                            val currentCollabStreak = goalSnap.child("collabStreak").getValue(Int::class.java) ?: 0

                            // Already counted today
                            if (lastFullDate == today) return@addOnSuccessListener

                            val newStreak = if (lastFullDate == yesterday) currentCollabStreak + 1 else 1

                            val streakUpdates = hashMapOf<String, Any?>(
                                "goals/$goalId/collabStreak" to newStreak,
                                "goals/$goalId/collabLastFullDate" to today
                            )
                            db.updateChildren(streakUpdates)

                            // Award collab streak bonus XP to all accepted collaborators
                            if (newStreak >= 3) {
                                for (uid in acceptedCollaborators) {
                                    db.child("users").child(uid).runTransaction(object : Transaction.Handler {
                                        override fun doTransaction(data: MutableData): Transaction.Result {
                                            addXp(data, XP_COLLAB_STREAK_BONUS)
                                            return Transaction.success(data)
                                        }
                                        override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {}
                                    })
                                }
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Unified method to respond to a challenge/quest invite.
     * Handles ACCEPTED, DECLINED, and CANCELLED statuses.
     * CANCELLED removes the goal entirely for all collaborators.
     */
    fun respondToChallenge(goalId: String, goal: Goal?, status: String, onComplete: (Boolean) -> Unit = {}) {
        val myId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference
        val updates = hashMapOf<String, Any?>()

        when (status) {
            "ACCEPTED" -> {
                updates["goals/$goalId/collaboratorStatuses/$myId"] = "ACCEPTED"
                updates["users/$myId/goals/$goalId"] = "ACCEPTED"
                updates["challenge_requests/$myId/$goalId"] = null
            }
            "DECLINED" -> {
                updates["goals/$goalId/collaboratorStatuses/$myId"] = "DECLINED"
                updates["users/$myId/goals/$goalId"] = null
                updates["challenge_requests/$myId/$goalId"] = null
            }
            "CANCELLED" -> {
                updates["goals/$goalId"] = null
                goal?.collaboratorStatuses?.keys?.forEach { uid ->
                    updates["users/$uid/goals/$goalId"] = null
                    updates["challenge_requests/$uid/$goalId"] = null
                }
                // Also remove for the current user if not already in collaborators
                updates["users/$myId/goals/$goalId"] = null
            }
        }

        db.updateChildren(updates).addOnSuccessListener {
            onComplete(true)
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    /**
     * Sends a challenge request notification to a collaborator.
     */
    fun sendChallengeRequest(goalId: String, goalName: String, targetGold: Double, creatorName: String, collaboratorId: String) {
        val db = FirebaseDatabase.getInstance().reference
        val requestData = hashMapOf<String, Any>(
            "goalId" to goalId,
            "goalName" to goalName,
            "targetGold" to targetGold,
            "creatorName" to creatorName,
            "timestamp" to ServerValue.TIMESTAMP
        )
        db.child("challenge_requests").child(collaboratorId).child(goalId).setValue(requestData)
    }


    private fun handleStreakAndXp(userNode: MutableData, goalId: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())

        val userStreaks = userNode.child("goal_streaks").child(goalId)
        val contributionDates = userStreaks.child("dates")

        if (contributionDates.child(today).value != null) {
            addXp(userNode, XP_PER_CONTRIBUTION)
            return
        }

        contributionDates.child(today).value = true

        var streak = 1
        val cal = Calendar.getInstance()
        while (true) {
            cal.add(Calendar.DATE, -1)
            val dateStr = sdf.format(cal.time)
            if (contributionDates.child(dateStr).value != null) {
                streak++
            } else {
                break
            }
        }

        userStreaks.child("current").value = streak

        val globalHighest = userNode.child("streak").getValue(Int::class.java) ?: 0
        if (streak > globalHighest) userNode.child("streak").value = streak

        val bonus = if (streak >= 3) XP_STREAK_BONUS else 0
        addXp(userNode, XP_PER_CONTRIBUTION + bonus)
    }

    fun addXp(userData: MutableData, amount: Int) {
        var xp = userData.child("xp").getValue(Int::class.java) ?: 0
        var level = userData.child("level").getValue(Int::class.java) ?: 1

        if (level >= MAX_LEVEL) return

        xp += amount
        while (xp >= getXpNeededForLevel(level) && level < MAX_LEVEL) {
            xp -= getXpNeededForLevel(level)
            level++
        }
        userData.child("xp").value = xp
        userData.child("level").value = level

        // Check and award badges
        checkBadges(userData, level)
    }

    /**
     * Badge definitions and automatic awarding logic.
     * Badges are awarded based on level, wins, streak, and totalSavedGold.
     */
    private fun checkBadges(userData: MutableData, level: Int) {
        val wins = userData.child("wins").getValue(Int::class.java) ?: 0
        val streak = userData.child("streak").getValue(Int::class.java) ?: 0
        val totalSaved = userData.child("totalSavedGold").getValue(Double::class.java) ?: 0.0
        val badges = userData.child("badges")

        // Level-based badges
        if (level >= 5 && badges.child("NOVICE_SAVER").value == null)
            badges.child("NOVICE_SAVER").value = "NOVICE SAVER"
        if (level >= 10 && badges.child("SKILLED_SAVER").value == null)
            badges.child("SKILLED_SAVER").value = "SKILLED SAVER"
        if (level >= 25 && badges.child("MASTER_SAVER").value == null)
            badges.child("MASTER_SAVER").value = "MASTER SAVER"
        if (level >= 50 && badges.child("LEGENDARY").value == null)
            badges.child("LEGENDARY").value = "LEGENDARY"

        // Win-based badges
        if (wins >= 1 && badges.child("FIRST_QUEST").value == null)
            badges.child("FIRST_QUEST").value = "FIRST QUEST"
        if (wins >= 5 && badges.child("QUEST_HUNTER").value == null)
            badges.child("QUEST_HUNTER").value = "QUEST HUNTER"
        if (wins >= 10 && badges.child("QUEST_MASTER").value == null)
            badges.child("QUEST_MASTER").value = "QUEST MASTER"

        // Streak-based badges
        if (streak >= 3 && badges.child("HOT_STREAK").value == null)
            badges.child("HOT_STREAK").value = "HOT STREAK"
        if (streak >= 7 && badges.child("WEEKLY_WARRIOR").value == null)
            badges.child("WEEKLY_WARRIOR").value = "WEEKLY WARRIOR"
        if (streak >= 30 && badges.child("MONTHLY_LEGEND").value == null)
            badges.child("MONTHLY_LEGEND").value = "MONTHLY LEGEND"

        // Savings-based badges
        if (totalSaved >= 1000 && badges.child("GOLD_HOARDER").value == null)
            badges.child("GOLD_HOARDER").value = "GOLD HOARDER"
        if (totalSaved >= 5000 && badges.child("TREASURE_HUNTER").value == null)
            badges.child("TREASURE_HUNTER").value = "TREASURE HUNTER"
        if (totalSaved >= 10000 && badges.child("DRAGON_VAULT").value == null)
            badges.child("DRAGON_VAULT").value = "DRAGON VAULT"
    }
}
