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
                if (!snapshot.exists()) {
                    Toast.makeText(this@AdminActivity, "UNAUTHORIZED ACCESS!", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
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

        findViewById<TextView>(R.id.btnGameConfig).setOnClickListener { showGameConfigDialog() }

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

        view.setOnClickListener { showUserActionDialog(uid, name, userSnap) }

        usersContainer.addView(view)
    }

    private fun showUserActionDialog(uid: String, name: String, userSnap: DataSnapshot) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "MANAGE USER"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "SELECT ACTION FOR:\n${name.uppercase()}"

        val container = dialogView as LinearLayout
        
        val btnEdit = Button(this).apply {
            text = "PROMOS / EDIT STATS"
            setOnClickListener {
                dialog.dismiss()
                showEditUserStatsDialog(uid, name, userSnap)
            }
        }
        container.addView(btnEdit, 2)

        val btnDelete = Button(this).apply {
            text = "DELETE USER"
            setTextColor(getColor(R.color.hp_red))
            setOnClickListener {
                dialog.dismiss()
                showConfirmDeleteUserDialog(uid, name)
            }
        }
        container.addView(btnDelete, 3)

        dialogView.findViewById<TextView>(R.id.btnDialogOk).text = "VIEW DETAILS"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener {
            dialog.dismiss()
            showUserDetailDialog(uid, name, userSnap)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showEditUserStatsDialog(uid: String, name: String, userSnap: DataSnapshot) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "EDIT USER"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "MODIFYING: $name"

        val currentBalance = userSnap.child("balance").getValue(Double::class.java) ?: 0.0
        val currentLevel = userSnap.child("level").getValue(Int::class.java) ?: 1

        val editBalance = EditText(this).apply {
            hint = "Balance"
            setText(currentBalance.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundResource(R.drawable.bg_input)
        }
        val editLevel = EditText(this).apply {
            hint = "Level"
            setText(currentLevel.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setBackgroundResource(R.drawable.bg_input)
        }

        val container = dialogView as LinearLayout
        container.addView(editBalance, 2)
        container.addView(editLevel, 3)

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "APPLY"
            setOnClickListener {
                val newBal = editBalance.text.toString().toDoubleOrNull() ?: currentBalance
                val newLvl = editLevel.text.toString().toIntOrNull() ?: currentLevel
                
                val updates = hashMapOf<String, Any>(
                    "balance" to newBal,
                    "level" to newLvl
                )
                db.child("users").child(uid).updateChildren(updates).addOnSuccessListener {
                    Toast.makeText(this@AdminActivity, "USER UPDATED!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showConfirmDeleteUserDialog(uid: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("PURGE USER?")
            .setMessage("THIS WILL PERMANENTLY REMOVE $name.")
            .setPositiveButton("DELETE") { _, _ ->
                db.child("users").child(uid).removeValue().addOnSuccessListener {
                    db.child("usernames").orderByValue().equalTo(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) {
                            for (child in s.children) child.ref.removeValue()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                    Toast.makeText(this, "USER REMOVED", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
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

        val details = StringBuilder()
        details.appendLine("EMAIL: $email")
        details.appendLine("LEVEL: $level  XP: $xp")
        details.appendLine("BALANCE: ₱${String.format(Locale.getDefault(), "%.2f", balance)}")
        details.appendLine("WINS: $wins  STREAK: $streak")
        details.appendLine("TOTAL SAVED: ₱${totalSaved.toInt()}")
        details.appendLine("UID: $uid")

        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = details.toString()
        dialogView.findViewById<TextView>(R.id.btnDialogOk).text = "CLOSE"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showManageAdminsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "STAFF MANAGEMENT"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ENTER USERNAME TO PROMOTE/DEMOTE:"

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

        val btnRemove = Button(this).apply {
            text = "REMOVE ADMIN"
            setTextColor(getColor(R.color.hp_red))
            setOnClickListener {
                val username = input.text.toString().trim().lowercase().replace(" ", "_")
                if (username.isEmpty()) { Toast.makeText(this@AdminActivity, "ENTER USERNAME!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                removeAdmin(username, dialog)
            }
        }
        container.addView(btnRemove)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun addAdmin(username: String, dialog: AlertDialog) {
        db.child("usernames").child(username).get().addOnSuccessListener { snap ->
            if (!snap.exists()) { Toast.makeText(this, "USER NOT FOUND!", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            val targetUid = snap.value.toString()
            val adminData = hashMapOf<String, Any>("role" to "admin")
            db.child("admins").child(targetUid).setValue(adminData).addOnSuccessListener {
                Toast.makeText(this, "NEW ADMIN APPOINTED!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    private fun removeAdmin(username: String, dialog: AlertDialog) {
        db.child("usernames").child(username).get().addOnSuccessListener { snap ->
            if (!snap.exists()) { Toast.makeText(this, "USER NOT FOUND!", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            val targetUid = snap.value.toString()
            if (targetUid == FirebaseAuth.getInstance().currentUser?.uid) {
                Toast.makeText(this, "CANNOT DEMOTE YOURSELF!", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            db.child("admins").child(targetUid).removeValue().addOnSuccessListener {
                Toast.makeText(this, "ADMIN PRIVILEGES REVOKED!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    private fun showGameConfigDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "GAME CONFIG"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "GLOBAL SETTINGS:"

        val container = dialogView as LinearLayout

        db.child("game_config").get().addOnSuccessListener { snapshot ->
            val currentMax = snapshot.child("max_level").getValue(Int::class.java) ?: 50
            
            val editMaxLevel = EditText(this).apply {
                hint = "Max Level"
                setText(currentMax.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setBackgroundResource(R.drawable.bg_input)
            }
            container.addView(editMaxLevel, 2)

            dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
                text = "SAVE CONFIG"
                setOnClickListener {
                    val newMax = editMaxLevel.text.toString().toIntOrNull() ?: currentMax
                    db.child("game_config").child("max_level").setValue(newMax).addOnSuccessListener {
                        Toast.makeText(this@AdminActivity, "CONFIG UPDATED!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
