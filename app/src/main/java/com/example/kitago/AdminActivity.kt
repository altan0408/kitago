package com.example.kitago

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Locale

@SuppressLint("SetTextI18n")
class AdminActivity : ComponentActivity() {

    private lateinit var db: DatabaseReference
    private lateinit var usersContainer: LinearLayout
    private var myRole: String = "admin"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        db = FirebaseDatabase.getInstance().reference
        usersContainer = findViewById(R.id.usersContainer)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }

        // Check admin role
        db.child("admins").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                myRole = snapshot.child("role").getValue(String::class.java) ?: "admin"
                findViewById<TextView>(R.id.tvAdminRole).text = myRole.uppercase().replace("_", " ")

                val btnManageAdmins = findViewById<TextView>(R.id.btnManageAdmins)
                if (myRole == "super_admin") {
                    btnManageAdmins.visibility = View.VISIBLE
                    btnManageAdmins.setOnClickListener { showManageAdminsDialog() }
                } else {
                    btnManageAdmins.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Load all users in realtime
        loadUsers()
    }

    private fun loadUsers() {
        db.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersContainer.removeAllViews()
                val count = snapshot.childrenCount
                findViewById<TextView>(R.id.tvUserCount).text = "USERS: $count"

                for (userSnap in snapshot.children) {
                    val uid = userSnap.key ?: continue
                    val name = userSnap.child("username").getValue(String::class.java) ?: "Unknown"
                    val email = userSnap.child("email").getValue(String::class.java) ?: "---"
                    val level = userSnap.child("level").getValue(Int::class.java) ?: 1
                    val balance = userSnap.child("balance").getValue(Double::class.java) ?: 0.0
                    val pic = userSnap.child("profilePic").getValue(String::class.java)

                    addUserRow(uid, name, email, level, balance, pic, userSnap)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addUserRow(uid: String, name: String, email: String, level: Int, balance: Double, pic: String?, userSnap: DataSnapshot) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_admin_user, usersContainer, false)

        view.findViewById<TextView>(R.id.tvUserName).text = name.uppercase()
        view.findViewById<TextView>(R.id.tvUserEmail).text = email
        view.findViewById<TextView>(R.id.tvUserLevel).text = "LVL $level"
        view.findViewById<TextView>(R.id.tvUserBalance).text = String.format(Locale.getDefault(), "₱%.2f", balance)

        val avatar = view.findViewById<ImageView>(R.id.ivUserAvatar)
        ImageUtils.loadProfileImage(this, pic, avatar)

        view.setOnClickListener { showUserDetailDialog(uid, name, userSnap) }

        usersContainer.addView(view)
    }

    private fun showUserDetailDialog(uid: String, name: String, userSnap: DataSnapshot) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = name.uppercase()

        val email = userSnap.child("email").getValue(String::class.java) ?: "---"
        val level = userSnap.child("level").getValue(Int::class.java) ?: 1
        val xp = userSnap.child("xp").getValue(Int::class.java) ?: 0
        val balance = userSnap.child("balance").getValue(Double::class.java) ?: 0.0
        val wins = userSnap.child("wins").getValue(Int::class.java) ?: 0
        val streak = userSnap.child("streak").getValue(Int::class.java) ?: 0
        val totalSaved = userSnap.child("totalSavedGold").getValue(Double::class.java) ?: 0.0
        val goalCount = userSnap.child("goals").childrenCount
        val friendCount = userSnap.child("friends").childrenCount
        val badgeCount = userSnap.child("badges").childrenCount

        val details = StringBuilder()
        details.appendLine("EMAIL: $email")
        details.appendLine("LEVEL: $level  XP: $xp")
        details.appendLine("BALANCE: ₱${String.format(Locale.getDefault(), "%.2f", balance)}")
        details.appendLine("WINS: $wins  STREAK: $streak")
        details.appendLine("TOTAL SAVED: ₱${totalSaved.toInt()}")
        details.appendLine("QUESTS: $goalCount")
        details.appendLine("FRIENDS: $friendCount")
        details.appendLine("BADGES: $badgeCount")
        details.appendLine("UID: ${uid.take(12)}...")

        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = details.toString()
        dialogView.findViewById<TextView>(R.id.btnDialogOk).text = "CLOSE"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showManageAdminsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "MANAGE ADMINS"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ENTER USERNAME TO\nADD/REMOVE AS ADMIN:"

        val input = EditText(this).apply {
            hint = "username"
            typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
            textSize = 10f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
        }
        val container = dialogView as LinearLayout
        container.addView(input, 2)

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "ADD ADMIN"
            setOnClickListener {
                val username = input.text.toString().trim().lowercase().replace(" ", "_")
                if (username.isEmpty()) { Toast.makeText(this@AdminActivity, "ENTER USERNAME!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                addAdmin(username, dialog)
            }
        }

        val btnRemove = TextView(this).apply {
            text = "REMOVE ADMIN"
            typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
            textSize = 10f; gravity = android.view.Gravity.CENTER; setPadding(0, 20, 0, 10)
            setTextColor(getColor(R.color.hp_red)); setBackgroundResource(R.drawable.bg_button_orange)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { setMargins(0, 16, 0, 0) }
            setOnClickListener {
                val username = input.text.toString().trim().lowercase().replace(" ", "_")
                if (username.isEmpty()) { Toast.makeText(this@AdminActivity, "ENTER USERNAME!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                removeAdmin(username, dialog)
            }
        }
        container.addView(btnRemove)

        val btnCancel = TextView(this).apply {
            text = "CANCEL"; typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
            textSize = 12f; gravity = android.view.Gravity.CENTER; setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted)); setOnClickListener { dialog.dismiss() }
        }
        container.addView(btnCancel)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun addAdmin(username: String, dialog: AlertDialog) {
        db.child("usernames").child(username).get().addOnSuccessListener { snap ->
            if (!snap.exists()) { Toast.makeText(this, "USER NOT FOUND!", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            val targetUid = snap.value.toString()
            db.child("users").child(targetUid).child("email").get().addOnSuccessListener { emailSnap ->
                val email = emailSnap.getValue(String::class.java) ?: ""
                val adminData = hashMapOf<String, Any>("role" to "admin", "email" to email)
                db.child("admins").child(targetUid).setValue(adminData).addOnSuccessListener {
                    Toast.makeText(this, "ADMIN ADDED!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
    }

    private fun removeAdmin(username: String, dialog: AlertDialog) {
        db.child("usernames").child(username).get().addOnSuccessListener { snap ->
            if (!snap.exists()) { Toast.makeText(this, "USER NOT FOUND!", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            val targetUid = snap.value.toString()
            // Don't allow removing self
            if (targetUid == FirebaseAuth.getInstance().currentUser?.uid) {
                Toast.makeText(this, "CAN'T REMOVE YOURSELF!", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            db.child("admins").child(targetUid).removeValue().addOnSuccessListener {
                Toast.makeText(this, "ADMIN REMOVED!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }
}

