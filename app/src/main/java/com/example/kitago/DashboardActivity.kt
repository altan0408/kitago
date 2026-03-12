package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
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

        // Show styled exit confirmation dialog on back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmDialog()
            }
        })

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()
        
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        userRef = firebaseDatabase.reference.child("users").child(currentUser.uid)

        ensureUsernameIsIndexed()
        checkAndPerformMonthlyReset()
        setupUI()
        observeUserData()
        setupNavigation()
        checkAdminStatus()
    }

    private fun checkAdminStatus() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firebaseDatabase.reference.child("admins").child(uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Show admin badge on username
                val tvUsername = findViewById<TextView>(R.id.tvUsername)
                if (!tvUsername.text.startsWith("🛡️")) {
                    tvUsername.text = "🛡️ " + tvUsername.text
                }

                // Show and wire the admin panel button
                val btnAdmin = findViewById<ImageButton>(R.id.btnAdminPanel)
                btnAdmin.visibility = View.VISIBLE
                btnAdmin.setOnClickListener {
                    startActivity(Intent(this, AdminActivity::class.java))
                }
            }
        }
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
        findViewById<LinearLayout>(R.id.goalPreviewContainer).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun observeUserData() {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val username = snapshot.child("username").getValue(String::class.java) ?: "ADVENTURER"
                val balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                val profilePicUrl = snapshot.child("profilePic").getValue(String::class.java)

                val tvUser = findViewById<TextView>(R.id.tvUsername)
                // Keep the shield if it was added by checkAdminStatus
                if (tvUser.text.startsWith("🛡️")) {
                    tvUser.text = "🛡️ " + username.uppercase()
                } else {
                    tvUser.text = username.uppercase()
                }
                
                findViewById<TextView>(R.id.tvBalance).text = String.format(Locale.getDefault(), "₱%.2f", balance)

                ImageUtils.loadProfileImage(this@DashboardActivity, profilePicUrl, findViewById(R.id.ivAvatar))

                val expenses = snapshot.child("expense_totals")
                var totalExpense = 0.0
                for (child in expenses.children) { totalExpense += child.getValue(Double::class.java) ?: 0.0 }
                
                val income = snapshot.child("income_totals")
                var totalIncome = 0.0
                for (child in income.children) { totalIncome += child.getValue(Double::class.java) ?: 0.0 }

                findViewById<TextView>(R.id.tvTotalIncome).text = "₱${totalIncome.toInt()}"
                findViewById<TextView>(R.id.tvTotalExpense).text = "₱${totalExpense.toInt()}"

                val goalIds = snapshot.child("goals").children.mapNotNull { it.key }
                fetchGoalsData(goalIds)

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
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchGoalsData(goalIds: List<String>) {
        val container = findViewById<LinearLayout>(R.id.goalPreviewContainer)

        if (goalIds.isEmpty()) {
            findViewById<TextView>(R.id.tvTotalSavedGoals).text = "₱0.00"
            container.removeAllViews()
            addEmptyQuestMessage(container)
            return
        }

        var totalQuestSaved = 0.0
        val incompleteGoals = mutableListOf<Goal>()
        var loadedCount = 0

        for (id in goalIds) {
            firebaseDatabase.reference.child("goals").child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val goal = snapshot.getValue(Goal::class.java)
                    if (goal != null) {
                        totalQuestSaved += goal.savedGold
                        if (goal.savedGold < goal.targetGold) incompleteGoals.add(goal)
                    }
                    
                    loadedCount++
                    if (loadedCount == goalIds.size) {
                        findViewById<TextView>(R.id.tvTotalSavedGoals).text = String.format(Locale.getDefault(), "₱%.2f", totalQuestSaved)
                        container.removeAllViews()
                        if (incompleteGoals.isEmpty()) {
                            addEmptyQuestMessage(container)
                        } else {
                            val displayGoals = incompleteGoals.sortedByDescending {
                                if (it.targetGold > 0) it.savedGold / it.targetGold else 0.0
                            }.take(5)
                            for (goal in displayGoals) {
                                addGoalPreviewItem(container, goal)
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    loadedCount++
                }
            })
        }
    }

    private fun addEmptyQuestMessage(container: LinearLayout) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_panel)
            setPadding(48, 48, 48, 48)
        }
        val label = TextView(this).apply {
            text = "NO ACTIVE QUESTS"
            textSize = 8f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@DashboardActivity, R.font.press_start_2p)
            setTextColor(getColor(R.color.text_dark))
            gravity = android.view.Gravity.CENTER
        }
        item.addView(label)
        container.addView(item)
    }

    private fun addGoalPreviewItem(container: LinearLayout, goal: Goal) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_panel)
            setPadding(48, 36, 48, 36)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16
            layoutParams = params
            setOnClickListener { startActivity(Intent(this@DashboardActivity, GoalsActivity::class.java)) }
        }
        val nameLabel = TextView(this).apply {
            text = goal.name.uppercase()
            textSize = 8f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@DashboardActivity, R.font.press_start_2p)
            setTextColor(getColor(R.color.text_dark))
        }
        val progress = if (goal.targetGold > 0) (goal.savedGold / goal.targetGold * 100).toInt() else 0
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 36
            ).apply { topMargin = 20 }
            max = 100
            this.progress = progress
            progressDrawable = getDrawable(R.drawable.bg_xp_bar)
        }
        val statsLabel = TextView(this).apply {
            text = "₱${goal.savedGold.toInt()} / ₱${goal.targetGold.toInt()}  ($progress%)"
            textSize = 6f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@DashboardActivity, R.font.press_start_2p)
            setTextColor(getColor(R.color.text_muted))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 12
            layoutParams = params
        }
        item.addView(nameLabel)
        item.addView(progressBar)
        item.addView(statsLabel)
        container.addView(item)
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

    private fun showExitConfirmDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "EXIT GAME?"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ARE YOU SURE YOU\nWANT TO LEAVE?"

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "EXIT"
            setBackgroundResource(R.drawable.bg_button_orange)
            setOnClickListener { dialog.dismiss(); finishAffinity() }
        }

        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@DashboardActivity, R.font.press_start_2p)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted))
            setOnClickListener { dialog.dismiss() }
        }
        (dialogView as LinearLayout).addView(btnCancel)

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
