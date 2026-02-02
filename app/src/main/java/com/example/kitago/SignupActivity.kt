package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity

class SignupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // BACK BUTTON
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // SIGN UP -> DASHBOARD
        findViewById<TextView>(R.id.btnSignUp).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }

        // LOGIN HERE LINK
        findViewById<TextView>(R.id.tvLoginHere).setOnClickListener {
            finish() // Since we came from Login or can just go there
        }
    }
}
