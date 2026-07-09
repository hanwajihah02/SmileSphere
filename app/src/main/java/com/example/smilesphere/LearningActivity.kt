package com.example.smilesphere

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LearningActivity : BaseActivity() {

    private lateinit var containerLessons: LinearLayout
    private val db = FirebaseFirestore.getInstance()
    private var school = ""
    private var date = ""
    private var sessionKey = ""
    private var openForAR = false

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

        school = intent.getStringExtra("school") ?: ""
        date = intent.getStringExtra("date") ?: ""
        sessionKey = intent.getStringExtra("sessionKey") ?: ""
        openForAR = intent.getBooleanExtra("openForAR", false)

        containerLessons = findViewById(R.id.containerLessons)
        loadLessons()
    }

    private fun loadLessons() {
        // addSnapshotListener ensures automatic updates when you change Firebase
        db.collection("lessons")
            .orderBy("order", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("Firestore", "Listen failed.", error)
                    return@addSnapshotListener
                }

                containerLessons.removeAllViews()
                snapshots?.forEachIndexed { index, doc ->
                    val title = doc.getString("title") ?: "Untitled Lesson"
                    val body = doc.getString("body") ?: ""
                    val order = doc.getLong("order")?.toInt() ?: (index + 1)
                    createLessonCard(doc.id, title, body, order, index)
                }
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

        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 40, 40, 40)
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.parseColor("#17324D"))
            setTypeface(null, Typeface.BOLD)
        }

        val tvPreview = TextView(this).apply {
            text = if (body.length > 45) body.take(45) + "..." else body
            if (openForAR) {
                text = "Open this lesson to scan its matching AR model"
            }
            textSize = 13f
            setTextColor(Color.parseColor("#668096"))
        }

        textBox.addView(tvTitle)
        textBox.addView(tvPreview)
        innerLayout.addView(tvIcon)
        innerLayout.addView(textBox)

        val tvArrow = TextView(this).apply {
            text = ">"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#107B64"))
        }
        innerLayout.addView(tvArrow)

        card.addView(innerLayout)
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
