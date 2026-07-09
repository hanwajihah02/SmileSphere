package com.example.smilesphere

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LessonDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lesson_detail)

        val title = intent.getStringExtra("title") ?: ""
        val body  = intent.getStringExtra("body")  ?: ""
        val school = intent.getStringExtra("school") ?: ""
        val date = intent.getStringExtra("date") ?: ""
        val sessionKey = intent.getStringExtra("sessionKey") ?: ""
        val lessonOrder = intent.getIntExtra("lessonOrder", 1)

        findViewById<TextView>(R.id.tvToolbarTitle).text = title
        findViewById<TextView>(R.id.tvTitle).text        = title
        findViewById<TextView>(R.id.tvBody).text         = body
        findViewById<ImageView>(R.id.ivLessonImage)
            .setImageResource(getImageForLesson(lessonOrder))

        // Back button
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Go to AR button — hidden for lesson 6 (info-only, no AR)
        val btnGoAR = findViewById<Button>(R.id.btnGoAR)
        if (lessonOrder == 6) {
            btnGoAR.visibility = android.view.View.GONE
        } else {
            btnGoAR.setOnClickListener {
                startActivity(Intent(this, ARActivity::class.java).apply {
                    putExtra("school", school)
                    putExtra("date", date)
                    putExtra("sessionKey", sessionKey)
                    putExtra("lessonOrder", lessonOrder)
                })
            }
        }
    }

    private fun getImageForLesson(order: Int): Int = when (order) {
        1 -> R.drawable.lesson1_anatomy
        2 -> R.drawable.lesson2_types
        3 -> R.drawable.lesson3_brushing
        4 -> R.drawable.lesson4_plaque
        5 -> R.drawable.lesson5_gum
        else -> R.drawable.lesson6_services
    }
}