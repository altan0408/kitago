package com.example.kitago

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class GoalDetailActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var goalRef: DatabaseReference
    private var currentGoal: Goal? = null
    private var userBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goal_detail)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()

        val goalId = intent.getStringExtra("GOAL_ID") ?: return
        val userId = firebaseAuth.currentUser?.uid ?: return
        val userNodeRef = firebaseDatabase.reference.child("users").child(userId)
        goalRef = userNodeRef.child("goals").child(goalId)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btnContribute).setOnClickListener {
            if (userBalance <= 0) {
                Toast.makeText(this, "YOUR VAULT IS EMPTY!", Toast.LENGTH_SHORT).show()
            } else {
                showContributeDialog()
            }
        }

        // Observe user balance to ensure we have the latest amount
        userNodeRef.child("balance").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userBalance = snapshot.getValue(Double::class.java) ?: 0.0
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        observeGoalDetails()
    }

    private fun observeGoalDetails() {
        goalRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentGoal = snapshot.getValue(Goal::class.java) ?: return
                val goal = currentGoal!!
                
                findViewById<TextView>(R.id.tvGoalDetailTitle).text = goal.name.uppercase()
                
                val progress = if (goal.targetGold > 0) (goal.savedGold / goal.targetGold * 100).toInt().coerceIn(0, 100) else 0
                findViewById<ProgressBar>(R.id.goalOrbProgress).progress = progress
                findViewById<TextView>(R.id.tvOrbPercent).text = "$progress%"

                findViewById<TextView>(R.id.tvSavedAmount).text = String.format("₱%.2f", goal.savedGold)
                findViewById<TextView>(R.id.tvTargetAmount).text = String.format("₱%.2f", goal.targetGold)
                findViewById<TextView>(R.id.tvDeadline).text = goal.deadline
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@GoalDetailActivity, "Error loading quest", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showContributeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnSave = dialogView.findViewById<TextView>(R.id.btnDialogOk)

        title.text = "FUND QUEST"
        message.text = "VAULT BALANCE: ₱${String.format("%.2f", userBalance)}\n\nENTER GOLD TO SAVE:"
        btnSave.text = "SAVE"

        val input = EditText(this).apply {
            hint = "0.00"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            val customFont = ResourcesCompat.getFont(this@GoalDetailActivity, R.font.press_start_2p)
            typeface = customFont
            textSize = 14f
            setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 140)
            params.setMargins(0, 0, 0, 40)
            layoutParams = params
        }
        
        val container = dialogView as LinearLayout
        val index = container.indexOfChild(message)
        container.addView(input, index + 1)

        btnSave.setOnClickListener {
            val amountStr = input.text.toString()
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toDouble()
                if (amount > userBalance) {
                    Toast.makeText(this, "INSUFFICIENT GOLD IN VAULT!", Toast.LENGTH_SHORT).show()
                } else {
                    contributeToGoal(amount)
                    dialog.dismiss()
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun contributeToGoal(amount: Double) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        val userNodeRef = firebaseDatabase.reference.child("users").child(userId)

        userNodeRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val balanceVal = currentData.child("balance").getValue(Double::class.java) ?: 0.0
                if (balanceVal < amount) return Transaction.abort()
                
                // 1. Deduct from monthly vault balance
                currentData.child("balance").value = balanceVal - amount

                // 2. Add to monthly expense totals under SAVINGS category
                val savingsRef = currentData.child("expense_totals").child("SAVINGS")
                val currentSavingsTotal = savingsRef.getValue(Double::class.java) ?: 0.0
                savingsRef.value = currentSavingsTotal + amount

                // 3. Add to specific goal's permanent saved total
                val goalId = intent.getStringExtra("GOAL_ID") ?: return Transaction.abort()
                val savedGoldVal = currentData.child("goals").child(goalId).child("savedGold").getValue(Double::class.java) ?: 0.0
                currentData.child("goals").child(goalId).child("savedGold").value = savedGoldVal + amount

                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    Toast.makeText(this@GoalDetailActivity, "QUEST FUNDED! VAULT UPDATED.", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
