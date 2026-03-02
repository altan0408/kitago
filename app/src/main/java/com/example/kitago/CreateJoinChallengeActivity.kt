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

class CreateJoinChallengeActivity : ComponentActivity() {
    private lateinit var database: FirebaseDatabase
    private lateinit var leaderboardContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_join_challenge)

        database = FirebaseDatabase.getInstance()
        leaderboardContainer = findViewById(R.id.leaderboardContainer)

        setupUI()
        loadLeaderboard()
    }

    private fun setupUI() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btnGoToGoals).setOnClickListener {
            startActivity(Intent(this, CreateGoalActivity::class.java))
            finish()
        }
    }

    private fun loadLeaderboard() {
        val usersRef = database.reference.child("users")
        usersRef.orderByChild("balance").limitToLast(20).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                leaderboardContainer.removeAllViews()
                
                // Collect and sort by total saved gold in goals
                val userList = mutableListOf<LeaderboardEntry>()
                
                for (userSnap in snapshot.children) {
                    val name = userSnap.child("username").getValue(String::class.java) ?: "Adventurer"
                    val pic = userSnap.child("profilePic").getValue(String::class.java)
                    
                    var totalSaved = 0.0
                    val goals = userSnap.child("goals")
                    for (goal in goals.children) {
                        totalSaved += goal.child("savedGold").getValue(Double::class.java) ?: 0.0
                    }
                    
                    userList.add(LeaderboardEntry(name, totalSaved, pic))
                }
                
                // Sort descending by score
                userList.sortByDescending { it.score }
                
                userList.forEachIndexed { index, entry ->
                    addLeaderboardEntry(index + 1, entry)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addLeaderboardEntry(rank: Int, entry: LeaderboardEntry) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_leaderboard, leaderboardContainer, false)
        
        view.findViewById<TextView>(R.id.tvRank).text = "$rank."
        view.findViewById<TextView>(R.id.tvLeaderboardName).text = entry.name.uppercase()
        view.findViewById<TextView>(R.id.tvLeaderboardScore).text = "₱${entry.score.toInt()}"
        
        val avatar = view.findViewById<ImageView>(R.id.ivLeaderboardAvatar)
        if (!entry.pic.isNullOrEmpty()) {
            if (entry.pic.startsWith("http")) Glide.with(this).load(entry.pic).circleCrop().into(avatar)
            else {
                try {
                    val bytes = android.util.Base64.decode(entry.pic, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    Glide.with(this).load(bitmap).circleCrop().into(avatar)
                } catch (e: Exception) { avatar.setImageResource(R.drawable.logo_kitago_main) }
            }
        }
        
        leaderboardContainer.addView(view)
    }

    data class LeaderboardEntry(val name: String, val score: Double, val pic: String?)
}
