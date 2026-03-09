package com.example.kitago

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream
import java.io.File

@SuppressLint("SetTextI18n")
@Suppress("DEPRECATION")
class ProfileActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userRef: DatabaseReference
    private lateinit var usernamesRef: DatabaseReference
    private var currentUsername: String? = null
    private var isViewerMode = false
    private var targetUserId: String? = null
    private var cropOutputUri: Uri? = null

    // Step 2: After crop completes, save the cropped image
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = cropOutputUri
            if (uri != null) {
                saveCroppedImage(uri)
            }
        } else {
            // Crop was cancelled
            Toast.makeText(this, "CROP CANCELLED", Toast.LENGTH_SHORT).show()
        }
    }

    // Step 1: After image is picked, launch the crop intent
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) {
                launchCropIntent(imageUri)
            }
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
            findViewById<LinearLayout>(R.id.accountSection).visibility = View.GONE

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
            findViewById<TextView>(R.id.btnSignOut).setOnClickListener { showSignOutDialog() }
            findViewById<TextView>(R.id.btnUnfriend).visibility = View.GONE
            findViewById<TextView>(R.id.btnChangeEmail).setOnClickListener { showChangeEmailDialog() }
            findViewById<TextView>(R.id.btnChangePassword).setOnClickListener { showChangePasswordDialog() }
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
                val totalSaved = snapshot.child("totalSavedGold").getValue(Double::class.java) ?: 0.0

                findViewById<TextView>(R.id.tvUsernameProfile).text = currentUsername!!.uppercase()
                findViewById<TextView>(R.id.tvEmailProfile).text = email
                findViewById<TextView>(R.id.tvLevel).text = "LEVEL $level"
                findViewById<TextView>(R.id.tvWins).text = "WINS: $wins"
                findViewById<TextView>(R.id.tvStreak).text = "\uD83D\uDD25 STREAK: $streak DAYS"
                findViewById<TextView>(R.id.tvTotalSaved).text = "TOTAL SAVED: ₱${totalSaved.toInt()}"

                val xpNeeded = DataManager.getXpNeededForLevel(level)
                val xpBar = findViewById<ProgressBar>(R.id.profileXpBar)
                xpBar.max = xpNeeded
                xpBar.progress = xp
                findViewById<TextView>(R.id.tvXpLabel).text = "$xp / $xpNeeded XP"

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
            setBackgroundResource(R.drawable.bg_button_orange)
            setOnClickListener { unfriendUser(); dialog.dismiss() }
        }
        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
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
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ENTER ADVENTURER NAME:"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "SEND"
            setOnClickListener {
                val name = input.text.toString().trim().lowercase().replace(" ", "_")
                if (name.isEmpty()) {
                    Toast.makeText(this@ProfileActivity, "ENTER A USERNAME!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                usernamesRef.child(name).get().addOnSuccessListener { s ->
                    if (s.exists()) {
                        val fId = s.value.toString()
                        if (fId == auth.currentUser!!.uid) {
                            Toast.makeText(this@ProfileActivity, "CAN'T ADD YOURSELF!", Toast.LENGTH_SHORT).show()
                        } else {
                            FirebaseDatabase.getInstance().reference.child("friend_requests").child(fId).child(auth.currentUser!!.uid).setValue(currentUsername)
                            Toast.makeText(this@ProfileActivity, "REQUEST SENT!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    } else { Toast.makeText(this@ProfileActivity, "NOT FOUND!", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted))
            setOnClickListener { dialog.dismiss() }
        }
        dialogView.addView(btnCancel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun loadProfileImage(data: String?, imageView: ImageView) {
        ImageUtils.loadProfileImage(this, data, imageView)
    }

    private fun loadBadges(badgesSnapshot: DataSnapshot) {
        val grid = findViewById<GridLayout>(R.id.badgeGrid)
        grid.removeAllViews()

        if (!badgesSnapshot.hasChildren()) {
            val emptyText = TextView(this).apply {
                text = "NO BADGES YET"
                typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
                textSize = 8f
                setTextColor(getColor(R.color.text_muted))
                gravity = android.view.Gravity.CENTER
                setPadding(16, 32, 16, 32)
                layoutParams = GridLayout.LayoutParams().apply {
                    columnSpec = GridLayout.spec(0, 3)
                    width = GridLayout.LayoutParams.MATCH_PARENT
                }
            }
            grid.addView(emptyText)
            return
        }

        for (badge in badgesSnapshot.children) {
            val badgeName = badge.getValue(String::class.java) ?: badge.key ?: "BADGE"
            val badgeView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(8, 8, 8, 8)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                setBackgroundResource(R.drawable.bg_panel)
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 8, 0, 4) }
                setImageResource(android.R.drawable.btn_star_big_on)
            }
            val label = TextView(this).apply {
                text = badgeName
                typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
                textSize = 6f
                setTextColor(getColor(R.color.text_dark))
                gravity = android.view.Gravity.CENTER
                setPadding(4, 0, 4, 8)
                maxLines = 2
            }
            badgeView.addView(icon)
            badgeView.addView(label)
            grid.addView(badgeView)
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
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ENTER NEW ADVENTURER NAME:"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).setOnClickListener {
            val n = input.text.toString().trim()
            if (n.isEmpty()) {
                Toast.makeText(this@ProfileActivity, "NAME CAN'T BE EMPTY!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (n.length > 30) {
                Toast.makeText(this@ProfileActivity, "NAME TOO LONG! (MAX 30)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (n != currentUsername) {
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
        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted))
            setOnClickListener { dialog.dismiss() }
        }
        dialogView.addView(btnCancel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun launchCropIntent(sourceUri: Uri) {
        try {
            val cropFile = File(cacheDir, "crop_output_${System.currentTimeMillis()}.jpg")
            cropOutputUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cropFile)

            val cropIntent = Intent("com.android.camera.action.CROP").apply {
                setDataAndType(sourceUri, "image/*")
                putExtra("crop", "true")
                putExtra("aspectX", 1)
                putExtra("aspectY", 1)
                putExtra("outputX", 400)
                putExtra("outputY", 400)
                putExtra("return-data", false)
                putExtra(MediaStore.EXTRA_OUTPUT, cropOutputUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            // Check if any app can handle the crop intent
            if (cropIntent.resolveActivity(packageManager) != null) {
                // Grant URI permissions to all apps that can handle crop
                val resInfoList = packageManager.queryIntentActivities(cropIntent, 0)
                for (resolveInfo in resInfoList) {
                    val pName = resolveInfo.activityInfo.packageName
                    grantUriPermission(pName, cropOutputUri!!, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    grantUriPermission(pName, sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                cropLauncher.launch(cropIntent)
            } else {
                // Fallback: crop manually in code if no crop app available
                fallbackCropAndSave(sourceUri)
            }
        } catch (e: Exception) {
            // Fallback on any error
            fallbackCropAndSave(sourceUri)
        }
    }

    private fun fallbackCropAndSave(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) {
                Toast.makeText(this, "FAILED TO LOAD IMAGE!", Toast.LENGTH_SHORT).show()
                return
            }
            // Center-crop to square
            val size = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - size) / 2
            val y = (bitmap.height - size) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
            val scaled = cropped.scale(400, 400, true)
            saveAndUploadBitmap(scaled)
        } catch (e: Exception) {
            Toast.makeText(this, "FAILED TO PROCESS IMAGE!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCroppedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) {
                Toast.makeText(this, "FAILED TO LOAD IMAGE!", Toast.LENGTH_SHORT).show()
                return
            }
            val scaled = bitmap.scale(400, 400, true)
            saveAndUploadBitmap(scaled)
        } catch (e: Exception) {
            Toast.makeText(this, "FAILED TO PROCESS IMAGE!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndUploadBitmap(bitmap: Bitmap) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
        userRef.child("profilePic").setValue(base64)
            .addOnSuccessListener { Toast.makeText(this, "AVATAR UPDATED!", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(this, "UPLOAD FAILED!", Toast.LENGTH_SHORT).show() }
    }

    private fun showChangeEmailDialog() {
        val user = auth.currentUser ?: return
        // Google users can't change email via password re-auth
        if (user.providerData.any { it.providerId == "google.com" } && user.providerData.none { it.providerId == "password" }) {
            Toast.makeText(this, "GOOGLE ACCOUNTS MANAGE EMAIL VIA GOOGLE!", Toast.LENGTH_LONG).show()
            return
        }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "CHANGE EMAIL"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ENTER CURRENT PASSWORD\nAND NEW EMAIL:"

        val inputPassword = EditText(this).apply {
            hint = "CURRENT PASSWORD"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p); textSize = 10f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        }
        val inputEmail = EditText(this).apply {
            hint = "NEW EMAIL"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p); textSize = 10f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        }
        val container = dialogView as LinearLayout
        container.addView(inputPassword, 2)
        container.addView(inputEmail, 3)

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "UPDATE"
            setOnClickListener {
                val pass = inputPassword.text.toString().trim()
                val newEmail = inputEmail.text.toString().trim()
                if (pass.isEmpty() || newEmail.isEmpty()) { Toast.makeText(this@ProfileActivity, "FILL ALL FIELDS!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) { Toast.makeText(this@ProfileActivity, "INVALID EMAIL!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

                val credential = EmailAuthProvider.getCredential(user.email!!, pass)
                user.reauthenticate(credential).addOnSuccessListener {
                    @Suppress("DEPRECATION")
                    user.verifyBeforeUpdateEmail(newEmail).addOnSuccessListener {
                        userRef.child("email").setValue(newEmail)
                        Toast.makeText(this@ProfileActivity, "VERIFICATION SENT TO NEW EMAIL!", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.addOnFailureListener { e -> Toast.makeText(this@ProfileActivity, "FAILED: ${e.message}", Toast.LENGTH_LONG).show() }
                }.addOnFailureListener { Toast.makeText(this@ProfileActivity, "WRONG PASSWORD!", Toast.LENGTH_SHORT).show() }
            }
        }
        val btnCancel = TextView(this).apply {
            text = "CANCEL"; typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
            textSize = 12f; gravity = android.view.Gravity.CENTER; setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted)); setOnClickListener { dialog.dismiss() }
        }
        container.addView(btnCancel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showChangePasswordDialog() {
        val user = auth.currentUser ?: return
        if (user.providerData.any { it.providerId == "google.com" } && user.providerData.none { it.providerId == "password" }) {
            Toast.makeText(this, "GOOGLE ACCOUNTS MANAGE PASSWORD VIA GOOGLE!", Toast.LENGTH_LONG).show()
            return
        }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "CHANGE PASSWORD"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ENTER CURRENT AND\nNEW PASSWORD:"

        val inputCurrent = EditText(this).apply {
            hint = "CURRENT PASSWORD"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p); textSize = 10f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        }
        val inputNew = EditText(this).apply {
            hint = "NEW PASSWORD"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p); textSize = 10f; setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        }
        val tvStrength = TextView(this).apply {
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p); textSize = 7f
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        }
        inputNew.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val p = s?.toString() ?: ""
                if (p.isEmpty()) { tvStrength.visibility = View.GONE; return }
                val r = PasswordValidator.validate(p)
                tvStrength.visibility = View.VISIBLE
                tvStrength.text = r.strength.label
                tvStrength.setTextColor(r.strength.color)
            }
        })

        val container = dialogView as LinearLayout
        container.addView(inputCurrent, 2)
        container.addView(inputNew, 3)
        container.addView(tvStrength, 4)

        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "UPDATE"
            setOnClickListener {
                val currentPass = inputCurrent.text.toString().trim()
                val newPass = inputNew.text.toString().trim()
                if (currentPass.isEmpty() || newPass.isEmpty()) { Toast.makeText(this@ProfileActivity, "FILL ALL FIELDS!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val result = PasswordValidator.validate(newPass)
                if (!result.isValid) { Toast.makeText(this@ProfileActivity, "NEEDS: ${result.errors.joinToString(", ")}", Toast.LENGTH_LONG).show(); return@setOnClickListener }

                val credential = EmailAuthProvider.getCredential(user.email!!, currentPass)
                user.reauthenticate(credential).addOnSuccessListener {
                    user.updatePassword(newPass).addOnSuccessListener {
                        Toast.makeText(this@ProfileActivity, "PASSWORD UPDATED!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }.addOnFailureListener { e -> Toast.makeText(this@ProfileActivity, "FAILED: ${e.message}", Toast.LENGTH_LONG).show() }
                }.addOnFailureListener { Toast.makeText(this@ProfileActivity, "WRONG CURRENT PASSWORD!", Toast.LENGTH_SHORT).show() }
            }
        }
        val btnCancel = TextView(this).apply {
            text = "CANCEL"; typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
            textSize = 12f; gravity = android.view.Gravity.CENTER; setPadding(0, 30, 0, 30)
            setTextColor(getColor(R.color.text_muted)); setOnClickListener { dialog.dismiss() }
        }
        container.addView(btnCancel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showSignOutDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "SIGN OUT"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "ARE YOU SURE YOU WANT TO SIGN OUT?"
        dialogView.findViewById<TextView>(R.id.btnDialogOk).apply {
            text = "SIGN OUT"
            setBackgroundResource(R.drawable.bg_button_orange)
            setOnClickListener { signOut(); dialog.dismiss() }
        }
        val btnCancel = TextView(this).apply {
            text = "CANCEL"
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.press_start_2p)
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
        findViewById<ImageButton>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<ImageButton>(R.id.navGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<ImageButton>(R.id.navAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<ImageButton>(R.id.navChallenges).setOnClickListener {
            startActivity(Intent(this, ChallengesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener { /* Already on profile */ }
    }
}
