package com.example.kitago

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Bottom Nav
        findViewById<ImageButton>(R.id.bottomNav).apply {
            // Find specific buttons within the layout if needed, 
            // but usually we set listeners on the children
        }

        // Add Transaction FAB
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        // Navigation (Assuming first child is Home, second is Goals, etc.)
        // Since we didn't give IDs to all children in the XML, let's just use the ones we have.
    }
}
