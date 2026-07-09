package com.example.smilesphere

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.cardview.widget.CardView

class SessionHomeActivity : BaseActivity() {

    override fun getLayoutResourceId() = R.layout.activity_session_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val school = intent.getStringExtra("school") ?: "School"
        val date = intent.getStringExtra("date") ?: ""
        val sessionKey = intent.getStringExtra("sessionKey") ?: ""

        findViewById<TextView>(R.id.tvSchoolName).text = school
        findViewById<TextView>(R.id.tvSessionDate).text = date

        findViewById<CardView>(R.id.cardLearn).setOnClickListener {
            startActivity(Intent(this, LearningActivity::class.java).apply {
                putExtra("school", school)
                putExtra("date", date)
                putExtra("sessionKey", sessionKey)
            })
        }

        findViewById<CardView>(R.id.cardAR).setOnClickListener {
            startActivity(Intent(this, LearningActivity::class.java).apply {
                putExtra("school", school)
                putExtra("date", date)
                putExtra("sessionKey", sessionKey)
                putExtra("openForAR", true)
            })
        }

        findViewById<CardView>(R.id.cardPostQuiz).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java).apply {
                putExtra("quizType", "post")
                putExtra("school", school)
                putExtra("date", date)
                putExtra("sessionKey", sessionKey)
            })
        }
    }
}
