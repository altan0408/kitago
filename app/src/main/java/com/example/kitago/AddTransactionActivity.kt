package com.example.kitago

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class AddTransactionActivity : ComponentActivity() {
    private lateinit var userRef: DatabaseReference
    private var expenseTotals = mutableMapOf<String, Double>()
    private var incomeTotals = mutableMapOf<String, Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            finish()
            return
        }

        userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        observeTotals()
        setupCategoryButtons()
    }

    private fun observeTotals() {
        userRef.child("expense_totals").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                expenseTotals.clear()
                for (child in snapshot.children) {
                    val cat = child.key ?: continue
                    val total = child.getValue(Double::class.java) ?: 0.0
                    expenseTotals[cat] = total
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        userRef.child("income_totals").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                incomeTotals.clear()
                for (child in snapshot.children) {
                    val cat = child.key ?: continue
                    val total = child.getValue(Double::class.java) ?: 0.0
                    incomeTotals[cat] = total
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupCategoryButtons() {
        val expenseCategories = mapOf(
            R.id.btnCategoryFood to "FOOD",
            R.id.btnCategoryTravel to "TRAVEL",
            R.id.btnCategoryFun to "FUN",
            R.id.btnCategoryHealth to "HEALTH",
            R.id.btnCategoryStudy to "STUDY",
            R.id.btnCategoryShop to "SHOP"
        )

        expenseCategories.forEach { (id, category) ->
            findViewById<ImageButton>(id).setOnClickListener {
                showTransactionDialog(category, isIncome = false)
            }
        }

        findViewById<ImageButton>(R.id.btnCategoryBaon).setOnClickListener {
            showTransactionDialog("BAON", isIncome = true)
        }
    }

    private fun showTransactionDialog(category: String, isIncome: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_message, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnOk = dialogView.findViewById<TextView>(R.id.btnDialogOk)

        val currentTotal = if (isIncome) incomeTotals[category] ?: 0.0 else expenseTotals[category] ?: 0.0
        val typeLabel = if (isIncome) "TOTAL INCOME" else "TOTAL SPENDING"
        
        title.text = category
        message.text = "$typeLabel: ₱${String.format("%.2f", currentTotal)}\n\nENTER NEW AMOUNT:"
        btnOk.text = if (isIncome) "SAVE" else "SPEND"

        val input = EditText(this).apply {
            hint = "0.00"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            val customFont = ResourcesCompat.getFont(this@AddTransactionActivity, R.font.press_start_2p)
            typeface = customFont
            textSize = 14f
            setPadding(40, 40, 40, 40)
            setBackgroundResource(R.drawable.bg_input)
        }
        
        val container = dialogView as android.widget.LinearLayout
        val index = container.indexOfChild(message)
        container.addView(input, index + 1)

        btnOk.setOnClickListener {
            val amountStr = input.text.toString()
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toDouble()
                DataManager.syncAddTransaction(amount, category, "", isIncome) { success ->
                    if (success) {
                        val msg = if (isIncome) "GOLD ADDED!" else "GOLD SPENT!"
                        Toast.makeText(this, "$msg ($category)", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
