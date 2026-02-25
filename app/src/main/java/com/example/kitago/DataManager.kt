package com.example.kitago

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

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

        userRef.child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentBalance = currentData.getValue(Double::class.java) ?: 0.0
                val newBalance = if (isIncome) currentBalance + amount else currentBalance - amount
                currentData.value = newBalance
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                onComplete(committed)
            }
        })
    }
}
