package com.example.kitago

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream

class ProfileActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRef: DatabaseReference
    private lateinit var usernamesRef: DatabaseReference
    private var currentUsername: String? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) {
                processAndSaveImage(imageUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            goToLogin()
            return
        }

        userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)
        usernamesRef = FirebaseDatabase.getInstance().reference.child("usernames")

        loadUserProfile()
        setupNavigation()

        findViewById<ImageButton>(R.id.btnEditUsername).setOnClickListener {
            showEditUsernameDialog()
        }

        findViewById<ImageView>(R.id.ivLargeAvatar).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        findViewById<TextView>(R.id.btnSignOut).setOnClickListener {
            auth.signOut()
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
                Toast.makeText(this, "LOGGED OUT", Toast.LENGTH_SHORT).show()
                goToLogin()
            }
        }
    }

    private fun loadUserProfile() {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                currentUsername = snapshot.child("username").getValue(String::class.java)
                val email = snapshot.child("email").getValue(String::class.java) ?: auth.currentUser?.email
                val profilePicData = snapshot.child("profilePic").getValue(String::class.java)

                findViewById<TextView>(R.id.tvUsernameProfile).text = (currentUsername ?: "ADVENTURER").uppercase()
                findViewById<TextView>(R.id.tvEmailProfile).text = email
                
                val wins = snapshot.child("wins").getValue(Int::class.java) ?: 0
                val streak = snapshot.child("streak").getValue(Int::class.java) ?: 0
                findViewById<TextView>(R.id.tvWins).text = "WINS: $wins"
                findViewById<TextView>(R.id.tvStreak).text = "STREAK: $streak DAYS"

                loadProfileImage(profilePicData ?: auth.currentUser?.photoUrl?.toString(), findViewById(R.id.ivLargeAvatar))
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun processAndSaveImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)

            userRef.child("profilePic").setValue(base64Image).addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "PICTURE UPDATED!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "IMAGE ERROR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditUsernameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnOk = dialogView.findViewById<TextView>(R.id.btnDialogOk)

        title.text = "CHANGE NAME"
        message.text = "ENTER NEW UNIQUE USERNAME:"
        btnOk.text = "SAVE"

        val input = EditText(this).apply {
            setText(currentUsername)
            typeface = resources.getFont(R.font.press_start_2p)
            textSize = 12f
            setPadding(40, 40, 40, 40)
        }
        
        val container = dialogView as android.widget.LinearLayout
        val index = container.indexOfChild(message)
        container.addView(input, index + 1)

        btnOk.setOnClickListener {
            val newUsername = input.text.toString().trim()
            if (newUsername.isNotEmpty() && newUsername != currentUsername) {
                checkUsernameAndSave(newUsername, dialog)
            } else {
                dialog.dismiss()
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun checkUsernameAndSave(newUsername: String, dialog: AlertDialog) {
        val cleanName = newUsername.lowercase().replace(" ", "_")
        usernamesRef.child(cleanName).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists() && snapshot.value != auth.currentUser!!.uid) {
                Toast.makeText(this, "USERNAME ALREADY TAKEN!", Toast.LENGTH_SHORT).show()
            } else {
                val userId = auth.currentUser!!.uid
                currentUsername?.let { old ->
                    usernamesRef.child(old.lowercase().replace(" ", "_")).removeValue()
                }
                usernamesRef.child(cleanName).setValue(userId)
                userRef.child("username").setValue(newUsername).addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "USERNAME UPDATED!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    private fun loadProfileImage(data: String?, imageView: ImageView) {
        if (data == null) return
        if (data.startsWith("http")) {
            Glide.with(this).load(data).circleCrop().placeholder(R.drawable.logo_kitago_main).into(imageView)
        } else {
            try {
                val decodedString = Base64.decode(data, Base64.DEFAULT)
                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                Glide.with(this).load(decodedByte).circleCrop().into(imageView)
            } catch (e: Exception) {
                imageView.setImageResource(R.drawable.logo_kitago_main)
            }
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener { startActivity(Intent(this, DashboardActivity::class.java)) }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener { startActivity(Intent(this, GoalsActivity::class.java)) }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener { startActivity(Intent(this, AddTransactionActivity::class.java)) }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener { startActivity(Intent(this, ChallengesActivity::class.java)) }
    }
}
