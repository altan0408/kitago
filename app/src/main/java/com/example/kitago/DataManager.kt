package com.example.kitago

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

object DataManager {
    private const val MAX_LEVEL = 16
    private const val XP_PER_DEPOSIT = 50
    private const val XP_PER_CONTRIBUTION = 100
    private const val XP_STREAK_BONUS = 150
    private const val XP_GOAL_COMPLETED = 1000

    // XP needed increases: 500, 1000, 1500, 2000...
    fun getXpNeededForLevel(level: Int): Int = level * 500

    fun syncUpdateBalance(amount: Double, isIncome: Boolean, onComplete: (Boolean) -> Unit = {}) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val balance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                currentData.child("balance").value = if (isIncome) balance + amount else balance - amount

                val path = if (isIncome) "income_totals" else "expense_totals"
                val category = if (isIncome) "VAULT_DEPOSIT" else "VAULT_WITHDRAW"
                
                val categoryRef = currentData.child(path).child(category)
                val currentTotal = categoryRef.getValue(Double::class.java) ?: 0.0
                categoryRef.value = currentTotal + amount

                if (isIncome) addXp(currentData, XP_PER_DEPOSIT)
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
                val currentBalance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                currentData.child("balance").value = if (isIncome) currentBalance + amount else currentBalance - amount

                val path = if (isIncome) "income_totals" else "expense_totals"
                val catRef = currentData.child(path).child(category)
                catRef.value = (catRef.getValue(Double::class.java) ?: 0.0) + amount

                if (isIncome) addXp(currentData, XP_PER_DEPOSIT)
                return Transaction.success(currentData)
            }
            override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) { onComplete(c) }
        })
    }

    fun handleContribution(amount: Double, goalId: String, onComplete: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference

        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val balance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                if (balance < amount) return Transaction.abort()

                currentData.child("balance").value = balance - amount
                
                // 1. Add to monthly expense totals under SAVINGS category
                val savingsRef = currentData.child("expense_totals").child("SAVINGS")
                val currentSavingsTotal = savingsRef.getValue(Double::class.java) ?: 0.0
                savingsRef.value = currentSavingsTotal + amount

                // 2. Add to specific goal's permanent saved total
                val goalNode = currentData.child("goals").child(goalId)
                val saved = (goalNode.child("savedGold").getValue(Double::class.java) ?: 0.0) + amount
                val target = goalNode.child("targetGold").getValue(Double::class.java) ?: 0.0
                goalNode.child("savedGold").value = saved

                if (saved >= target && goalNode.child("status").value != "COMPLETED") {
                    goalNode.child("status").value = "COMPLETED"
                    addXp(currentData, XP_GOAL_COMPLETED)
                    val stars = currentData.child("stars").getValue(Int::class.java) ?: 0
                    currentData.child("stars").value = stars + 1
                    awardBadge(currentData, "goal_$goalId", "Completed: ${goalNode.child("name").value}")
                }

                handleStreakAndXp(currentData)
                return Transaction.success(currentData)
            }
            override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) { onComplete(c) }
        })
    }

    private fun handleStreakAndXp(userData: MutableData) {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val today = sdf.format(Date())
        val lastDate = userData.child("lastContributionDate").getValue(String::class.java) ?: ""
        var streak = userData.child("streak").getValue(Int::class.java) ?: 0

        if (lastDate != today) {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
            if (lastDate == sdf.format(yesterday.time)) streak++ else streak = 1
            
            userData.child("lastContributionDate").value = today
            userData.child("streak").value = streak

            if (streak == 3) awardBadge(userData, "streak_3", "3-Day Streak!")
            if (streak == 7) awardBadge(userData, "streak_7", "Weekly Warrior!")
        }

        val xpBonus = if (streak >= 3) XP_STREAK_BONUS else 0
        addXp(userData, XP_PER_CONTRIBUTION + xpBonus)
    }

    private fun addXp(userData: MutableData, amount: Int) {
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

    fun setBalance(context: Context, amount: Double) {
        context.getSharedPreferences("KitagoPrefs", Context.MODE_PRIVATE).edit().putFloat("total_balance", amount.toFloat()).apply()
    }
}
