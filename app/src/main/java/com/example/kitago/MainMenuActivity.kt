package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainMenuActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        // START -> LOGIN
        findViewById<TextView>(R.id.btnStart).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // CHALLENGES -> CHALLENGES HUB
        findViewById<TextView>(R.id.btnChallenges).setOnClickListener {
            val intent = Intent(this, ChallengesActivity::class.java)
            startActivity(intent)
        }

        // EXIT
        findViewById<TextView>(R.id.btnExit).setOnClickListener {
            finish()
        }
    }
}
