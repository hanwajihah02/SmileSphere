package com.example.smilesphere

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ResultActivity : BaseActivity() {

    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultType: TextView
    private lateinit var containerResults: LinearLayout
    private lateinit var btnPostQuiz: Button
    private lateinit var btnAnalysis: Button

    override fun getLayoutResourceId() = R.layout.activity_result

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultType = findViewById(R.id.tvResultType)
        containerResults = findViewById(R.id.containerResults)
        btnPostQuiz = findViewById(R.id.btnPostQuiz)
        btnAnalysis = findViewById(R.id.btnAnalysis)

        val score = intent.getIntExtra("score", 0)
        val total = intent.getIntExtra("total", 0)
        val quizType = intent.getStringExtra("quizType")
            ?: intent.getStringExtra("type")
            ?: "pre"
        val school = intent.getStringExtra("school") ?: ""
        val date = intent.getStringExtra("date") ?: ""
        val sessionKey = intent.getStringExtra("sessionKey") ?: ""

        val percent = if (total > 0) (score * 100) / total else 0

        tvResultTitle.text = if (quizType == "pre") "Pre-Quiz Result" else "Post-Quiz Result"
        tvResultType.text = quizType.uppercase() + "-QUIZ"

        addResultLine("Score", "$score / $total")
        addResultLine("Accuracy", "$percent%")
        addResultLine(
            "Feedback",
            when {
                percent >= 80 -> "Excellent work."
                percent >= 60 -> "Good effort."
                percent >= 40 -> "Keep learning."
                else -> "Review the lessons and try again."
            }
        )

        if (quizType == "pre") {
            btnPostQuiz.text = "Continue to session modules"
            btnPostQuiz.setOnClickListener {
                startActivity(Intent(this, SessionHomeActivity::class.java).apply {
                    putExtra("school", school)
                    putExtra("date", date)
                    putExtra("sessionKey", sessionKey)
                })
                finish()
            }
            btnAnalysis.visibility = View.GONE
        } else {
            btnPostQuiz.text = "Show dashboard graph"
            btnPostQuiz.setOnClickListener {
                startActivity(Intent(this, DashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            }
            btnAnalysis.visibility = View.GONE
        }
    }

    private fun addResultLine(label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 18)
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#668096"))
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#17324D"))
        })
        containerResults.addView(row)
    }
}
