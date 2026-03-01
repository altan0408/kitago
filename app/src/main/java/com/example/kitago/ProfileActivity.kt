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
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
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
    private var isViewerMode = false
    private var targetUserId: String? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) { processAndSaveImage(imageUri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        targetUserId = intent.getStringExtra("TARGET_USER_ID")
        isViewerMode = targetUserId != null && targetUserId != auth.currentUser?.uid

        if (auth.currentUser == null && !isViewerMode) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val effectiveId = targetUserId ?: auth.currentUser!!.uid
        userRef = FirebaseDatabase.getInstance().reference.child("users").child(effectiveId)
        usernamesRef = FirebaseDatabase.getInstance().reference.child("usernames")

        setupUI()
        loadUserProfile()
        setupNavigation()
        
        if (!isViewerMode) { listenForFriendRequests() }
    }

    private fun setupUI() {
        // Back Button
        findViewById<ImageButton>(R.id.btnBackProfile).setOnClickListener { finish() }

        if (isViewerMode) {
            findViewById<ImageButton>(R.id.btnEditUsername).visibility = View.GONE
            findViewById<TextView>(R.id.btnSignOut).visibility = View.GONE
            findViewById<ImageButton>(R.id.btnAddFriend).visibility = View.GONE
            findViewById<LinearLayout>(R.id.requestsLayout).visibility = View.GONE
            findViewById<ImageView>(R.id.ivCameraIcon).visibility = View.GONE
            
            val btnUnfriend = findViewById<TextView>(R.id.btnUnfriend)
            btnUnfriend.visibility = View.VISIBLE
            btnUnfriend.setOnClickListener { showUnfriendDialog() }
        } else {
            findViewById<ImageButton>(R.id.btnEditUsername).setOnClickListener { showEditUsernameDialog() }
            findViewById<ImageView>(R.id.ivLargeAvatar).setOnClickListener {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                imagePickerLauncher.launch(intent)
            }
            findViewById<ImageButton>(R.id.btnAddFriend).setOnClickListener { showAddFriendDialog() }
            findViewById<TextView>(R.id.btnSignOut).setOnClickListener { signOut() }
            findViewById<TextView>(R.id.btnUnfriend).visibility = View.GONE
        }
    }

    private fun loadUserProfile() {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                currentUsername = snapshot.child("username").getValue(String::class.java) ?: "ADVENTURER"
                val email = snapshot.child("email").getValue(String::class.java) ?: "---"
                val level = snapshot.child("level").getValue(Int::class.java) ?: 1
                val wins = snapshot.child("wins").getValue(Int::class.java) ?: 0
                val streak = snapshot.child("streak").getValue(Int::class.java) ?: 0
                val xp = snapshot.child("xp").getValue(Int::class.java) ?: 0
                val pic = snapshot.child("profilePic").getValue(String::class.java)

                findViewById<TextView>(R.id.tvUsernameProfile).text = currentUsername!!.uppercase()
                findViewById<TextView>(R.id.tvEmailProfile).text = email
                findViewById<TextView>(R.id.tvLevel).text = "LEVEL $level"
                findViewById<TextView>(R.id.tvWins).text = "WINS: $wins"
                findViewById<TextView>(R.id.tvStreak).text = "STREAK: $streak DAYS"
                
                val xpBar = findViewById<ProgressBar>(R.id.profileXpBar)
                xpBar.max = level * 500
                xpBar.progress = xp

                loadProfileImage(pic, findViewById(R.id.ivLargeAvatar))
                loadFriends(snapshot.child("friends"))
                loadBadges(snapshot.child("badges"))
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadFriends(friendsSnapshot: DataSnapshot) {
        val container = findViewById<LinearLayout>(R.id.friendsContainer)
        container.removeAllViews()
        
        for (friendSnap in friendsSnapshot.children) {
            val fId = friendSnap.key ?: continue
            FirebaseDatabase.getInstance().reference.child("users").child(fId).get().addOnSuccessListener { s ->
                if (!s.exists()) return@addOnSuccessListener
                val name = s.child("username").getValue(String::class.java) ?: "Friend"
                val lvl = s.child("level").getValue(Int::class.java) ?: 1
                val pic = s.child("profilePic").getValue(String::class.java)
                
                addFriendToView(container, fId, name, lvl, pic)
            }
        }
    }

    private fun addFriendToView(container: LinearLayout, fId: String, name: String, lvl: Int, pic: String?) {
        // Prevent Duplicates: check if view with this ID tag already exists
        if (container.findViewWithTag<View>(fId) != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.item_friend, container, false)
        view.tag = fId
        view.findViewById<TextView>(R.id.tvFriendName).text = name.uppercase()
        view.findViewById<TextView>(R.id.tvFriendLevel).text = "LVL $lvl"
        loadProfileImage(pic, view.findViewById(R.id.ivFriendAvatar))
        
        view.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("TARGET_USER_ID", fId)
            startActivity(intent)
        }
        container.addView(view)
    }

    private fun showUnfriendDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "REMOVE PARTY"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "REMOVE $currentUsername?"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "UNFRIEND"
            setOnClickListener { unfriendUser(); dialog.dismiss() }
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun unfriendUser() {
        val myId = auth.currentUser!!.uid
        val fId = targetUserId ?: return
        val db = FirebaseDatabase.getInstance().reference
        val updates = hashMapOf<String, Any?>(
            "users/$myId/friends/$fId" to null,
            "users/$fId/friends/$myId" to null
        )
        db.updateChildren(updates).addOnSuccessListener { finish() }
    }

    private fun listenForFriendRequests() {
        val ref = FirebaseDatabase.getInstance().reference.child("friend_requests").child(auth.currentUser!!.uid)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val container = findViewById<LinearLayout>(R.id.requestsContainer)
                val layout = findViewById<LinearLayout>(R.id.requestsLayout)
                container.removeAllViews()
                if (snapshot.hasChildren()) {
                    layout.visibility = View.VISIBLE
                    for (req in snapshot.children) {
                        addRequestToView(container, req.key!!, req.value.toString())
                    }
                } else { layout.visibility = View.GONE }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addRequestToView(container: LinearLayout, sId: String, sName: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_friend_request, container, false)
        view.findViewById<TextView>(R.id.tvRequestName).text = sName.uppercase()
        view.findViewById<ImageButton>(R.id.btnAccept).setOnClickListener {
            val myId = auth.currentUser!!.uid
            val updates = hashMapOf<String, Any?>(
                "users/$myId/friends/$sId" to true,
                "users/$sId/friends/$myId" to true,
                "friend_requests/$myId/$sId" to null
            )
            FirebaseDatabase.getInstance().reference.updateChildren(updates)
        }
        view.findViewById<ImageButton>(R.id.btnReject).setOnClickListener {
            FirebaseDatabase.getInstance().reference.child("friend_requests").child(auth.currentUser!!.uid).child(sId).removeValue()
        }
        container.addView(view)
    }

    private fun showAddFriendDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        val input = EditText(this).apply {
            hint = "username"; setBackgroundResource(R.drawable.bg_input)
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
            textSize = 12f; setPadding(40, 40, 40, 40)
        }
        (dialogView as LinearLayout).addView(input, 2)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "ADD PARTY"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "SEND"
            setOnClickListener {
                val name = input.text.toString().trim().lowercase().replace(" ", "_")
                usernamesRef.child(name).get().addOnSuccessListener { s ->
                    if (s.exists()) {
                        val fId = s.value.toString()
                        FirebaseDatabase.getInstance().reference.child("friend_requests").child(fId).child(auth.currentUser!!.uid).setValue(currentUsername)
                        Toast.makeText(this@ProfileActivity, "REQUEST SENT!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else { Toast.makeText(this@ProfileActivity, "NOT FOUND!", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun loadProfileImage(data: String?, imageView: ImageView) {
        if (data == null) { imageView.setImageResource(R.drawable.logo_kitago_main); return }
        if (data.startsWith("http")) Glide.with(this).load(data).circleCrop().into(imageView)
        else {
            try {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                Glide.with(this).load(bitmap).circleCrop().into(imageView)
            } catch (e: Exception) { imageView.setImageResource(R.drawable.logo_kitago_main) }
        }
    }

    private fun loadBadges(badgesSnapshot: DataSnapshot) {
        val grid = findViewById<GridLayout>(R.id.badgeGrid)
        grid.removeAllViews()
        for (badge in badgesSnapshot.children) {
            val icon = ImageView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = 100; height = 100; setMargins(8, 8, 8, 8) }
                setImageResource(android.R.drawable.btn_star_big_on)
            }
            grid.addView(icon)
        }
    }

    private fun showEditUsernameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        val input = EditText(this).apply {
            setText(currentUsername); setBackgroundResource(R.drawable.bg_input)
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p); textSize = 12f; setPadding(40, 40, 40, 40)
        }
        (dialogView as LinearLayout).addView(input, 2)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "CHANGE NAME"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener {
            val n = input.text.toString().trim()
            if (n.isNotEmpty() && n != currentUsername) {
                val clean = n.lowercase().replace(" ", "_")
                usernamesRef.child(clean).get().addOnSuccessListener { s ->
                    if (s.exists() && s.value != auth.currentUser!!.uid) { Toast.makeText(this@ProfileActivity, "TAKEN!", Toast.LENGTH_SHORT).show() }
                    else {
                        currentUsername?.let { usernamesRef.child(it.lowercase().replace(" ", "_")).removeValue() }
                        usernamesRef.child(clean).setValue(auth.currentUser!!.uid)
                        userRef.child("username").setValue(n).addOnCompleteListener { dialog.dismiss() }
                    }
                }
            } else { dialog.dismiss() }
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun processAndSaveImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val scaled = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            userRef.child("profilePic").setValue(Base64.encodeToString(out.toByteArray(), Base64.DEFAULT))
        } catch (e: Exception) {}
    }

    private fun signOut() {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build()
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.navHome).setOnClickListener { startActivity(Intent(this, DashboardActivity::class.java)) }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener { startActivity(Intent(this, GoalsActivity::class.java)) }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener { startActivity(Intent(this, AddTransactionActivity::class.java)) }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener { startActivity(Intent(this, ChallengesActivity::class.java)) }
    }
}
