package com.example.kitago

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object DataManager {
    private const val PREFS_NAME = "KitagoPrefs"
    private const val KEY_BALANCE = "total_balance"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBalance(context: Context): Double {
        return getPrefs(context).getFloat(KEY_BALANCE, 0.00f).toDouble()
    }

    fun setBalance(context: Context, amount: Double) {
        getPrefs(context).edit().putFloat(KEY_BALANCE, amount.toFloat()).apply()
    }

    fun syncUpdateBalance(amount: Double, isIncome: Boolean, onComplete: (Boolean) -> Unit = {}) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                // 1. Update Total Balance
                val currentBalance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                currentData.child("balance").value = if (isIncome) currentBalance + amount else currentBalance - amount

                // 2. Update Monthly Totals for Dashboard Display
                val path = if (isIncome) "income_totals" else "expense_totals"
                val category = if (isIncome) "VAULT_DEPOSIT" else "VAULT_WITHDRAW"
                
                val categoryRef = currentData.child(path).child(category)
                val currentTotal = categoryRef.getValue(Double::class.java) ?: 0.0
                categoryRef.value = currentTotal + amount

                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                onComplete(committed)
            }
        })
    }

    fun syncAddTransaction(amount: Double, category: String, note: String, isIncome: Boolean, onComplete: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                // 1. Update Total Balance
                val currentBalance = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                currentData.child("balance").value = if (isIncome) currentBalance + amount else currentBalance - amount

                // 2. Update Category Totals
                val path = if (isIncome) "income_totals" else "expense_totals"
                val categoryRef = currentData.child(path).child(category)
                val currentCategoryTotal = categoryRef.getValue(Double::class.java) ?: 0.0
                categoryRef.value = currentCategoryTotal + amount

                // 3. Log History
                val transactionId = System.currentTimeMillis().toString()
                val logRef = currentData.child("history").child(transactionId)
                logRef.child("amount").value = amount
                logRef.child("category").value = category
                logRef.child("note").value = note
                logRef.child("isIncome").value = isIncome
                logRef.child("timestamp").value = ServerValue.TIMESTAMP

                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                onComplete(committed)
            }
        })
    }
}
