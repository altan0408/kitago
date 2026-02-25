package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            goToLogin()
            return
        }

        userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)

        // 1. SET INITIAL AUTH DATA (Shows immediately)
        val tvName = findViewById<TextView>(R.id.tvUsernameProfile)
        val tvEmail = findViewById<TextView>(R.id.tvEmailProfile)
        val ivAvatar = findViewById<ImageView>(R.id.ivLargeAvatar)

        tvName.text = (currentUser.displayName ?: "ADVENTURER").uppercase()
        tvEmail.text = currentUser.email ?: "---"
        loadProfileImage(currentUser.photoUrl?.toString(), ivAvatar)

        // 2. FETCH REAL DATABASE STATS
        loadUserProfile()
        setupNavigation()

        // SIGN OUT LOGIC
        findViewById<TextView>(R.id.btnSignOut).setOnClickListener {
            auth.signOut()

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            
            GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
                Toast.makeText(this, "LOGGED OUT", Toast.LENGTH_SHORT).show()
                goToLogin()
            }
        }
    }

    private fun loadUserProfile() {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val username = snapshot.child("username").getValue(String::class.java)
                val email = snapshot.child("email").getValue(String::class.java)
                val level = snapshot.child("level").getValue(Int::class.java) ?: 1
                val wins = snapshot.child("wins").getValue(Int::class.java) ?: 0
                val streak = snapshot.child("streak").getValue(Int::class.java) ?: 0
                val profilePicUrl = snapshot.child("profilePic").getValue(String::class.java)

                if (username != null) findViewById<TextView>(R.id.tvUsernameProfile).text = username.uppercase()
                if (email != null) findViewById<TextView>(R.id.tvEmailProfile).text = email
                
                findViewById<TextView>(R.id.tvLevel).text = "LEVEL $level"
                findViewById<TextView>(R.id.tvWins).text = "WINS: $wins"
                findViewById<TextView>(R.id.tvStreak).text = "STREAK: $streak DAYS"

                if (profilePicUrl != null) {
                    loadProfileImage(profilePicUrl, findViewById(R.id.ivLargeAvatar))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // If this triggers, check your Firebase Database Rules!
            }
        })
    }

    private fun loadProfileImage(url: String?, imageView: ImageView) {
        if (!url.isNullOrEmpty()) {
            Glide.with(this)
                .load(url)
                .circleCrop()
                .placeholder(R.drawable.logo_kitago_main)
                .error(R.drawable.logo_kitago_main)
                .into(imageView)
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener {
            startActivity(Intent(this, ChallengesActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener { /* Already here */ }
    }
}
