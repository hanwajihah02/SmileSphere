package com.example.smilesphere

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class EditLessonActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etBody: EditText
    private lateinit var etOrder: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: TextView

    private val db = FirebaseFirestore.getInstance()

    // The Firestore document ID passed from LearningActivity.
    // We need this to know WHICH document to update — without it
    // Firestore wouldn't know where to save the changes.
    private var docId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_lesson)

        etTitle  = findViewById(R.id.etTitle)
        etBody   = findViewById(R.id.etBody)
        etOrder  = findViewById(R.id.etOrder)
        btnSave  = findViewById(R.id.btnSave)
        btnBack  = findViewById(R.id.btnBack)

        // Read the lesson data passed from LearningActivity
        docId         = intent.getStringExtra("docId")  ?: ""
        val title     = intent.getStringExtra("title")  ?: ""
        val body      = intent.getStringExtra("body")   ?: ""
        val order     = intent.getIntExtra("order", 1)

        // Pre-fill the form with the current lesson values
        // so the officer can see what's there before editing
        etTitle.setText(title)
        etBody.setText(body)
        etOrder.setText(order.toString())

        btnBack.setOnClickListener {
            finish() // go back without saving
        }

        btnSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun saveChanges() {
        val newTitle = etTitle.text.toString().trim()
        val newBody  = etBody.text.toString().trim()
        val newOrder = etOrder.text.toString().trim().toIntOrNull()

        // Basic validation — don't save if fields are empty
        if (newTitle.isEmpty()) {
            etTitle.error = "Title cannot be empty"
            return
        }
        if (newBody.isEmpty()) {
            etBody.error = "Content cannot be empty"
            return
        }
        if (newOrder == null) {
            etOrder.error = "Order must be a number"
            return
        }

        // Disable the button while saving so the officer
        // can't accidentally tap it twice
        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        // Build the map of fields to update.
        // We only update these three fields — imageUrl is left untouched.
        val updates = mapOf(
            "title" to newTitle,
            "body"  to newBody,
            "order" to newOrder
        )

        // .update() changes only the specified fields in the document.
        // It does NOT delete other fields (like imageUrl) — safe to use.
        db.collection("lessons")
            .document(docId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Lesson updated successfully!", Toast.LENGTH_SHORT).show()
                finish() // go back to LearningActivity — onResume will refresh the list
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                btnSave.isEnabled = true
                btnSave.text = "Save Changes"
            }
    }
}