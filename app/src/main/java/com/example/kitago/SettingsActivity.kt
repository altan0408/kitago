package com.example.kitago

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

@SuppressLint("SetTextI18n")
class SettingsActivity : ComponentActivity() {

    companion object {
        const val PREFS_NAME = "kitago_settings"
        const val KEY_MUSIC = "music_enabled"
        const val KEY_SFX = "sfx_enabled"
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_LANGUAGE = "language"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // --- Audio Toggles ---
        val switchMusic = findViewById<SwitchCompat>(R.id.switchMusic)
        val switchSfx = findViewById<SwitchCompat>(R.id.switchSfx)
        switchMusic.isChecked = prefs.getBoolean(KEY_MUSIC, true)
        switchSfx.isChecked = prefs.getBoolean(KEY_SFX, true)
        switchMusic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_MUSIC, isChecked).apply()
            Toast.makeText(this, if (isChecked) "MUSIC ON" else "MUSIC OFF", Toast.LENGTH_SHORT).show()
        }
        switchSfx.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SFX, isChecked).apply()
            Toast.makeText(this, if (isChecked) "SFX ON" else "SFX OFF", Toast.LENGTH_SHORT).show()
        }

        // --- System Toggles ---
        val switchAlerts = findViewById<SwitchCompat>(R.id.switchAlerts)
        switchAlerts.isChecked = prefs.getBoolean(KEY_ALERTS, true)
        switchAlerts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ALERTS, isChecked).apply()
            Toast.makeText(this, if (isChecked) "ALERTS ON" else "ALERTS OFF", Toast.LENGTH_SHORT).show()
        }

        // --- Language ---
        val tvLanguage = findViewById<TextView>(R.id.tvLanguageValue)
        tvLanguage.text = prefs.getString(KEY_LANGUAGE, "ENG") ?: "ENG"
        tvLanguage.setOnClickListener { showLanguageDialog() }

        // --- Account Buttons ---
        findViewById<TextView>(R.id.btnSettingsChangeEmail).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<TextView>(R.id.btnSettingsChangePassword).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // --- Danger Zone ---
        findViewById<TextView>(R.id.btnResetProgress).setOnClickListener { showResetProgressDialog() }
        findViewById<TextView>(R.id.btnDeleteAccount).setOnClickListener { showDeleteAccountDialog() }
    }

    private fun showLanguageDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "LANGUAGE"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "MULTI-LANGUAGE SUPPORT\nCOMING SOON!\n\nSTAY TUNED, ADVENTURER."
        dialogView.findViewById<TextView>(R.id.btnDialogOk).text = "OK"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener { dialog.dismiss() }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showResetProgressDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "⚠️ RESET"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "THIS WILL RESET YOUR\nBALANCE, XP, LEVEL,\nWINS, STREAK, BADGES.\n\nQUESTS & FRIENDS KEPT.\n\nARE YOU SURE?"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "RESET"; setBackgroundResource(R.drawable.bg_button_orange)
            setOnClickListener { performReset(); dialog.dismiss() }
        }
        val btnCancel = TextView(this).apply {
            text = "CANCEL"; typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.press_start_2p)
            textSize = 12f; gravity = android.view.Gravity.CENTER; setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted)); setOnClickListener { dialog.dismiss() }
        }
        (dialogView as LinearLayout).addView(btnCancel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showDeleteAccountDialog() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val isGoogleOnly = user.providerData.any { it.providerId == "google.com" } && user.providerData.none { it.providerId == "password" }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "☠️ DELETE"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "THIS WILL PERMANENTLY\nDELETE YOUR ACCOUNT\nAND ALL DATA.\n\nTHIS CANNOT BE UNDONE!"

        val container = dialogView as LinearLayout

        if (!isGoogleOnly) {
            val inputPassword = EditText(this).apply {
                hint = "ENTER PASSWORD"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.press_start_2p)
                textSize = 10f; setPadding(40, 40, 40, 40); setBackgroundResource(R.drawable.bg_input)
            }
            container.addView(inputPassword, 2)

            dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
                text = "DELETE"; setBackgroundResource(R.drawable.bg_button_orange)
                setOnClickListener {
                    val pass = inputPassword.text.toString().trim()
                    if (pass.isEmpty()) { Toast.makeText(this@SettingsActivity, "ENTER PASSWORD!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    val credential = EmailAuthProvider.getCredential(user.email!!, pass)
                    user.reauthenticate(credential).addOnSuccessListener {
                        deleteAccountData(user.uid); dialog.dismiss()
                    }.addOnFailureListener { Toast.makeText(this@SettingsActivity, "WRONG PASSWORD!", Toast.LENGTH_SHORT).show() }
                }
            }
        } else {
            dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
                text = "DELETE"; setBackgroundResource(R.drawable.bg_button_orange)
                setOnClickListener { deleteAccountData(user.uid); dialog.dismiss() }
            }
        }

        val btnCancel = TextView(this).apply {
            text = "CANCEL"; typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.press_start_2p)
            textSize = 12f; gravity = android.view.Gravity.CENTER; setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted)); setOnClickListener { dialog.dismiss() }
        }
        container.addView(btnCancel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun deleteAccountData(uid: String) {
        val db = FirebaseDatabase.getInstance().reference
        db.child("users").child(uid).get().addOnSuccessListener { snapshot ->
            val username = snapshot.child("username").getValue(String::class.java)
            if (username != null) db.child("usernames").child(username.lowercase().replace(" ", "_")).removeValue()
            db.child("users").child(uid).removeValue()
            db.child("friend_requests").child(uid).removeValue()
            db.child("challenge_requests").child(uid).removeValue()

            FirebaseAuth.getInstance().currentUser?.delete()?.addOnCompleteListener {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build()
                GoogleSignIn.getClient(this, gso).signOut()
                Toast.makeText(this, "ACCOUNT DELETED", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent); finish()
            }
        }
    }

    private fun performReset() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { Toast.makeText(this, "NOT LOGGED IN!", Toast.LENGTH_SHORT).show(); return }
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        val updates = hashMapOf<String, Any?>(
            "balance" to 0.0, "xp" to 0, "level" to 1, "wins" to 0, "streak" to 0,
            "totalSavedGold" to 0.0, "expense_totals" to null, "income_totals" to null,
            "goal_streaks" to null, "badges" to null
        )
        userRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "PROGRESS RESET!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "RESET FAILED!", Toast.LENGTH_SHORT).show()
        }
    }
}
