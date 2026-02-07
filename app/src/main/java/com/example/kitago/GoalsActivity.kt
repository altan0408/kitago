package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class GoalsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goals)

        setupNavigation()

        // NEW GOAL BUTTON -> CREATE GOAL SCREEN
        findViewById<TextView>(R.id.btnNewGoal).setOnClickListener {
            startActivity(Intent(this, CreateGoalActivity::class.java))
        }

        // EXISTING GOAL ITEM -> GOAL DETAIL SCREEN
        findViewById<LinearLayout>(R.id.sampleGoalItem).setOnClickListener {
            startActivity(Intent(this, GoalDetailActivity::class.java))
        }
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener {
            // Already here
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
