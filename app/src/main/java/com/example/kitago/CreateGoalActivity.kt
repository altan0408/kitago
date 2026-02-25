package com.example.kitago

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class CreateGoalActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private var selectedDeadline: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_goal)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()

        val etGoalName = findViewById<EditText>(R.id.etGoalName)
        val etTargetGold = findViewById<EditText>(R.id.etTargetGold)
        val btnSelectDate = findViewById<LinearLayout>(R.id.btnSelectDate)
        val tvSelectedDate = btnSelectDate.findViewById<TextView>(android.R.id.text1) // Assuming there's a TextView inside

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        findViewById<TextView>(R.id.btnCreateGoal).setOnClickListener {
            val name = etGoalName.text.toString().trim()
            val targetStr = etTargetGold.text.toString().trim()

            if (name.isNotEmpty() && targetStr.isNotEmpty() && selectedDeadline.isNotEmpty()) {
                saveGoalToFirebase(name, targetStr.toDouble())
            } else {
                Toast.makeText(this, "COMPLETE ALL QUEST DETAILS!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            selectedDeadline = "$dayOfMonth/${month + 1}/$year"
            Toast.makeText(this, "DEADLINE SET: $selectedDeadline", Toast.LENGTH_SHORT).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        
        datePickerDialog.show()
    }

    private fun saveGoalToFirebase(name: String, target: Double) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        val goalsRef = firebaseDatabase.reference.child("users").child(userId).child("goals")
        val goalId = goalsRef.push().key ?: return

        val newGoal = Goal(goalId, name, target, 0.0, selectedDeadline)

        goalsRef.child(goalId).setValue(newGoal).addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(this, "NEW QUEST STARTED!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
