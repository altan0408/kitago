package com.example.kitago

import android.content.Context
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

    fun handleContribution(amount: Double, goalId: String, goal: Goal, onComplete: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userName = FirebaseAuth.getInstance().currentUser?.displayName ?: "User"
        val db = FirebaseDatabase.getInstance().reference

        val transactionId = db.push().key ?: "c_${System.currentTimeMillis()}"
        val contribution = Contribution(uid, userName, amount, System.currentTimeMillis())

        val updates = hashMapOf<String, Any?>()
        val newSavedTotal = goal.savedGold + amount
        updates["goals/$goalId/savedGold"] = newSavedTotal
        updates["goals/$goalId/contributionHistory/$transactionId"] = contribution
        
        if (newSavedTotal >= goal.targetGold && goal.status != "COMPLETED") {
            updates["goals/$goalId/status"] = "COMPLETED"
        }

        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val balance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                if (balance < amount) return Transaction.abort()

                // 1. Deduct Balance
                currentData.child("balance").value = balance - amount
                
                // 2. Update Total Saved for Leaderboards
                val totalSavedEver = currentData.child("totalSavedGold").getValue(Double::class.java) ?: 0.0
                currentData.child("totalSavedGold").value = totalSavedEver + amount

                // 3. XP and Stats
                if (newSavedTotal >= goal.targetGold && goal.status != "COMPLETED") {
                    addXp(currentData, XP_GOAL_COMPLETED)
                    val wins = currentData.child("wins").getValue(Int::class.java) ?: 0
                    currentData.child("wins").value = wins + 1
                    awardBadge(currentData, "goal_$goalId", "Mastered: ${goal.name}")
                }
                
                handleStreakAndXp(currentData, goalId)
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    db.updateChildren(updates).addOnCompleteListener { onComplete(it.isSuccessful) }
                } else {
                    onComplete(false)
                }
            }
        })
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
        
        // Global highest streak
        val globalHighest = userNode.child("streak").getValue(Int::class.java) ?: 0
        if (streak > globalHighest) userNode.child("streak").value = streak

        val bonus = if (streak >= 3) XP_STREAK_BONUS else 0
        addXp(userNode, XP_PER_CONTRIBUTION + bonus)
        
        if (streak == 7) awardBadge(userNode, "week_streak_$goalId", "Unstoppable Week!")
    }

    fun addXp(userData: MutableData, amount: Int) {
        var xp = userData.child("xp").getValue(Int::class.java) ?: 0
        var level = userData.child("level").getValue(Int::class.java) ?: 1
        
        if (level >= MAX_LEVEL) return

        xp += amount
        while (xp >= getXpNeededForLevel(level) && level < MAX_LEVEL) {
            xp -= getXpNeededForLevel(level)
            level++
            awardBadge(userData, "lvl_$level", "Reached Level $level!")
        }
        userData.child("xp").value = xp
        userData.child("level").value = level
    }

    fun awardBadge(userData: MutableData, id: String, desc: String) {
        userData.child("badges").child(id).value = desc
    }
}
