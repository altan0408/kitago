package com.example.kitago

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

object DataManager {
    private var maxLevel = 50
    private const val XP_PER_DEPOSIT = 50
    private const val XP_PER_CONTRIBUTION = 100
    private const val XP_STREAK_BONUS = 250
    private const val XP_GOAL_COMPLETED = 1500
    private const val XP_COLLAB_STREAK_BONUS = 300

    fun getXpNeededForLevel(level: Int): Int = level * 750

    fun getLevelTitle(level: Int): String {
        return when {
            level >= 40 -> "LEGENDARY TREASURER"
            level >= 30 -> "DRAGON HOARDER"
            level >= 20 -> "QUEST MASTER"
            level >= 10 -> "ELITE ADVENTURER"
            level >= 5 -> "SKILLED SAVER"
            else -> "NOVICE"
        }
    }

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

    fun syncAddTransaction(amount: Double, category: String, note: String, isIncome: Boolean, onComplete: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val balance = currentData.child("balance").getValue(Double::class.java) ?: 0.0

                if (!isIncome && balance < amount) return Transaction.abort()

                currentData.child("balance").value = if (isIncome) balance + amount else balance - amount

                val path = if (isIncome) "income_totals" else "expense_totals"
                val catRef = currentData.child(path).child(category)
                catRef.value = (catRef.getValue(Double::class.java) ?: 0.0) + amount

                addXp(currentData, if (isIncome) XP_PER_DEPOSIT else 20)
                return Transaction.success(currentData)
            }
            override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) { onComplete(c) }
        })
    }

    fun handleContribution(amount: Double, goalId: String, goal: Goal, onComplete: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference

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
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DATE, -1)
                        val yesterday = sdf.format(cal.time)

                        db.child("goals").child(goalId).get().addOnSuccessListener { goalSnap ->
                            val lastFullDate = goalSnap.child("collabLastFullDate").getValue(String::class.java) ?: ""
                            val currentCollabStreak = goalSnap.child("collabStreak").getValue(Int::class.java) ?: 0

                            if (lastFullDate == today) return@addOnSuccessListener

                            val newStreak = if (lastFullDate == yesterday) currentCollabStreak + 1 else 1
                            val streakUpdates = hashMapOf<String, Any?>(
                                "goals/$goalId/collabStreak" to newStreak,
                                "goals/$goalId/collabLastFullDate" to today
                            )
                            db.updateChildren(streakUpdates)

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
                updates["users/$myId/goals/$goalId"] = null
            }
        }

        db.updateChildren(updates).addOnSuccessListener { onComplete(true) }.addOnFailureListener { onComplete(false) }
    }

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
            if (contributionDates.child(sdf.format(cal.time)).value != null) streak++ else break
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

        if (level >= maxLevel) return

        xp += amount
        while (xp >= getXpNeededForLevel(level) && level < maxLevel) {
            xp -= getXpNeededForLevel(level)
            level++
        }
        userData.child("xp").value = xp
        userData.child("level").value = level
        checkBadges(userData, level)
    }

    private fun checkBadges(userData: MutableData, level: Int) {
        val wins = userData.child("wins").getValue(Int::class.java) ?: 0
        val streak = userData.child("streak").getValue(Int::class.java) ?: 0
        val totalSaved = userData.child("totalSavedGold").getValue(Double::class.java) ?: 0.0
        val badges = userData.child("badges")

        if (level >= 5 && badges.child("NOVICE_SAVER").value == null) badges.child("NOVICE_SAVER").value = "NOVICE SAVER"
        if (level >= 10 && badges.child("SKILLED_SAVER").value == null) badges.child("SKILLED_SAVER").value = "SKILLED SAVER"
        if (wins >= 1 && badges.child("FIRST_QUEST").value == null) badges.child("FIRST_QUEST").value = "FIRST QUEST"
        if (streak >= 3 && badges.child("HOT_STREAK").value == null) badges.child("HOT_STREAK").value = "HOT STREAK"
        if (totalSaved >= 1000 && badges.child("GOLD_HOARDER").value == null) badges.child("GOLD_HOARDER").value = "GOLD HOARDER"
    }

    fun fetchGlobalConfig() {
        FirebaseDatabase.getInstance().reference.child("game_config").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                maxLevel = snapshot.child("max_level").getValue(Int::class.java) ?: 50
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
