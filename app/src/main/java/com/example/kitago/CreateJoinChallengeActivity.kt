package com.example.kitago

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class CreateJoinChallengeActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private var selectedDeadline: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_join_challenge)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()

        val etGoalName = findViewById<EditText>(R.id.etCollabGoalName)
        val etTargetGold = findViewById<EditText>(R.id.etCollabTargetGold)
        val etFriendUsername = findViewById<EditText>(R.id.etFriendUsername)
        val btnSelectDate = findViewById<LinearLayout>(R.id.btnSelectCollabDate)
        val tvSelectedDate = findViewById<TextView>(R.id.tvCollabDate)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnSelectDate.setOnClickListener {
            showDatePicker(tvSelectedDate)
        }

        findViewById<TextView>(R.id.btnCreateCollabGoal).setOnClickListener {
            val name = etGoalName.text.toString().trim()
            val targetStr = etTargetGold.text.toString().trim()
            val friendUsername = etFriendUsername.text.toString().trim()

            if (name.isNotEmpty() && targetStr.isNotEmpty() && selectedDeadline.isNotEmpty() && friendUsername.isNotEmpty()) {
                checkFriendAndCreate(name, targetStr.toDouble(), friendUsername)
            } else {
                Toast.makeText(this, "FILL ALL FIELDS!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDatePicker(tv: TextView) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            selectedDeadline = "$dayOfMonth/${month + 1}/$year"
            tv.text = selectedDeadline
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        datePickerDialog.show()
    }

    private fun checkFriendAndCreate(name: String, target: Double, friendUsername: String) {
        val cleanFriendName = friendUsername.lowercase().replace(" ", "_")
        firebaseDatabase.reference.child("usernames").child(cleanFriendName).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val friendId = snapshot.value.toString()
                if (friendId == firebaseAuth.currentUser?.uid) {
                    Toast.makeText(this, "CANNOT COLLAB WITH YOURSELF!", Toast.LENGTH_SHORT).show()
                } else {
                    createCollabGoal(name, target, friendId, friendUsername)
                }
            } else {
                Toast.makeText(this, "FRIEND NOT FOUND!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createCollabGoal(name: String, target: Double, friendId: String, friendName: String) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        val creatorName = firebaseAuth.currentUser?.displayName ?: "Owner"
        
        val goalsRef = firebaseDatabase.reference.child("users").child(userId).child("goals")
        val friendGoalsRef = firebaseDatabase.reference.child("users").child(friendId).child("goals")
        
        val goalId = goalsRef.push().key ?: return

        val newGoal = Goal(
            id = goalId,
            name = name,
            targetGold = target,
            savedGold = 0.0,
            deadline = selectedDeadline,
            isCollaborative = true,
            creatorId = userId,
            collaboratorId = friendId,
            collaboratorName = friendName,
            status = "ACTIVE"
        )

        // Save to both users
        val task1 = goalsRef.child(goalId).setValue(newGoal)
        val task2 = friendGoalsRef.child(goalId).setValue(newGoal.copy(collaboratorName = creatorName))

        task1.addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(this, "COLLAB QUEST STARTED!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
