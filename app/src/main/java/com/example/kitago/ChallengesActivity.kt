package com.example.kitago

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@SuppressLint("SetTextI18n")
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
        loadChallengeRequests()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btnStartNewQuest).setOnClickListener {
            startActivity(Intent(this, CreateGoalActivity::class.java))
        }
    }

    // --- CHALLENGE REQUESTS SECTION ---

    private fun loadChallengeRequests() {
        val myId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = database.reference.child("challenge_requests").child(myId)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val layout = findViewById<LinearLayout>(R.id.challengeRequestsLayout)
                val container = findViewById<LinearLayout>(R.id.challengeRequestsContainer)
                container.removeAllViews()

                if (snapshot.hasChildren()) {
                    layout.visibility = View.VISIBLE
                    for (req in snapshot.children) {
                        val goalId = req.child("goalId").getValue(String::class.java) ?: req.key ?: continue
                        val goalName = req.child("goalName").getValue(String::class.java) ?: "Unknown Quest"
                        val creatorName = req.child("creatorName").getValue(String::class.java) ?: "Unknown"
                        val targetGold = req.child("targetGold").getValue(Double::class.java) ?: 0.0
                        addChallengeRequestToView(container, goalId, goalName, creatorName, targetGold)
                    }
                } else {
                    layout.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addChallengeRequestToView(container: LinearLayout, goalId: String, goalName: String, creatorName: String, targetGold: Double) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_challenge_request, container, false)
        view.findViewById<TextView>(R.id.tvChallengeGoalName).text = "⚔️ ${goalName.uppercase()}"
        view.findViewById<TextView>(R.id.tvChallengeCreator).text = "FROM: ${creatorName.uppercase()}"
        view.findViewById<TextView>(R.id.tvChallengeTarget).text = "TARGET: ₱${targetGold.toInt()}"

        view.findViewById<ImageButton>(R.id.btnAcceptChallenge).setOnClickListener {
            respondToChallengeRequest(goalId, true)
        }
        view.findViewById<ImageButton>(R.id.btnRejectChallenge).setOnClickListener {
            respondToChallengeRequest(goalId, false)
        }
        container.addView(view)
    }

    private fun respondToChallengeRequest(goalId: String, accepted: Boolean) {
        val status = if (accepted) "ACCEPTED" else "DECLINED"
        DataManager.respondToChallenge(goalId, null, status) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, if (accepted) "QUEST ACCEPTED!" else "QUEST DECLINED", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- LEADERBOARD SECTION ---

    private fun loadLeaderboard() {
        // Show loading state
        leaderboardContainer.removeAllViews()
        val loadingText = TextView(this).apply {
            text = "LOADING..."
            typeface = android.graphics.Typeface.create("@font/press_start_2p", android.graphics.Typeface.NORMAL)
            textSize = 8f
            setTextColor(getColor(R.color.text_muted))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }
        leaderboardContainer.addView(loadingText)

        val usersRef = database.reference.child("users")
        usersRef.orderByChild("totalSavedGold").limitToLast(20).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                leaderboardContainer.removeAllViews()
                val userList = mutableListOf<LeaderboardEntry>()

                for (userSnap in snapshot.children) {
                    val name = userSnap.child("username").getValue(String::class.java) ?: "Adventurer"
                    val pic = userSnap.child("profilePic").getValue(String::class.java)
                    val totalSaved = userSnap.child("totalSavedGold").getValue(Double::class.java) ?: 0.0
                    val level = userSnap.child("level").getValue(Int::class.java) ?: 1

                    userList.add(LeaderboardEntry(name, totalSaved, pic, level))
                }

                // Sort descending by score
                userList.sortByDescending { it.score }

                if (userList.isEmpty() || userList.all { it.score == 0.0 }) {
                    val emptyText = TextView(this@ChallengesActivity).apply {
                        text = "NO ADVENTURERS ON\nTHE BOARD YET!\n\nSAVE GOLD TO RANK UP."
                        typeface = androidx.core.content.res.ResourcesCompat.getFont(this@ChallengesActivity, R.font.press_start_2p)
                        textSize = 8f
                        setTextColor(getColor(R.color.text_muted))
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 40, 0, 40)
                        setLineSpacing(8f, 1.2f)
                    }
                    leaderboardContainer.addView(emptyText)
                    return
                }

                // Filter out users with 0 score for a cleaner leaderboard
                val filteredList = userList.filter { it.score > 0 }
                filteredList.forEachIndexed { index, entry ->
                    addLeaderboardEntry(index + 1, entry)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addLeaderboardEntry(rank: Int, entry: LeaderboardEntry) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_leaderboard, leaderboardContainer, false)

        val tvRank = view.findViewById<TextView>(R.id.tvRank)
        // Medal emoji for top 3
        tvRank.text = when (rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "$rank."
        }
        view.findViewById<TextView>(R.id.tvLeaderboardName).text = entry.name.uppercase()
        view.findViewById<TextView>(R.id.tvLeaderboardScore).text = "₱${entry.score.toInt()}"

        val avatar = view.findViewById<ImageView>(R.id.ivLeaderboardAvatar)
        loadProfileImage(entry.pic, avatar)

        leaderboardContainer.addView(view)
    }

    private fun loadProfileImage(data: String?, imageView: ImageView) {
        ImageUtils.loadProfileImage(this, data, imageView)
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener { /* Already on challenges */ }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    data class LeaderboardEntry(val name: String, val score: Double, val pic: String?, val level: Int)
}
