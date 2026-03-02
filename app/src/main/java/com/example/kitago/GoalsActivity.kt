package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class GoalsActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var userGoalsRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goals)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()
        
        val userId = firebaseAuth.currentUser?.uid ?: return
        userGoalsRef = firebaseDatabase.reference.child("users").child(userId).child("goals")

        setupNavigation()
        observeUserGoals()

        findViewById<TextView>(R.id.btnNewGoal).setOnClickListener {
            startActivity(Intent(this, CreateGoalActivity::class.java))
        }
    }

    private fun observeUserGoals() {
        val container = findViewById<LinearLayout>(R.id.goalsContainer)
        
        userGoalsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                container.removeAllViews()
                
                val myId = firebaseAuth.currentUser?.uid ?: ""
                val goalIds = snapshot.children.mapNotNull { it.key }
                
                if (goalIds.isEmpty()) {
                    addHeader(container, "ACTIVE QUESTS")
                    addEmptyMessage(container)
                    return
                }

                val pendingInvites = mutableListOf<Goal>()
                val activeGoals = mutableListOf<Goal>()
                var loadedCount = 0

                for (goalId in goalIds) {
                    firebaseDatabase.reference.child("goals").child(goalId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(goalSnap: DataSnapshot) {
                            val goal = goalSnap.getValue(Goal::class.java)
                            if (goal != null) {
                                val myStatus = goal.collaboratorStatuses[myId]
                                val anyoneElsePending = goal.collaboratorStatuses.values.any { it == "PENDING" }
                                
                                if (myStatus == "PENDING" || (goal.isCollaborative && anyoneElsePending)) {
                                    pendingInvites.add(goal)
                                } else {
                                    activeGoals.add(goal)
                                }
                            }
                            
                            loadedCount++
                            if (loadedCount == goalIds.size) {
                                updateUI(container, pendingInvites, activeGoals)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            loadedCount++
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateUI(container: LinearLayout, pending: List<Goal>, active: List<Goal>) {
        container.removeAllViews()
        
        if (pending.isNotEmpty()) {
            addHeader(container, "PENDING INVITATIONS")
            for (invite in pending) {
                addInviteToLayout(container, invite)
            }
        }

        addHeader(container, "ACTIVE QUESTS")
        if (active.isEmpty()) {
            addEmptyMessage(container)
        } else {
            for (goal in active) {
                addGoalToLayout(container, goal)
            }
        }
    }

    private fun addHeader(container: LinearLayout, text: String) {
        val header = TextView(this).apply {
            this.text = text
            this.setPadding(0, 40, 0, 16)
            this.typeface = resources.getFont(R.font.press_start_2p)
            this.textSize = 10f
            this.setTextColor(getColor(R.color.gold_light))
        }
        container.addView(header)
    }

    private fun addEmptyMessage(container: LinearLayout) {
        val msg = TextView(this).apply {
            this.text = "NO ACTIVE QUESTS"
            this.setPadding(0, 40, 0, 40)
            this.typeface = resources.getFont(R.font.press_start_2p)
            this.textSize = 8f
            this.gravity = android.view.Gravity.CENTER
            this.setTextColor(getColor(R.color.text_muted))
        }
        container.addView(msg)
    }

    private fun addInviteToLayout(container: LinearLayout, goal: Goal) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_goal, container, false)
        val myId = firebaseAuth.currentUser?.uid ?: ""
        val myStatus = goal.collaboratorStatuses[myId]
        
        if (myStatus == "PENDING") {
            view.findViewById<TextView>(R.id.tvGoalName).text = "INVITE: ${goal.name.uppercase()}"
            view.findViewById<TextView>(R.id.tvProgressPercent).text = "FROM: ${goal.creatorName}"
        } else {
            view.findViewById<TextView>(R.id.tvGoalName).text = "WAITING: ${goal.name.uppercase()}"
            val pendingNames = goal.collaboratorStatuses.filter { it.value == "PENDING" }.mapNotNull { goal.collaboratorNames[it.key] }
            view.findViewById<TextView>(R.id.tvProgressPercent).text = "WAITING FOR: ${pendingNames.joinToString(", ")}"
        }
        
        view.findViewById<ProgressBar>(R.id.goalProgressBar).visibility = View.GONE

        view.setOnClickListener {
            showInviteDialog(goal)
        }
        container.addView(view)
    }

    private fun showInviteDialog(goal: Goal) {
        val myId = firebaseAuth.currentUser?.uid ?: ""
        val myStatus = goal.collaboratorStatuses[myId]
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        
        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnOk = dialogView.findViewById<TextView>(R.id.btnDialogOk)

        if (myStatus == "PENDING") {
            title.text = "QUEST INVITE"
            message.text = "${goal.creatorName} invited you to collab on:\n\n${goal.name.uppercase()}\nTARGET: ₱${goal.targetGold}"
            btnOk.text = "ACCEPT"
            btnOk.setOnClickListener {
                respondToInvite(goal, "ACCEPTED")
                dialog.dismiss()
            }

            val btnDecline = TextView(this).apply {
                text = "DECLINE"
                typeface = resources.getFont(R.font.press_start_2p)
                textSize = 12f
                setTextColor(getColor(R.color.hp_red))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 40)
                setOnClickListener {
                    respondToInvite(goal, "DECLINED")
                    dialog.dismiss()
                }
            }
            (dialogView as LinearLayout).addView(btnDecline)
        } else {
            title.text = "PENDING PARTY"
            val pendingNames = goal.collaboratorStatuses.filter { it.value == "PENDING" }.mapNotNull { goal.collaboratorNames[it.key] }
            message.text = "Waiting for these adventurers to join:\n\n${pendingNames.joinToString("\n")}"
            btnOk.text = "OK"
            btnOk.setOnClickListener { dialog.dismiss() }

            val btnCancel = TextView(this).apply {
                text = "ABANDON QUEST"
                typeface = resources.getFont(R.font.press_start_2p)
                textSize = 10f
                setTextColor(getColor(R.color.hp_red))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 40)
                setOnClickListener {
                    respondToInvite(goal, "CANCELLED")
                    dialog.dismiss()
                }
            }
            (dialogView as LinearLayout).addView(btnCancel)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun respondToInvite(goal: Goal, status: String) {
        val myId = firebaseAuth.currentUser!!.uid
        val db = firebaseDatabase.reference
        val updates = hashMapOf<String, Any?>()

        if (status == "ACCEPTED") {
            updates["goals/${goal.id}/collaboratorStatuses/$myId"] = "ACCEPTED"
            updates["users/$myId/goals/${goal.id}"] = "ACCEPTED"
        } else if (status == "DECLINED") {
            updates["users/$myId/goals/${goal.id}"] = null
            updates["goals/${goal.id}/collaboratorStatuses/$myId"] = "DECLINED"
        } else if (status == "CANCELLED") {
            // Remove reference for everyone and delete the central goal
            updates["goals/${goal.id}"] = null
            goal.collaboratorStatuses.keys.forEach { uid ->
                updates["users/$uid/goals/${goal.id}"] = null
            }
        }

        db.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "QUEST UPDATED", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addGoalToLayout(container: LinearLayout, goal: Goal) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_goal, container, false)
        val tvName = view.findViewById<TextView>(R.id.tvGoalName)
        val progressBar = view.findViewById<ProgressBar>(R.id.goalProgressBar)
        val tvProgress = view.findViewById<TextView>(R.id.tvProgressPercent)

        tvName.text = if (goal.isCollaborative) "⚔️ ${goal.name.uppercase()}" else goal.name.uppercase()
        val progress = if (goal.targetGold > 0) (goal.savedGold / goal.targetGold * 100).toInt() else 0
        progressBar.progress = progress
        tvProgress.text = "$progress% COMPLETE"

        view.setOnClickListener {
            val intent = Intent(this, GoalDetailActivity::class.java)
            intent.putExtra("GOAL_ID", goal.id)
            startActivity(intent)
        }
        container.addView(view)
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener { startActivity(Intent(this, DashboardActivity::class.java)); finish() }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener { }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener { startActivity(Intent(this, AddTransactionActivity::class.java)) }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener { startActivity(Intent(this, ChallengesActivity::class.java)) }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
    }
}
