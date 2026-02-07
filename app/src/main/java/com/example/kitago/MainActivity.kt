package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        // START -> LOGIN
        findViewById<TextView>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // CHALLENGES -> CHALLENGES HUB
        findViewById<TextView>(R.id.btnChallenges).setOnClickListener {
            startActivity(Intent(this, ChallengesActivity::class.java))
        }

        // SETTINGS -> SETTINGS SCREEN
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // EXIT
        findViewById<TextView>(R.id.btnExit).setOnClickListener {
            finish()
        }
    }
}
