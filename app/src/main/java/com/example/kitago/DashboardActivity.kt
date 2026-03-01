package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class DashboardActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var userRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()
        
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        userRef = firebaseDatabase.reference.child("users").child(currentUser.uid)

        // Self-heal: ensure username is indexed for friend searches
        ensureUsernameIsIndexed()
        
        checkAndPerformMonthlyReset()
        setupUI()
        observeUserData()
        setupNavigation()
    }

    private fun ensureUsernameIsIndexed() {
        val user = firebaseAuth.currentUser ?: return
        userRef.child("username").get().addOnSuccessListener { snapshot ->
            val username = snapshot.getValue(String::class.java)
            if (username != null) {
                val cleanName = username.lowercase().replace(" ", "_")
                firebaseDatabase.reference.child("usernames").child(cleanName).setValue(user.uid)
            }
        }
    }

    private fun checkAndPerformMonthlyReset() {
        val calendar = Calendar.getInstance()
        val currentMonthYear = "${calendar.get(Calendar.MONTH)}_${calendar.get(Calendar.YEAR)}"

        userRef.child("lastResetMonth").get().addOnSuccessListener { snapshot ->
            val lastReset = snapshot.getValue(String::class.java)
            if (lastReset != null && lastReset != currentMonthYear) {
                // NEW MONTH: Reset balance and totals but KEEP goals
                val updates = hashMapOf<String, Any?>(
                    "balance" to 0.0,
                    "expense_totals" to null,
                    "income_totals" to null,
                    "lastResetMonth" to currentMonthYear
                )
                userRef.updateChildren(updates)
                Toast.makeText(this, "NEW MONTH! VAULT RESET.", Toast.LENGTH_LONG).show()
            } else if (lastReset == null) {
                userRef.child("lastResetMonth").setValue(currentMonthYear)
            }
        }
    }

    private fun setupUI() {
        findViewById<TextView>(R.id.tvBalance).setOnClickListener { showAddBalanceDialog() }
        findViewById<TextView>(R.id.tvViewAllGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.goalPreviewItem).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
    }

    private fun observeUserData() {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val username = snapshot.child("username").getValue(String::class.java) ?: "ADVENTURER"
                val balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                val profilePicUrl = snapshot.child("profilePic").getValue(String::class.java)

                findViewById<TextView>(R.id.tvUsername).text = username.uppercase()
                findViewById<TextView>(R.id.tvBalance).text = String.format("₱%.2f", balance)
                
                loadProfileImage(profilePicUrl, findViewById(R.id.ivAvatar))

                // Monthly Summary
                val expenses = snapshot.child("expense_totals")
                var totalExpense = 0.0
                for (child in expenses.children) { totalExpense += child.getValue(Double::class.java) ?: 0.0 }
                
                val income = snapshot.child("income_totals")
                var totalIncome = 0.0
                for (child in income.children) { totalIncome += child.getValue(Double::class.java) ?: 0.0 }

                findViewById<TextView>(R.id.tvTotalIncome).text = "₱${totalIncome.toInt()}"
                findViewById<TextView>(R.id.tvTotalExpense).text = "₱${totalExpense.toInt()}"

                // Total Quest Savings
                val goals = snapshot.child("goals")
                var totalQuestSaved = 0.0
                var lastIncompleteGoal: Goal? = null
                
                for (goalSnap in goals.children) {
                    val goal = goalSnap.getValue(Goal::class.java) ?: continue
                    totalQuestSaved += goal.savedGold
                    if (goal.savedGold < goal.targetGold) lastIncompleteGoal = goal
                }
                findViewById<TextView>(R.id.tvTotalSavedGoals).text = String.format("₱%.2f", totalQuestSaved)

                // HP Bar
                val hpBar = findViewById<ProgressBar>(R.id.budgetHpBar)
                val hpLabel = findViewById<TextView>(R.id.tvBudgetHpLabel)
                if (balance <= 0) {
                    hpBar.visibility = View.GONE
                    hpLabel.visibility = View.GONE
                } else {
                    hpBar.visibility = View.VISIBLE
                    hpLabel.visibility = View.VISIBLE
                    hpBar.progress = ((balance / 5000.0) * 100).toInt().coerceIn(0, 100)
                }

                // Quest Preview
                lastIncompleteGoal?.let { updateGoalPreview(it) } ?: run {
                    findViewById<TextView>(R.id.tvPreviewGoalName).text = "NO ACTIVE QUESTS"
                    findViewById<ProgressBar>(R.id.previewGoalProgress).progress = 0
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateGoalPreview(goal: Goal) {
        findViewById<TextView>(R.id.tvPreviewGoalName).text = goal.name.uppercase()
        val progress = if (goal.targetGold > 0) (goal.savedGold / goal.targetGold * 100).toInt() else 0
        findViewById<ProgressBar>(R.id.previewGoalProgress).progress = progress
    }

    private fun loadProfileImage(data: String?, imageView: ImageView) {
        if (data == null) { imageView.setImageResource(R.drawable.logo_kitago_main); return }
        if (data.startsWith("http")) {
            Glide.with(this).load(data).circleCrop().placeholder(R.drawable.logo_kitago_main).into(imageView)
        } else {
            try {
                val androidUtilBase64 = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(androidUtilBase64, 0, androidUtilBase64.size)
                Glide.with(this).load(bitmap).circleCrop().into(imageView)
            } catch (e: Exception) { imageView.setImageResource(R.drawable.logo_kitago_main) }
        }
    }

    private fun showAddBalanceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        val input = EditText(this).apply {
            hint = "0.00"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            val customFont = ResourcesCompat.getFont(this@DashboardActivity, R.font.press_start_2p)
            typeface = customFont; textSize = 14f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
        }
        (dialogView as LinearLayout).addView(input, 2)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "DEPOSIT"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener {
            val amountStr = input.text.toString()
            if (amountStr.isNotEmpty()) {
                DataManager.syncUpdateBalance(amountStr.toDouble(), true) { dialog.dismiss() }
            }
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener { }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener { startActivity(Intent(this, GoalsActivity::class.java)) }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener { startActivity(Intent(this, AddTransactionActivity::class.java)) }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener { startActivity(Intent(this, ChallengesActivity::class.java)) }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
    }
}
