package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()

        // If already logged in, check role before navigating
        if (auth.currentUser != null) {
            checkRoleAndNavigate()
            return
        }

        setContentView(R.layout.activity_main_menu)

        findViewById<TextView>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<TextView>(R.id.btnChallenges).setOnClickListener {
            if (auth.currentUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                startActivity(Intent(this, ChallengesActivity::class.java))
            }
        }

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<TextView>(R.id.btnExit).setOnClickListener {
            finish()
        }
    }

    private fun checkRoleAndNavigate() {
        val user = auth.currentUser ?: return
        val db = FirebaseDatabase.getInstance().reference
        
        // Ensure game config is loaded
        DataManager.fetchGlobalConfig()

        db.child("admins").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // User is an ADMIN
                val intent = Intent(this, AdminActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                // Regular player
                val intent = Intent(this, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }.addOnFailureListener {
            // Fallback safety
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }
}
