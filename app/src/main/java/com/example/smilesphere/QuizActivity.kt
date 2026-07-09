package com.example.smilesphere

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class QuizActivity : BaseActivity() {

    private lateinit var tvQuizLabel: TextView
    private lateinit var tvQNum: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var containerOptions: LinearLayout
    private lateinit var btnNextQ: Button

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val questions = mutableListOf<Map<String, Any>>()
    // tallies[questionIndex][optionIndex] = count
    private val tallies   = mutableListOf<MutableList<Int>>()
    private var currentQ  = 0
    private var quizType  = "pre"

    // School session info passed from SessionSetupActivity
    private var schoolName  = ""
    private var sessionDate = ""
    private var sessionKey  = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)
        setupNavigation()

        // Read intent extras
        quizType    = intent.getStringExtra("quizType")
            ?: intent.getStringExtra("type")
            ?: "pre"
        schoolName  = intent.getStringExtra("school")      ?: ""
        sessionDate = intent.getStringExtra("date")        ?: ""
        sessionKey  = intent.getStringExtra("sessionKey")  ?: ""

        tvQuizLabel      = findViewById(R.id.tvQuizLabel)
        tvQNum           = findViewById(R.id.tvQNum)
        tvQuestion       = findViewById(R.id.tvQuestion)
        containerOptions = findViewById(R.id.containerOptions)
        btnNextQ         = findViewById(R.id.btnNextQ)

        tvQuizLabel.text =
            if (quizType == "pre") "PRE-QUIZ" else "POST-QUIZ"

        loadQuestions()
        btnNextQ.setOnClickListener { onNextClicked() }
    }

    private fun loadQuestions() {
        db.collection("questions")
            .orderBy("order", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                questions.clear()
                tallies.clear()
                for (doc in result) {
                    questions.add(doc.data)
                    val opts = doc.get("options") as? List<*>
                    // initialise all counts to 0
                    tallies.add(
                        MutableList(opts?.size ?: 4) { 0 }
                    )
                }
                if (questions.isNotEmpty()) showQuestion()
            }
            .addOnFailureListener {
                Toast.makeText(this,
                    "Failed to load questions",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun showQuestion() {
        val q    = questions[currentQ]
        val opts = q["options"] as? List<*> ?: emptyList<Any>()

        tvQNum.text     =
            "Question ${currentQ + 1} of ${questions.size}"
        tvQuestion.text = q["question"].toString()
        btnNextQ.text   =
            if (currentQ == questions.size - 1)
                "Submit" else "Next Question →"

        containerOptions.removeAllViews()

        opts.forEachIndexed { i, option ->
            // ── outer card ──────────────────────────────────
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.WHITE)
                elevation = 6f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 14
                layoutParams = lp
            }

            // ── option label ─────────────────────────────────
            val tvOpt = TextView(this).apply {
                text     = option.toString()
                textSize = 14f
                setTextColor(Color.parseColor("#1A1A1A"))
                setTypeface(null, Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f)
            }

            // ── MINUS button ─────────────────────────────────
            val btnMinus = Button(this).apply {
                text            = "−"
                textSize        = 18f
                setTextColor(Color.WHITE)
                backgroundTintList =
                    android.content.res.ColorStateList
                        .valueOf(Color.parseColor("#E24B4A"))
                val lp = LinearLayout.LayoutParams(88, 88)
                lp.marginEnd = 4
                layoutParams = lp
            }

            // ── count EditText ───────────────────────────────
            val etCount = EditText(this).apply {
                setText("0")
                textSize  = 16f
                gravity   = Gravity.CENTER
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.parseColor("#1D9E75"))
                setTypeface(null, Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#F0FBF7"))
                val lp = LinearLayout.LayoutParams(120,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = 4
                layoutParams = lp
            }

            // ── PLUS button ──────────────────────────────────
            val btnPlus = Button(this).apply {
                text            = "+"
                textSize        = 18f
                setTextColor(Color.WHITE)
                backgroundTintList =
                    android.content.res.ColorStateList
                        .valueOf(Color.parseColor("#1D9E75"))
                layoutParams = LinearLayout.LayoutParams(88, 88)
            }

            // ── button listeners ─────────────────────────────
            btnPlus.setOnClickListener {
                tallies[currentQ][i]++
                etCount.setText(
                    tallies[currentQ][i].toString())
            }

            btnMinus.setOnClickListener {
                if (tallies[currentQ][i] > 0) {
                    tallies[currentQ][i]--
                    etCount.setText(
                        tallies[currentQ][i].toString())
                }
            }

            // Save typed value when focus leaves EditText
            etCount.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    tallies[currentQ][i] =
                        etCount.text.toString()
                            .toIntOrNull() ?: 0
                }
            }

            card.addView(tvOpt)
            card.addView(btnMinus)
            card.addView(etCount)
            card.addView(btnPlus)
            containerOptions.addView(card)
        }
    }

    // ── Force-read all EditText values before moving ─────────
    private fun saveCurrentTallies() {
        for (i in 0 until containerOptions.childCount) {
            val card = containerOptions.getChildAt(i)
                    as? LinearLayout ?: continue
            // EditText is child index 2 in each card
            val et = card.getChildAt(2) as? EditText
                ?: continue
            tallies[currentQ][i] =
                et.text.toString().toIntOrNull() ?: 0
        }
    }

    private fun onNextClicked() {
        saveCurrentTallies()          // always save before moving
        if (currentQ < questions.size - 1) {
            currentQ++
            showQuestion()
        } else {
            saveTallies()
        }
    }

    private fun saveTallies() {
        val uid  = auth.currentUser?.uid ?: return
        var correctTotal = 0
        var responseTotal = 0

        val data = hashMapOf<String, Any>(
            "uid"        to uid,
            "type"       to quizType,
            "school"     to schoolName,
            "date"       to sessionDate,
            "sessionKey" to sessionKey,
            "timestamp"  to Timestamp.now()
        )

        questions.forEachIndexed { qi, q ->
            val opts = q["options"] as? List<*> ?: emptyList<Any>()
            val correctIndex = (q["correct"] as? Long)?.toInt()
                ?: (q["correct"] as? Int)
                ?: 0
            val tally = hashMapOf<String, Int>()
            opts.forEachIndexed { oi, opt ->
                val count = tallies[qi][oi]
                tally[opt.toString()] = count
                responseTotal += count
                if (oi == correctIndex) correctTotal += count
            }
            data["q${qi + 1}"] = mapOf(
                "question" to q["question"].toString(),
                "correct"  to correctIndex,
                "tally"    to tally
            )
        }

        val percent = if (responseTotal > 0) {
            (correctTotal * 100.0) / responseTotal
        } else {
            0.0
        }
        data["score"] = correctTotal
        data["total"] = responseTotal
        data["percent"] = percent

        db.collection("sessions").add(data)
            .addOnSuccessListener { ref ->
                val intent = Intent(this,
                    ResultActivity::class.java)
                intent.putExtra("sessionId",  ref.id)
                intent.putExtra("quizType",   quizType)
                intent.putExtra("school",     schoolName)
                intent.putExtra("date",       sessionDate)
                intent.putExtra("sessionKey", sessionKey)
                intent.putExtra("score",      correctTotal)
                intent.putExtra("total",      responseTotal)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,
                    "Failed to save: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
    }
}
