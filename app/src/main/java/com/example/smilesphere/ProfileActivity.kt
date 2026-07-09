package com.example.smilesphere

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvSchool: TextView
    private lateinit var tvAvatarInitial: TextView
    private lateinit var btnLogout: Button

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvName          = findViewById(R.id.tvName)
        tvEmail         = findViewById(R.id.tvEmail)
        tvRole          = findViewById(R.id.tvRole)
        tvSchool        = findViewById(R.id.tvSchool)
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)
        btnLogout       = findViewById(R.id.btnLogout)

        // Status bar inset fix — this screen has no toolbar, so the
        // scroll root needs top padding to avoid drawing under the status bar
        val scrollRoot = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollRoot)
        ViewCompat.setOnApplyWindowInsetsListener(scrollRoot) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBarInsets.top)
            insets
        }

        loadProfile()

        btnLogout.setOnClickListener { showLogoutDialog() }
    }

    private fun loadProfile() {
        val uid = auth.currentUser!!.uid
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: "No name"
                tvName.text = name
                tvAvatarInitial.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "S"

                tvEmail.text = doc.getString("email") ?: auth.currentUser?.email ?: "—"

                val school = doc.getString("school")
                tvSchool.text = if (school.isNullOrBlank()) "Not assigned yet" else school

                val role = doc.getString("role")
                tvRole.text = if (role.isNullOrBlank()) "SDS Officer" else role
            }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sign out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Yes, sign out") { _, _ -> logoutUser() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}