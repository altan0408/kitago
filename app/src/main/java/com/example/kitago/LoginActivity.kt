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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.e("LoginActivity", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
            }
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

        val etIdentifier = findViewById<EditText>(R.id.etUsername) // Input can be Email OR Username
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnSignIn = findViewById<TextView>(R.id.btnSignIn)
        val ivGoogle = findViewById<ImageView>(R.id.ivGoogle)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnSignIn.setOnClickListener {
            val identifier = etIdentifier.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (identifier.isNotEmpty() && password.isNotEmpty()) {
                if (Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                    // It's an email, login directly
                    performLogin(identifier, password)
                } else {
                    // It's a username, lookup the email first
                    lookupEmailAndLogin(identifier, password)
                }
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        ivGoogle.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        findViewById<TextView>(R.id.tvRegisterNow).setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun performLogin(email: String, pass: String) {
        firebaseAuth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    navigateToDashboard()
                } else {
                    showGameDialog("LOGIN FAILED", task.exception?.message ?: "Invalid credentials.") {
                        findViewById<EditText>(R.id.etPassword).text.clear()
                    }
                }
            }
    }

    private fun lookupEmailAndLogin(username: String, pass: String) {
        val cleanName = username.lowercase().replace(" ", "_")
        FirebaseDatabase.getInstance().reference.child("usernames").child(cleanName).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val userId = snapshot.value.toString()
                    FirebaseDatabase.getInstance().reference.child("users").child(userId).child("email").get()
                        .addOnSuccessListener { emailSnap ->
                            val email = emailSnap.value.toString()
                            performLogin(email, pass)
                        }
                } else {
                    showGameDialog("LOGIN FAILED", "Username not found.") {}
                }
            }
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
                            userData["wins"] = 0
                            userData["streak"] = 0
                            
                            userRef.setValue(userData).addOnCompleteListener {
                                // Also store in usernames lookup
                                val cleanName = (user.displayName ?: "Adventurer").lowercase().replace(" ", "_")
                                FirebaseDatabase.getInstance().reference.child("usernames").child(cleanName).setValue(user.uid)
                                navigateToDashboard()
                            }
                        } else {
                            navigateToDashboard()
                        }
                    }
                } else {
                    Toast.makeText(this, "Google auth failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
