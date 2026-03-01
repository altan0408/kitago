package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
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

class SignupActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("SignupActivity", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnSignUp = findViewById<TextView>(R.id.btnSignUp)
        val ivGoogle = findViewById<ImageView>(R.id.ivGoogleSignup)

        // BACK BUTTON
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // SIGN UP LOGIC
        btnSignUp.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (validateFields(username, email, password, confirmPassword)) {
                // Check if username is taken first
                val cleanName = username.lowercase().replace(" ", "_")
                firebaseDatabase.reference.child("usernames").child(cleanName).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        Toast.makeText(this, "USERNAME ALREADY TAKEN!", Toast.LENGTH_SHORT).show()
                    } else {
                        firebaseAuth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this) { task ->
                                if (task.isSuccessful) {
                                    val userId = firebaseAuth.currentUser?.uid ?: return@addOnCompleteListener
                                    saveUserToDatabase(userId, username, email, "")
                                } else {
                                    Toast.makeText(this, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                }
            }
        }

        // GOOGLE SIGN UP
        ivGoogle.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // LOGIN HERE LINK
        findViewById<TextView>(R.id.tvLoginHere).setOnClickListener {
            finish()
        }
    }

    private fun validateFields(user: String, email: String, pass: String, confirmPass: String): Boolean {
        if (user.isEmpty()) {
            Toast.makeText(this, "Username is required", Toast.LENGTH_SHORT).show()
            return false
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Valid email address is required", Toast.LENGTH_SHORT).show()
            return false
        }
        if (pass.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return false
        }
        if (pass != confirmPass) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    val userRef = firebaseDatabase.reference.child("users").child(user!!.uid)
                    
                    userRef.get().addOnSuccessListener { snapshot ->
                        if (!snapshot.exists()) {
                            // First time login - save profile
                            saveUserToDatabase(user.uid, user.displayName ?: "Adventurer", user.email ?: "", user.photoUrl?.toString() ?: "")
                        } else {
                            startActivity(Intent(this, DashboardActivity::class.java))
                            finish()
                        }
                    }
                } else {
                    Toast.makeText(this, "Google authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToDatabase(userId: String, username: String, email: String, profilePic: String) {
        val userRef = firebaseDatabase.reference.child("users").child(userId)
        val usernamesRef = firebaseDatabase.reference.child("usernames")
        
        val userData = HashMap<String, Any>()
        userData["username"] = username
        userData["email"] = email
        userData["profilePic"] = profilePic
        userData["balance"] = 0.0
        userData["level"] = 1
        userData["xp"] = 0
        userData["wins"] = 0
        userData["streak"] = 0
        
        val cleanName = username.lowercase().replace(" ", "_")
        
        // Save user data and update lookup index
        userRef.setValue(userData).addOnCompleteListener {
            usernamesRef.child(cleanName).setValue(userId).addOnCompleteListener {
                val intent = Intent(this, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}
