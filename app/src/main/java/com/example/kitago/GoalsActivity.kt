package com.example.kitago

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
    private lateinit var goalsRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goals)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()
        
        val userId = firebaseAuth.currentUser?.uid ?: return
        goalsRef = firebaseDatabase.reference.child("users").child(userId).child("goals")

        setupNavigation()
        observeGoals()

        findViewById<TextView>(R.id.btnNewGoal).setOnClickListener {
            startActivity(Intent(this, CreateGoalActivity::class.java))
        }
    }

    private fun observeGoals() {
        val container = findViewById<LinearLayout>(R.id.goalsContainer) // Ensure this ID exists in XML
        
        goalsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                container.removeAllViews()
                for (goalSnapshot in snapshot.children) {
                    val goal = goalSnapshot.getValue(Goal::class.java)
                    if (goal != null) {
                        addGoalToLayout(container, goal)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@GoalsActivity, "Failed to load quests", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun addGoalToLayout(container: LinearLayout, goal: Goal) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_goal, container, false)
        
        val tvName = view.findViewById<TextView>(R.id.tvGoalName)
        val progressBar = view.findViewById<ProgressBar>(R.id.goalProgressBar)
        val tvProgress = view.findViewById<TextView>(R.id.tvProgressPercent)

        tvName.text = goal.name.uppercase()
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
        findViewById<ImageButton>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener { /* Already here */ }
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
