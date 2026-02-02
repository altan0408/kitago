package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // BACK BUTTON
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // SIGN IN -> DASHBOARD
        findViewById<TextView>(R.id.btnSignIn).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish() // Optional: remove Login from back stack
        }

        // REGISTER LINK -> SIGNUP
        findViewById<TextView>(R.id.tvRegisterNow).setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }
}
