package com.example.kitago

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class AddTransactionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val etAmount = findViewById<EditText>(R.id.etAmount)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btnSave).setOnClickListener {
            val amountStr = etAmount.text.toString()
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toDouble()
                // Update persistent balance (deducting gold)
                DataManager.updateBalance(this, amount, isIncome = false)
                
                Toast.makeText(this, "QUEST UPDATED: GOLD SPENT!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "ENTER AMOUNT!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
