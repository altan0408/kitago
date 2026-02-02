package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnSignIn = findViewById<TextView>(R.id.btnSignIn)

        // BACK BUTTON
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // SIGN IN LOGIC
        btnSignIn.setOnClickListener {
            val user = etUsername.text.toString()
            val pass = etPassword.text.toString()

            if (user == "test1" && pass == "12345678") {
                // SUCCESS DIALOG
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.login_success_title))
                    .setMessage(getString(R.string.login_success_msg))
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        val intent = Intent(this, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            } else {
                // WRONG CREDENTIALS
                Toast.makeText(this, getString(R.string.error_invalid_credentials), Toast.LENGTH_SHORT).show()
                etUsername.text.clear()
                etPassword.text.clear()
            }
        }

        // REGISTER LINK
        findViewById<TextView>(R.id.tvRegisterNow).setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }
}
