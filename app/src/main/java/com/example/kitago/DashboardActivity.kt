package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        updateBalanceDisplay()
        setupNavigation()

        // CLICK BALANCE -> ADD BALANCE DIALOG
        findViewById<TextView>(R.id.tvBalance).setOnClickListener {
            showAddBalanceDialog()
        }

        // VIEW ALL GOALS
        findViewById<TextView>(R.id.tvViewAllGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateBalanceDisplay()
    }

    private fun updateBalanceDisplay() {
        val balance = DataManager.getBalance(this)
        findViewById<TextView>(R.id.tvBalance).text = String.format("$%.2f", balance)
    }

    private fun showAddBalanceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnOk = dialogView.findViewById<TextView>(R.id.btnDialogOk)

        title.text = "ADD TREASURE"
        message.text = "ENTER AMOUNT TO ADD TO YOUR VAULT:"
        btnOk.text = "DEPOSIT"

        // Add an EditText dynamically for input
        val input = EditText(this).apply {
            hint = "0.00"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            typeface = resources.getFont(R.font.press_start_2p)
            textSize = 14f
            setPadding(40, 40, 40, 40)
        }
        
        // Replace message text with input for this specific dialog
        val container = dialogView as android.widget.LinearLayout
        val index = container.indexOfChild(message)
        container.addView(input, index + 1)

        btnOk.setOnClickListener {
            val amountStr = input.text.toString()
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toDouble()
                DataManager.updateBalance(this, amount, isIncome = true)
                updateBalanceDisplay()
                dialog.dismiss()
                Toast.makeText(this, "TREASURE ADDED!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener { /* Already Home */ }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener {
            startActivity(Intent(this, ChallengesActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}
