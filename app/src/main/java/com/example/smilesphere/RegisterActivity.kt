package com.example.smilesphere

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirm: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvGoLogin: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etName     = findViewById(R.id.etName)
        etEmail    = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirm  = findViewById(R.id.etConfirm)
        btnRegister = findViewById(R.id.btnRegister)
        tvGoLogin  = findViewById(R.id.tvGoLogin)

        btnRegister.setOnClickListener { registerUser() }

        tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val name  = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val pass  = etPassword.text.toString().trim()
        val conf  = etConfirm.text.toString().trim()

        if (name.isEmpty()) {
            etName.error = "Please enter your name"; return
        }
        if (email.isEmpty()) {
            etEmail.error = "Please enter your email"; return
        }
        if (pass.length < 6) {
            etPassword.error = "Password must be 6+ characters"; return
        }
        if (pass != conf) {
            etConfirm.error = "Passwords do not match"; return
        }

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                val user = hashMapOf(
                    "name"  to name,
                    "email" to email,
                    "role"  to "SDS Officer"
                )
                db.collection("users").document(uid).set(user)
                    .addOnSuccessListener {

                        FirebaseAuth.getInstance().signOut()

                        Toast.makeText(this,
                            "Account created! Please log in", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.message,
                    Toast.LENGTH_LONG).show()
            }
    }
}
