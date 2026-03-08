package com.example.kitago

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

@SuppressLint("SetTextI18n")
class CreateGoalActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private var selectedDeadline: String = ""
    private var selectedCollaboratorId: String? = null
    private var selectedCollaboratorName: String? = null
    private var myUsername: String = "ADVENTURER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_goal)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()

        val userId = firebaseAuth.currentUser?.uid ?: return
        firebaseDatabase.reference.child("users").child(userId).child("username").get().addOnSuccessListener {
            myUsername = it.getValue(String::class.java) ?: "ADVENTURER"
        }

        val etGoalName = findViewById<EditText>(R.id.etGoalName)
        val etTargetGold = findViewById<EditText>(R.id.etTargetGold)
        val btnSelectDate = findViewById<LinearLayout>(R.id.btnSelectDate)
        val tvSelectedDate = findViewById<TextView>(R.id.tvSelectedDate)
        val btnSelectCollaborator = findViewById<LinearLayout>(R.id.btnSelectCollaborator)
        val tvSelectedCollaborator = findViewById<TextView>(R.id.tvSelectedCollaborator)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnSelectDate.setOnClickListener {
            showDatePicker(tvSelectedDate)
        }

        btnSelectCollaborator.setOnClickListener {
            showCollaboratorPickerDialog(tvSelectedCollaborator)
        }

        findViewById<TextView>(R.id.btnCreateGoal).setOnClickListener {
            val name = etGoalName.text.toString().trim()
            val targetStr = etTargetGold.text.toString().trim()

            if (name.isNotEmpty() && targetStr.isNotEmpty() && selectedDeadline.isNotEmpty()) {
                val target = targetStr.toDoubleOrNull() ?: 0.0
                if (target <= 0) {
                    Toast.makeText(this, "TARGET MUST BE GREATER THAN 0!", Toast.LENGTH_SHORT).show()
                } else if (target > 1000000) {
                    Toast.makeText(this, "TARGET TOO LARGE! (MAX ₱1,000,000)", Toast.LENGTH_SHORT).show()
                } else {
                    saveGoalToFirebase(name, target)
                }
            } else {
                Toast.makeText(this, "COMPLETE ALL QUEST DETAILS!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDatePicker(tv: TextView) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            selectedDeadline = "$dayOfMonth/${month + 1}/$year"
            tv.text = selectedDeadline
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun showCollaboratorPickerDialog(tv: TextView) {
        val myId = firebaseAuth.currentUser?.uid ?: return
        val friendsRef = firebaseDatabase.reference.child("users").child(myId).child("friends")

        friendsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val friendIds = snapshot.children.mapNotNull { it.key }
                if (friendIds.isEmpty()) {
                    Toast.makeText(this@CreateGoalActivity, "NO FRIENDS TO INVITE!", Toast.LENGTH_SHORT).show()
                    return
                }

                val friendNames = mutableListOf<String>()
                val friendIdMap = mutableMapOf<String, String>()

                var loadedCount = 0
                for (fId in friendIds) {
                    firebaseDatabase.reference.child("users").child(fId).child("username").get().addOnSuccessListener {
                        val name = it.getValue(String::class.java) ?: "Unknown"
                        friendNames.add(name)
                        friendIdMap[name] = fId
                        loadedCount++

                        if (loadedCount == friendIds.size) {
                            showFriendsListDialog(friendNames, friendIdMap, tv)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showFriendsListDialog(names: List<String>, idMap: Map<String, String>, tv: TextView) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("CHOOSE COLLABORATOR")
        
        val namesArray = (listOf("NONE") + names).toTypedArray()
        builder.setItems(namesArray) { _, which ->
            if (namesArray[which] == "NONE") {
                selectedCollaboratorName = null
                selectedCollaboratorId = null
                tv.text = "INVITE FRIEND"
            } else {
                selectedCollaboratorName = namesArray[which]
                selectedCollaboratorId = idMap[selectedCollaboratorName]
                tv.text = "INVITING: ${selectedCollaboratorName?.uppercase()}"
            }
        }
        builder.setNegativeButton("CANCEL", null)
        builder.show()
    }

    private fun saveGoalToFirebase(name: String, target: Double) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        val db = firebaseDatabase.reference
        val goalId = db.child("goals").push().key ?: return

        val isCollab = selectedCollaboratorId != null
        val statuses = mutableMapOf<String, String>()
        val collabNames = mutableMapOf<String, String>()

        statuses[userId] = "ACCEPTED"
        collabNames[userId] = myUsername

        if (isCollab) {
            statuses[selectedCollaboratorId!!] = "PENDING"
            collabNames[selectedCollaboratorId!!] = selectedCollaboratorName!!
        }

        val newGoal = Goal(
            id = goalId,
            name = name,
            targetGold = target,
            savedGold = 0.0,
            deadline = selectedDeadline,
            isCollaborative = isCollab,
            creatorId = userId,
            creatorName = myUsername,
            collaboratorStatuses = statuses,
            collaboratorNames = collabNames,
            status = "ACTIVE"
        )

        val updates = hashMapOf<String, Any?>()
        updates["/goals/$goalId"] = newGoal
        updates["/users/$userId/goals/$goalId"] = "ACCEPTED"
        
        if (isCollab) {
            updates["/users/$selectedCollaboratorId/goals/$goalId"] = "PENDING"
        }

        db.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Send challenge request notification for collab goals
                if (isCollab && selectedCollaboratorId != null) {
                    DataManager.sendChallengeRequest(
                        goalId = goalId,
                        goalName = name,
                        targetGold = target,
                        creatorName = myUsername,
                        collaboratorId = selectedCollaboratorId!!
                    )
                }
                Toast.makeText(this, if (isCollab) "INVITATION SENT!" else "QUEST STARTED!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "FAILED: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
