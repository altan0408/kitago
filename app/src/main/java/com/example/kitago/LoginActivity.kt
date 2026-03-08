package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase

@Suppress("DEPRECATION")
class LoginActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google sign in failed, code=${e.statusCode}", e)
            Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        firebaseAuth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnSignIn = findViewById<TextView>(R.id.btnSignIn)
        val ivGoogle = findViewById<ImageView>(R.id.ivGoogle)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateLogin(email, password)) {
                firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            checkRoleAndNavigate()
                        } else {
                            showGameDialog("LOGIN FAILED", task.exception?.message ?: "Invalid credentials.") {
                                etPassword.text.clear()
                            }
                        }
                    }
            }
        }

        ivGoogle.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        findViewById<TextView>(R.id.tvRegisterNow).setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            showForgotPasswordDialog(etEmail.text.toString().trim())
        }
    }

    private fun checkRoleAndNavigate() {
        val user = firebaseAuth.currentUser ?: return
        val db = FirebaseDatabase.getInstance().reference
        
        // Load global config first
        DataManager.fetchGlobalConfig()

        db.child("admins").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // User is an ADMIN
                val intent = Intent(this, AdminActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                Toast.makeText(this, "ADMIN PORTAL ACCESS", Toast.LENGTH_SHORT).show()
            } else {
                // Regular USER
                ensureUsernameIsIndexed()
                val intent = Intent(this, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }.addOnFailureListener {
            // Fallback to Dashboard if check fails
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showForgotPasswordDialog(prefilledEmail: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "RESET PASSWORD"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ENTER YOUR EMAIL TO\nRECEIVE A RESET LINK:"

        val input = EditText(this).apply {
            hint = "email@example.com"
            setText(prefilledEmail)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            typeface = ResourcesCompat.getFont(this@LoginActivity, R.font.press_start_2p)
            textSize = 10f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
        }
        (dialogView as LinearLayout).addView(input, 2)

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "SEND"
            setOnClickListener {
                val email = input.text.toString().trim()
                if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this@LoginActivity, "ENTER A VALID EMAIL!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                firebaseAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        dialog.dismiss()
                        if (task.isSuccessful) {
                            showGameDialog("EMAIL SENT!", "CHECK YOUR INBOX.") {}
                        } else {
                            showGameDialog("FAILED", task.exception?.message ?: "ERROR.") {}
                        }
                    }
            }
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun validateLogin(email: String, pass: String): Boolean {
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
            return false
        }
        if (pass.isEmpty()) {
            Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser ?: return@addOnCompleteListener
                    val userRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
                    
                    userRef.get().addOnSuccessListener { snapshot ->
                        if (!snapshot.exists()) {
                            val userData = HashMap<String, Any>()
                            userData["username"] = user.displayName ?: "Adventurer"
                            userData["email"] = user.email ?: ""
                            userData["profilePic"] = user.photoUrl?.toString() ?: ""
                            userData["balance"] = 0.0
                            userData["level"] = 1
                            userData["xp"] = 0
                            userData["wins"] = 0
                            userData["streak"] = 0
                            userData["totalSavedGold"] = 0.0
                            userRef.setValue(userData).addOnCompleteListener { checkRoleAndNavigate() }
                        } else {
                            checkRoleAndNavigate()
                        }
                    }
                } else {
                    Toast.makeText(this, "Google auth failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun ensureUsernameIsIndexed() {
        val user = firebaseAuth.currentUser ?: return
        val db = FirebaseDatabase.getInstance().reference
        db.child("users").child(user.uid).child("username").get().addOnSuccessListener { snapshot ->
            val username = snapshot.getValue(String::class.java)
            if (username != null) {
                val cleanName = username.lowercase().replace(" ", "_")
                db.child("usernames").child(cleanName).setValue(user.uid)
            }
        }
    }

    private fun showGameDialog(title: String, message: String, onOk: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = message
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener {
            dialog.dismiss()
            onOk()
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
