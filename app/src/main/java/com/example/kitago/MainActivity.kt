package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        val btnStart = findViewById<ImageButton>(R.id.btnStart)
        val btnChallenges = findViewById<ImageButton>(R.id.btnChallenges)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnExit = findViewById<ImageButton>(R.id.btnExit)

        btnStart.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        btnChallenges.setOnClickListener {
            // TODO: navigate to Challenges
        }

        btnSettings.setOnClickListener {
            // TODO: open Settings
        }

        btnExit.setOnClickListener {
            finish()
        }
    }
}
