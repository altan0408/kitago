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
        findViewById<TextView>(R.id.btnGlobalPromo).setOnClickListener { showGlobalPromoDialog() }

        loadUsers()
    }

    private fun loadUsers() {
        findViewById<TextView>(R.id.tvUserCount).text = "LOADING USERS..."

        db.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersContainer.removeAllViews()
                val count = snapshot.childrenCount
                findViewById<TextView>(R.id.tvUserCount).text = "USERS: $count"

                if (count == 0L) {
                    val emptyMsg = TextView(this@AdminActivity).apply {
                        text = "NO USERS FOUND"
                        typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
                        textSize = 10f
                        setTextColor(getColor(R.color.text_muted))
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 60, 0, 60)
                    }
                    usersContainer.addView(emptyMsg)
                    return
                }

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
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("AdminActivity", "loadUsers FAILED: ${error.message} | code: ${error.code}")
                findViewById<TextView>(R.id.tvUserCount).text = "USERS: ERROR"
                usersContainer.removeAllViews()
                val errorMsg = TextView(this@AdminActivity).apply {
                    text = "⚠️ PERMISSION DENIED\n\nDEPLOY DATABASE RULES\nTO FIREBASE CONSOLE"
                    typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
                    textSize = 8f
                    setTextColor(getColor(R.color.hp_red))
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 60, 0, 60)
                }
                usersContainer.addView(errorMsg)
                Toast.makeText(this@AdminActivity, "FAILED TO LOAD USERS: ${error.message}", Toast.LENGTH_LONG).show()
            }
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

    private fun createThemedButton(label: String, bgRes: Int = R.drawable.bg_button_teal, textColor: Int = android.R.color.black): TextView {
        return TextView(this).apply {
            text = label
            typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
            textSize = 8f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 0, 32, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 96
            ).apply { topMargin = 16 }
            setBackgroundResource(bgRes)
            setTextColor(getColor(textColor))
            isClickable = true
            isFocusable = true
        }
    }

    private fun showUserActionDialog(uid: String, name: String, userSnap: DataSnapshot) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "MANAGE USER"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "SELECT ACTION FOR:\n${name.uppercase()}"

        val container = dialogView as LinearLayout
        
        val btnEdit = createThemedButton("EDIT STATS", R.drawable.bg_button_teal)
        btnEdit.setOnClickListener { dialog.dismiss(); showEditUserStatsDialog(uid, name, userSnap) }
        container.addView(btnEdit, 2)

        val btnDelete = createThemedButton("DELETE USER", R.drawable.bg_button_orange, R.color.hp_red)
        btnDelete.setOnClickListener { dialog.dismiss(); showConfirmDeleteUserDialog(uid, name) }
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
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "☠️ PURGE USER?"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "THIS WILL PERMANENTLY\nREMOVE ${name.uppercase()}.\n\nTHIS CANNOT BE UNDONE!"

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "DELETE"
            setBackgroundResource(R.drawable.bg_button_orange)
            setOnClickListener {
                db.child("users").child(uid).removeValue().addOnSuccessListener {
                    db.child("usernames").orderByValue().equalTo(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) {
                            for (child in s.children) child.ref.removeValue()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                    Toast.makeText(this@AdminActivity, "USER REMOVED", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }

        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
            textSize = 12f; gravity = android.view.Gravity.CENTER; setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted)); setOnClickListener { dialog.dismiss() }
        }
        (dialogView as LinearLayout).addView(btnCancel)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
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

        val btnRemove = createThemedButton("REMOVE ADMIN", R.drawable.bg_button_orange, R.color.hp_red)
        btnRemove.setOnClickListener {
            val username = input.text.toString().trim().lowercase().replace(" ", "_")
            if (username.isEmpty()) { Toast.makeText(this@AdminActivity, "ENTER USERNAME!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            removeAdmin(username, dialog)
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

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "⚙️ GAME CONFIG"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "GLOBAL SETTINGS:"

        val container = dialogView as LinearLayout

        db.child("game_config").get().addOnSuccessListener { snapshot ->
            val currentMax = snapshot.child("max_level").getValue(Int::class.java) ?: 50
            val currentXpDeposit = snapshot.child("xp_per_deposit").getValue(Int::class.java) ?: 50
            val currentXpContrib = snapshot.child("xp_per_contribution").getValue(Int::class.java) ?: 100
            val currentXpGoal = snapshot.child("xp_goal_completed").getValue(Int::class.java) ?: 1500

            fun makeInput(hint: String, value: String): EditText {
                return EditText(this).apply {
                    this.hint = hint; setText(value)
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
                    textSize = 9f; setPadding(40, 30, 40, 30)
                    setBackgroundResource(R.drawable.bg_input)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 12 }
                }
            }

            val editMaxLevel = makeInput("MAX LEVEL", currentMax.toString())
            val editXpDeposit = makeInput("XP PER DEPOSIT", currentXpDeposit.toString())
            val editXpContrib = makeInput("XP PER CONTRIBUTION", currentXpContrib.toString())
            val editXpGoal = makeInput("XP GOAL COMPLETED", currentXpGoal.toString())

            container.addView(editMaxLevel, 2)
            container.addView(editXpDeposit, 3)
            container.addView(editXpContrib, 4)
            container.addView(editXpGoal, 5)

            dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
                text = "SAVE CONFIG"
                setOnClickListener {
                    val updates = hashMapOf<String, Any>(
                        "max_level" to (editMaxLevel.text.toString().toIntOrNull() ?: currentMax),
                        "xp_per_deposit" to (editXpDeposit.text.toString().toIntOrNull() ?: currentXpDeposit),
                        "xp_per_contribution" to (editXpContrib.text.toString().toIntOrNull() ?: currentXpContrib),
                        "xp_goal_completed" to (editXpGoal.text.toString().toIntOrNull() ?: currentXpGoal)
                    )
                    db.child("game_config").updateChildren(updates).addOnSuccessListener {
                        Toast.makeText(this@AdminActivity, "CONFIG UPDATED!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showGlobalPromoDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "🎁 GLOBAL PROMO"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "GRANT BONUS GOLD\nTO ALL USERS:"

        val container = dialogView as LinearLayout

        val editAmount = EditText(this).apply {
            hint = "BONUS AMOUNT"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
            textSize = 10f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        val editReason = EditText(this).apply {
            hint = "REASON (OPTIONAL)"
            typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
            textSize = 9f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }

        container.addView(editAmount, 2)
        container.addView(editReason, 3)

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "APPLY TO ALL"
            setBackgroundResource(R.drawable.bg_button_orange)
            setOnClickListener {
                val amount = editAmount.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this@AdminActivity, "ENTER VALID AMOUNT!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val reason = editReason.text.toString().trim().ifEmpty { "ADMIN PROMO" }
                applyGlobalPromo(amount, reason, dialog)
            }
        }

        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@AdminActivity, R.font.press_start_2p)
            textSize = 12f; gravity = android.view.Gravity.CENTER; setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted)); setOnClickListener { dialog.dismiss() }
        }
        container.addView(btnCancel)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun applyGlobalPromo(amount: Double, reason: String, dialog: AlertDialog) {
        db.child("users").get().addOnSuccessListener { snapshot ->
            var count = 0
            for (userSnap in snapshot.children) {
                val uid = userSnap.key ?: continue
                val currentBalance = userSnap.child("balance").getValue(Double::class.java) ?: 0.0
                db.child("users").child(uid).child("balance").setValue(currentBalance + amount)
                count++
            }
            // Log promo history
            val promoEntry = hashMapOf<String, Any>(
                "amount" to amount,
                "reason" to reason,
                "appliedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"),
                "usersAffected" to count,
                "timestamp" to ServerValue.TIMESTAMP
            )
            db.child("game_config").child("promo_history").push().setValue(promoEntry)

            Toast.makeText(this, "₱$amount GRANTED TO $count USERS!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }.addOnFailureListener {
            Toast.makeText(this, "PROMO FAILED!", Toast.LENGTH_SHORT).show()
        }
    }
}
