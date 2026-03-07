package com.example.kitago

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

@SuppressLint("SetTextI18n")
class GoalDetailActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var goalRef: DatabaseReference
    private var currentGoal: Goal? = null
    private var userBalance: Double = 0.0
    private var goalId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goal_detail)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()

        goalId = intent.getStringExtra("GOAL_ID") ?: run { finish(); return }
        val userId = firebaseAuth.currentUser?.uid ?: run { finish(); return }
        goalRef = firebaseDatabase.reference.child("goals").child(goalId)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btnContribute).setOnClickListener {
            val goal = currentGoal ?: return@setOnClickListener
            // Block contribution if any collaborator is still pending
            val hasPending = goal.collaboratorStatuses.values.any { it == "PENDING" }
            if (hasPending) {
                Toast.makeText(this, "WAITING FOR PARTY TO ACCEPT!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (goal.status == "COMPLETED") {
                Toast.makeText(this, "QUEST ALREADY COMPLETED!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (userBalance <= 0) {
                Toast.makeText(this, "YOUR VAULT IS EMPTY!", Toast.LENGTH_SHORT).show()
            } else {
                showContributeDialog()
            }
        }

        findViewById<ImageButton>(R.id.btnEditGoal).setOnClickListener {
            showEditGoalDialog()
        }

        findViewById<ImageButton>(R.id.btnDeleteGoal).setOnClickListener {
            showDeleteGoalDialog()
        }

        // Observe user balance
        firebaseDatabase.reference.child("users").child(userId).child("balance").addValueEventListener(object : ValueEventListener {
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

                findViewById<TextView>(R.id.tvSavedAmount).text = String.format(Locale.getDefault(), "₱%.2f", goal.savedGold)
                findViewById<TextView>(R.id.tvTargetAmount).text = String.format(Locale.getDefault(), "₱%.2f", goal.targetGold)
                findViewById<TextView>(R.id.tvDeadline).text = goal.deadline

                val tvCollabs = findViewById<TextView>(R.id.tvCollaborators)
                val tvCollabStreak = findViewById<TextView>(R.id.tvCollabStreak)
                val tvPendingStatus = findViewById<TextView>(R.id.tvPendingStatus)
                val btnContribute = findViewById<TextView>(R.id.btnContribute)

                if (goal.isCollaborative) {
                    val names = goal.collaboratorNames.values.joinToString(", ")
                    tvCollabs.text = "PARTY: $names"
                    tvCollabs.visibility = View.VISIBLE

                    // Show collab streak
                    if (goal.collabStreak > 0) {
                        tvCollabStreak.text = "\uD83D\uDD25 COLLAB STREAK: ${goal.collabStreak} DAYS"
                        tvCollabStreak.visibility = View.VISIBLE
                    } else {
                        tvCollabStreak.visibility = View.GONE
                    }

                    // Check for pending collaborators
                    val pendingNames = goal.collaboratorStatuses.filter { it.value == "PENDING" }
                        .mapNotNull { goal.collaboratorNames[it.key] }
                    if (pendingNames.isNotEmpty()) {
                        tvPendingStatus.text = "⏳ WAITING FOR: ${pendingNames.joinToString(", ").uppercase()}"
                        tvPendingStatus.visibility = View.VISIBLE
                        btnContribute.alpha = 0.5f
                    } else {
                        tvPendingStatus.visibility = View.GONE
                        btnContribute.alpha = 1.0f
                    }
                } else {
                    tvCollabs.text = ""
                    tvCollabs.visibility = View.GONE
                    tvCollabStreak.visibility = View.GONE
                    tvPendingStatus.visibility = View.GONE
                    btnContribute.alpha = 1.0f
                }

                // Show completed state
                if (goal.status == "COMPLETED") {
                    btnContribute.text = "✅ QUEST COMPLETE"
                    btnContribute.alpha = 0.7f
                } else {
                    if (goal.collaboratorStatuses.values.none { it == "PENDING" }) {
                        btnContribute.text = "CONTRIBUTE"
                        btnContribute.alpha = 1.0f
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@GoalDetailActivity, "Error loading quest", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showEditGoalDialog() {
        val goal = currentGoal ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_create_goal, null)
        dialogView.findViewById<ImageButton>(R.id.btnBack).visibility = View.GONE
        dialogView.findViewById<TextView>(R.id.tvCreateTitle).text = "EDIT QUEST"
        dialogView.findViewById<TextView>(R.id.btnCreateGoal).text = "UPDATE"
        dialogView.findViewById<LinearLayout>(R.id.btnSelectCollaborator).visibility = View.GONE

        val etName = dialogView.findViewById<EditText>(R.id.etGoalName)
        val etTarget = dialogView.findViewById<EditText>(R.id.etTargetGold)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvSelectedDate)

        etName.setText(goal.name)
        etTarget.setText(goal.targetGold.toString())
        tvDate.text = goal.deadline
        var updatedDeadline = goal.deadline

        dialogView.findViewById<LinearLayout>(R.id.btnSelectDate).setOnClickListener {
            val calendar = Calendar.getInstance()
            val dpd = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                updatedDeadline = "$dayOfMonth/${month + 1}/$year"
                tvDate.text = updatedDeadline
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            dpd.datePicker.minDate = System.currentTimeMillis()
            dpd.show()
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.btnCreateGoal).setOnClickListener {
            val newName = etName.text.toString().trim()
            val newTarget = etTarget.text.toString().toDoubleOrNull() ?: 0.0

            if (newName.isNotEmpty() && newTarget > 0) {
                updateGoalData(newName, newTarget, updatedDeadline)
                dialog.dismiss()
            }
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateGoalData(name: String, target: Double, deadline: String) {
        val goal = currentGoal ?: return
        val db = firebaseDatabase.reference
        val updates = hashMapOf<String, Any?>(
            "goals/${goal.id}/name" to name,
            "goals/${goal.id}/targetGold" to target,
            "goals/${goal.id}/deadline" to deadline
        )

        db.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "QUEST UPDATED!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteGoalDialog() {
        val goal = currentGoal ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "ABANDON QUEST?"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = if (goal.isCollaborative)
            "DELETE ${goal.name.uppercase()} FOR ALL PARTY MEMBERS?"
        else "ARE YOU SURE YOU WANT TO DELETE ${goal.name.uppercase()}?"

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "DELETE"
            setBackgroundResource(R.drawable.bg_button_orange)
            setOnClickListener {
                deleteGoalForAll()
                dialog.dismiss()
                finish()
            }
        }

        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@GoalDetailActivity, R.font.press_start_2p)
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

    private fun deleteGoalForAll() {
        val goal = currentGoal ?: return
        val db = firebaseDatabase.reference
        val updates = hashMapOf<String, Any?>()

        updates["goals/${goal.id}"] = null
        goal.collaboratorStatuses.keys.forEach { uid ->
            updates["users/$uid/goals/${goal.id}"] = null
            // Also clean up any pending challenge requests
            updates["challenge_requests/$uid/${goal.id}"] = null
        }

        db.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "QUEST DELETED", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showContributeDialog() {
        val goal = currentGoal ?: return
        val remaining = (goal.targetGold - goal.savedGold).coerceAtLeast(0.0)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create()

        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnSave = dialogView.findViewById<TextView>(R.id.btnDialogOk)

        title.text = "FUND QUEST"
        message.text = "VAULT: ₱${String.format(Locale.getDefault(), "%.2f", userBalance)}\nREMAINING: ₱${String.format(Locale.getDefault(), "%.2f", remaining)}\n\nENTER GOLD TO SAVE:"
        btnSave.text = "SAVE"

        val input = EditText(this).apply {
            hint = "0.00"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            typeface = ResourcesCompat.getFont(this@GoalDetailActivity, R.font.press_start_2p)
            textSize = 14f
            setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 140).apply { setMargins(0, 0, 0, 40) }
        }

        val container = dialogView as LinearLayout
        container.addView(input, container.indexOfChild(message) + 1)

        btnSave.setOnClickListener {
            val amountStr = input.text.toString()
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                if (amount <= 0) {
                    Toast.makeText(this, "ENTER A VALID AMOUNT!", Toast.LENGTH_SHORT).show()
                } else if (amount > userBalance) {
                    Toast.makeText(this, "INSUFFICIENT GOLD!", Toast.LENGTH_SHORT).show()
                } else if (amount > remaining) {
                    Toast.makeText(this, "EXCEEDS QUEST TARGET!", Toast.LENGTH_SHORT).show()
                } else {
                    contributeToGoal(amount)
                    dialog.dismiss()
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        dialog.show()
    }

    private fun contributeToGoal(amount: Double) {
        val goal = currentGoal ?: return
        // Use DataManager for atomic balance deduction + XP + streak tracking
        DataManager.handleContribution(amount, goalId, goal) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "QUEST FUNDED!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "FAILED TO CONTRIBUTE!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
