package com.example.kitago

import android.content.Context
import android.content.SharedPreferences

object DataManager {
    private const val PREFS_NAME = "KitagoPrefs"
    private const val KEY_BALANCE = "total_balance"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBalance(context: Context): Double {
        return getPrefs(context).getFloat(KEY_BALANCE, 1250.00f).toDouble()
    }

    fun setBalance(context: Context, amount: Double) {
        getPrefs(context).edit().putFloat(KEY_BALANCE, amount.toFloat()).apply()
    }

    fun updateBalance(context: Context, amount: Double, isIncome: Boolean) {
        val currentBalance = getBalance(context)
        val newBalance = if (isIncome) currentBalance + amount else currentBalance - amount
        getPrefs(context).edit().putFloat(KEY_BALANCE, newBalance.toFloat()).apply()
    }
}
