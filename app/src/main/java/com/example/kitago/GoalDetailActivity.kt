package com.example.kitago

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class GoalDetailActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var goalRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goal_detail)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()

        val goalId = intent.getStringExtra("GOAL_ID") ?: return
        val userId = firebaseAuth.currentUser?.uid ?: return
        goalRef = firebaseDatabase.reference.child("users").child(userId).child("goals").child(goalId)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        observeGoalDetails()
    }

    private fun observeGoalDetails() {
        goalRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val goal = snapshot.getValue(Goal::class.java) ?: return
                
                findViewById<TextView>(R.id.tvGoalDetailTitle).text = goal.name.uppercase()
                
                val progress = if (goal.targetGold > 0) (goal.savedGold / goal.targetGold * 100).toInt() else 0
                findViewById<ProgressBar>(R.id.goalOrbProgress).progress = progress
                findViewById<TextView>(R.id.tvOrbPercent).text = "$progress%"

                findViewById<TextView>(R.id.tvSavedAmount).text = String.format("₱%.2f", goal.savedGold)
                findViewById<TextView>(R.id.tvTargetAmount).text = String.format("₱%.2f", goal.targetGold)
                findViewById<TextView>(R.id.tvDeadline).text = goal.deadline
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@GoalDetailActivity, "Error loading quest: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
