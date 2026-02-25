package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var userRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()
        
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        userRef = firebaseDatabase.reference.child("users").child(currentUser.uid)

        // 1. SHOW AUTH DATA IMMEDIATELY (Reduces "Adventurer" flicker)
        val tvUsername = findViewById<TextView>(R.id.tvUsername)
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
        
        tvUsername.text = (currentUser.displayName ?: "ADVENTURER").uppercase()
        loadProfileImage(currentUser.photoUrl?.toString(), ivAvatar)

        // 2. LISTEN FOR DATABASE UPDATES (Real balance, custom username)
        observeUserData()
        setupNavigation()

        findViewById<TextView>(R.id.tvBalance).setOnClickListener {
            showAddBalanceDialog()
        }

        findViewById<TextView>(R.id.tvViewAllGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
    }

    private fun observeUserData() {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val username = snapshot.child("username").getValue(String::class.java)
                val balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                val profilePicUrl = snapshot.child("profilePic").getValue(String::class.java)

                if (username != null) {
                    findViewById<TextView>(R.id.tvUsername).text = username.uppercase()
                }
                findViewById<TextView>(R.id.tvBalance).text = String.format("₱%.2f", balance)
                
                if (profilePicUrl != null) {
                    loadProfileImage(profilePicUrl, findViewById(R.id.ivAvatar))
                }

                DataManager.setBalance(this@DashboardActivity, balance)
            }

            override fun onCancelled(error: DatabaseError) {
                // Usually a permission issue in Firebase Rules
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

    private fun showAddBalanceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnOk = dialogView.findViewById<TextView>(R.id.btnDialogOk)

        title.text = "ADD TREASURE"
        message.text = "ENTER AMOUNT TO ADD TO YOUR VAULT:"
        btnOk.text = "DEPOSIT"

        val input = EditText(this).apply {
            hint = "0.00"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            typeface = resources.getFont(R.font.press_start_2p)
            textSize = 14f
            setPadding(40, 40, 40, 40)
        }
        
        val container = dialogView as android.widget.LinearLayout
        val index = container.indexOfChild(message)
        container.addView(input, index + 1)

        btnOk.setOnClickListener {
            val amountStr = input.text.toString()
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toDouble()
                updateFirebaseBalance(amount, true)
                dialog.dismiss()
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateFirebaseBalance(amount: Double, isIncome: Boolean) {
        userRef.child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentBalance = currentData.getValue(Double::class.java) ?: 0.0
                val newBalance = if (isIncome) currentBalance + amount else currentBalance - amount
                currentData.value = newBalance
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    Toast.makeText(this@DashboardActivity, "TREASURE UPDATED!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener { /* Already Home */ }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener {
            startActivity(Intent(this, ChallengesActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}
