package com.example.smilesphere

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvGoRegister: TextView
    private lateinit var tvTogglePassword: TextView

    private val auth = FirebaseAuth.getInstance()
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-skip login if already signed in
        if (auth.currentUser != null) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail          = findViewById(R.id.etEmail)
        etPassword       = findViewById(R.id.etPassword)
        btnLogin         = findViewById(R.id.btnLogin)
        tvGoRegister     = findViewById(R.id.tvGoRegister)
        tvTogglePassword = findViewById(R.id.tvTogglePassword)

        tvTogglePassword.setOnClickListener { togglePasswordVisibility() }

        btnLogin.setOnClickListener { loginUser() }

        tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible

        etPassword.inputType = if (isPasswordVisible) {
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_CLASS_TEXT
        } else {
            InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
        }
        tvTogglePassword.text = if (isPasswordVisible) "◉" else "⊘"

        // keep cursor at the end after switching input type
        etPassword.setSelection(etPassword.text.length)
    }

    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val pass  = etPassword.text.toString().trim()

        if (email.isEmpty()) {
            etEmail.error = "Enter your email"; return
        }
        if (pass.isEmpty()) {
            etPassword.error = "Enter your password"; return
        }

        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this,
                    "Email or password is incorrect",
                    Toast.LENGTH_SHORT).show()
            }
    }
}