package com.example.smilesphere

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LearningActivity : BaseActivity() {

    private lateinit var containerLessons: LinearLayout
    private lateinit var etSearch: EditText // NEW: search bar reference
    private val db = FirebaseFirestore.getInstance()
    private var school = ""
    private var date = ""
    private var sessionKey = ""
    private var openForAR = false

    // NEW: we store all lessons in memory so search can filter
    // without hitting Firestore every time the user types a letter
    private data class LessonItem(
        val docId: String,
        val title: String,
        val body: String,
        val order: Int
    )
    private var allLessons: List<LessonItem> = emptyList()

    private val icons  = listOf("\uD83E\uDDB7", "\uD83E\uDEA5", "\uD83E\uDDFC", "\uD83E\uDDB4", "\uD83D\uDCDA")
    private val colors = listOf(
        0xFF27B894.toInt(),
        0xFFFFB84D.toInt(),
        0xFFFF6F61.toInt(),
        0xFF3F8CFF.toInt(),
        0xFF8B7CFF.toInt()
    )

    override fun getLayoutResourceId() = R.layout.activity_learning

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        school     = intent.getStringExtra("school")     ?: ""
        date       = intent.getStringExtra("date")       ?: ""
        sessionKey = intent.getStringExtra("sessionKey") ?: ""
        openForAR  = intent.getBooleanExtra("openForAR", false)

        containerLessons = findViewById(R.id.containerLessons)
        etSearch         = findViewById(R.id.etSearch) // NEW

        // NEW: listen to what the user types in the search bar.
        // Every time the text changes, re-filter the lesson list.
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Called after every keystroke — filter and redraw
                filterAndDisplay(s.toString())
            }
        })

        loadLessons()
    }

    // NEW: called when this activity comes back into view
    // (e.g. after the officer saved an edit in EditLessonActivity).
    // Without this, the list wouldn't refresh after an edit.
    override fun onResume() {
        super.onResume()
        loadLessons()
    }

    private fun loadLessons() {
        db.collection("lessons")
            .orderBy("order", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("Firestore", "Listen failed.", error)
                    return@addSnapshotListener
                }

                // NEW: store all lessons in memory
                allLessons = snapshots?.map { doc ->
                    LessonItem(
                        docId = doc.id,
                        title = doc.getString("title") ?: "Untitled Lesson",
                        body  = doc.getString("body")  ?: "",
                        order = doc.getLong("order")?.toInt() ?: 1
                    )
                } ?: emptyList()

                // NEW: apply whatever is currently in the search bar
                // (handles the case where user typed something, then
                // an edit saved and the list reloaded)
                filterAndDisplay(etSearch.text.toString())
            }
    }

    // NEW: filters allLessons by the search query, then redraws the cards.
    // If query is blank, shows everything.
    // Search is case-insensitive and checks if the title CONTAINS the query.
    private fun filterAndDisplay(query: String) {
        val filtered = if (query.isBlank()) {
            allLessons
        } else {
            allLessons.filter { lesson ->
                lesson.title.contains(query, ignoreCase = true)
            }
        }

        containerLessons.removeAllViews()

        if (filtered.isEmpty()) {
            // NEW: show a "no results" message if nothing matches
            val tvEmpty = TextView(this).apply {
                text = "No lessons found for \"$query\""
                textSize = 14f
                setTextColor(Color.parseColor("#668096"))
                gravity = Gravity.CENTER
                setPadding(0, 60, 0, 0)
            }
            containerLessons.addView(tvEmpty)
            return
        }

        filtered.forEachIndexed { index, lesson ->
            createLessonCard(lesson.docId, lesson.title, lesson.body, lesson.order, index)
        }
    }

    private fun createLessonCard(docId: String, title: String, body: String, order: Int, index: Int) {
        val color = colors[index % colors.size]

        val card = CardView(this).apply {
            radius = 28f
            useCompatPadding = true
            elevation = 3f
            setCardBackgroundColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 18)
            layoutParams = lp
        }

        // FrameLayout lets children overlap and be positioned freely —
        // this is what allows the buttons to sit in the top-right corner
        // independently from the lesson content on the left
        val frameLayout = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Main content row (icon + text) — same as before
        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Extra paddingEnd so the text doesn't slide under the buttons
            setPadding(40, 40, 120, 40)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvIcon = TextView(this).apply {
            text = icons[index % icons.size]
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            val size = (54 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = 30
            }
        }

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.parseColor("#17324D"))
            setTypeface(null, Typeface.BOLD)
        }

        val tvPreview = TextView(this).apply {
            text = if (openForAR) "Open this lesson to scan its matching AR model"
            else if (body.length > 45) body.take(45) + "..." else body
            textSize = 13f
            setTextColor(Color.parseColor("#668096"))
        }

        textBox.addView(tvTitle)
        textBox.addView(tvPreview)
        innerLayout.addView(tvIcon)
        innerLayout.addView(textBox)

        // Edit + Delete buttons stacked vertically in the top-right corner
        // Gravity.TOP or END positions them inside the FrameLayout
        val btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // TOP or END = stick to top-right corner of the card
            lp.gravity = Gravity.TOP or Gravity.END
            lp.topMargin = 10
            lp.rightMargin = 16
            layoutParams = lp
            setPadding(8, 8, 8, 8)
        }

        val tvEdit = TextView(this).apply {
            text = "✏️"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 4)
            setOnClickListener {
                val intent = Intent(this@LearningActivity, EditLessonActivity::class.java)
                intent.putExtra("docId", docId)
                intent.putExtra("title", title)
                intent.putExtra("body", body)
                intent.putExtra("order", order)
                startActivity(intent)
            }
        }

        val tvDelete = TextView(this).apply {
            text = "🗑️"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 4)
            setOnClickListener {
                android.app.AlertDialog.Builder(this@LearningActivity)
                    .setTitle("Delete Lesson")
                    .setMessage("Are you sure you want to delete \"$title\"? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        db.collection("lessons")
                            .document(docId)
                            .delete()
                            .addOnSuccessListener {
                                android.widget.Toast.makeText(
                                    this@LearningActivity,
                                    "\"$title\" deleted.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { e ->
                                android.widget.Toast.makeText(
                                    this@LearningActivity,
                                    "Failed to delete: ${e.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        btnContainer.addView(tvEdit)
        btnContainer.addView(tvDelete)

        // Add content row first, then buttons on top (FrameLayout stacking)
        frameLayout.addView(innerLayout)
        frameLayout.addView(btnContainer)

        card.addView(frameLayout)
        containerLessons.addView(card)

        card.setOnClickListener {
            val intent = Intent(this, LessonDetailActivity::class.java)
            intent.putExtra("title", title)
            intent.putExtra("body", body)
            intent.putExtra("school", school)
            intent.putExtra("date", date)
            intent.putExtra("sessionKey", sessionKey)
            intent.putExtra("lessonOrder", order)
            startActivity(intent)
        }
    }
}