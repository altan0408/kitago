package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class GoalsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goals)

        findViewById<TextView>(R.id.btnNewGoal).setOnClickListener {
            // Logic for new goal or navigate to detail for now
            startActivity(Intent(this, GoalDetailActivity::class.java))
        }
    }
}
