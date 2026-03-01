package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.bumptech.glide.Glide
import com.google.firebase.database.*

class ChallengesActivity : ComponentActivity() {
    private lateinit var database: FirebaseDatabase
    private lateinit var leaderboardContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenges)

        database = FirebaseDatabase.getInstance()
        leaderboardContainer = findViewById(R.id.leaderboardContainer)

        setupNavigation()
        loadLeaderboard()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btnAction).setOnClickListener {
            startActivity(Intent(this, CreateJoinChallengeActivity::class.java))
        }
    }

    private fun loadLeaderboard() {
        val usersRef = database.reference.child("users")
        usersRef.orderByChild("balance").limitToLast(10).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                leaderboardContainer.removeAllViews()
                val userList = mutableListOf<Pair<String, Double>>()
                
                for (userSnap in snapshot.children) {
                    val name = userSnap.child("username").getValue(String::class.java) ?: "Adventurer"
                    val balance = userSnap.child("balance").getValue(Double::class.java) ?: 0.0
                    val pic = userSnap.child("profilePic").getValue(String::class.java)
                    
                    // We'll calculate total saved from goals instead
                    var totalSaved = 0.0
                    val goals = userSnap.child("goals")
                    for (goal in goals.children) {
                        totalSaved += goal.child("savedGold").getValue(Double::class.java) ?: 0.0
                    }
                    
                    // Add to list for sorting
                    // For now, let's just add the entry
                    addLeaderboardEntry(name, totalSaved, pic)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addLeaderboardEntry(name: String, score: Double, pic: String?) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_leaderboard, leaderboardContainer, false)
        val rank = leaderboardContainer.childCount + 1
        
        view.findViewById<TextView>(R.id.tvRank).text = "$rank."
        view.findViewById<TextView>(R.id.tvLeaderboardName).text = name.uppercase()
        view.findViewById<TextView>(R.id.tvLeaderboardScore).text = "₱${score.toInt()}"
        
        val avatar = view.findViewById<ImageView>(R.id.ivLeaderboardAvatar)
        if (!pic.isNullOrEmpty()) {
            if (pic.startsWith("http")) Glide.with(this).load(pic).circleCrop().into(avatar)
            else {
                try {
                    val decodedString = android.util.Base64.decode(pic, android.util.Base64.DEFAULT)
                    val decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    Glide.with(this).load(decodedByte).circleCrop().into(avatar)
                } catch (e: Exception) { avatar.setImageResource(R.drawable.logo_kitago_main) }
            }
        }
        
        leaderboardContainer.addView(view)
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener { startActivity(Intent(this, DashboardActivity::class.java)) }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener { startActivity(Intent(this, GoalsActivity::class.java)) }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener { startActivity(Intent(this, AddTransactionActivity::class.java)) }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
    }
}
