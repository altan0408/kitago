package com.example.kitago

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.ResourcesCompat
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
            prefs.edit { putBoolean(KEY_MUSIC, isChecked) }
        }
        switchSfx.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(KEY_SFX, isChecked) }
        }

        // --- System Toggles ---
        val switchAlerts = findViewById<SwitchCompat>(R.id.switchAlerts)
        switchAlerts.isChecked = prefs.getBoolean(KEY_ALERTS, true)
        switchAlerts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(KEY_ALERTS, isChecked) }
        }

        // --- Language Selector ---
        val tvLanguage = findViewById<TextView>(R.id.tvLanguageValue)
        val currentLang = prefs.getString(KEY_LANGUAGE, "ENG") ?: "ENG"
        tvLanguage.text = currentLang

        tvLanguage.setOnClickListener {
            showLanguageDialog(tvLanguage, prefs)
        }

        // --- Reset Progress ---
        findViewById<TextView>(R.id.btnResetProgress).setOnClickListener {
            showResetProgressDialog()
        }
    }

    private fun showLanguageDialog(tvLanguage: TextView, prefs: android.content.SharedPreferences) {
        val languages = arrayOf("ENG", "FIL")
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "LANGUAGE"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "SELECT YOUR LANGUAGE"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).text = "CANCEL"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener { dialog.dismiss() }

        val container = dialogView as LinearLayout
        val btnOkIndex = container.indexOfChild(dialogView.findViewById(R.id.btnDialogOk))

        for (lang in languages) {
            val btn = TextView(this).apply {
                text = lang
                typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.press_start_2p)
                textSize = 12f
                setTextColor(getColor(R.color.text_dark))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 30, 0, 30)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                background = AppCompatResources.getDrawable(this@SettingsActivity, R.drawable.bg_button_gold)
                setOnClickListener {
                    prefs.edit { putString(KEY_LANGUAGE, lang) }
                    tvLanguage.text = lang
                    Toast.makeText(this@SettingsActivity, "LANGUAGE SET TO $lang", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            container.addView(btn, btnOkIndex)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showResetProgressDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "⚠️ RESET"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "THIS WILL RESET YOUR BALANCE, XP, LEVEL, WINS, AND STREAK.\n\nYOUR QUESTS AND FRIENDS WILL BE KEPT.\n\nARE YOU SURE?"

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "RESET"
            setBackgroundResource(R.drawable.bg_button_orange)
            setOnClickListener {
                performReset()
                dialog.dismiss()
            }
        }

        // Add a cancel button
        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.press_start_2p)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted))
            setOnClickListener { dialog.dismiss() }
        }
        (dialogView as LinearLayout).addView(btnCancel)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun performReset() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "NOT LOGGED IN!", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        val updates = hashMapOf<String, Any?>(
            "balance" to 0.0,
            "xp" to 0,
            "level" to 1,
            "wins" to 0,
            "streak" to 0,
            "totalSavedGold" to 0.0,
            "expense_totals" to null,
            "income_totals" to null,
            "goal_streaks" to null
        )
        userRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "PROGRESS RESET!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "RESET FAILED!", Toast.LENGTH_SHORT).show()
        }
    }
}
