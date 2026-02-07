package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
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
                showGameDialog(
                    getString(R.string.login_success_title),
                    getString(R.string.login_success_msg)
                ) {
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            } else {
                showGameDialog(
                    "LOGIN FAILED",
                    getString(R.string.error_invalid_credentials)
                ) {
                    etUsername.text.clear()
                    etPassword.text.clear()
                }
            }
        }

        // REGISTER LINK
        findViewById<TextView>(R.id.tvRegisterNow).setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showGameDialog(title: String, message: String, onOk: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = message
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener {
            dialog.dismiss()
            onOk()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
