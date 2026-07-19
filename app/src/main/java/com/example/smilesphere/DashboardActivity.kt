package com.example.smilesphere

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class DashboardActivity : BaseActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var tvTotalSessions: TextView
    private lateinit var tvSchoolsVisited: TextView
    private lateinit var tvPreAvg: TextView
    private lateinit var tvPostAvg: TextView
    private lateinit var spinnerSchool: Spinner
    private lateinit var barChart: BarChart
    private lateinit var btnStartSession: Button
    private lateinit var tvNoData: TextView
    private lateinit var progressBar: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val schoolList = mutableListOf<String>()

    override fun getLayoutResourceId() = R.layout.activity_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tvWelcome        = findViewById(R.id.tvWelcome)
        tvTotalSessions  = findViewById(R.id.tvTotalSessions)
        tvSchoolsVisited = findViewById(R.id.tvSchoolsVisited)
        tvPreAvg         = findViewById(R.id.tvPreAvg)
        tvPostAvg        = findViewById(R.id.tvPostAvg)
        spinnerSchool    = findViewById(R.id.spinnerSchool)
        barChart         = findViewById(R.id.barChart)
        btnStartSession  = findViewById(R.id.btnStartSession)
        tvNoData         = findViewById(R.id.tvNoData)
        progressBar      = findViewById(R.id.progressBar)

        loadWelcomeName()
        loadSummaryStats()
        loadSchoolsForSpinner()

        btnStartSession.setOnClickListener {
            startActivity(Intent(this, SessionSetupActivity::class.java))
        }
    }

    // ─── Welcome name ────────────────────────────────────────────────────────

    private fun loadWelcomeName() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Officer"
                tvWelcome.text = "Welcome back, $name 👋"
            }
    }

    // ─── Summary stat cards ──────────────────────────────────────────────────

    private fun loadSummaryStats() {
        val uid = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("sessions")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { docs ->
                progressBar.visibility = View.GONE

                val allSessions = docs.documents
                val total = allSessions.size
                val schools = allSessions.mapNotNull { it.getString("school") }.toSet().size

                // Compute averages from tallied quiz results
                val preSessions  = allSessions.filter { it.getString("type") == "pre" }
                val postSessions = allSessions.filter { it.getString("type") == "post" }

                val preAvg  = averageScore(preSessions)
                val postAvg = averageScore(postSessions)

                tvTotalSessions.text  = total.toString()
                tvSchoolsVisited.text = schools.toString()
                tvPreAvg.text         = "%.0f%%".format(preAvg)
                tvPostAvg.text        = "%.0f%%".format(postAvg)
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Could not load stats", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Compute a percentage score from q1…qN fields.
     * Each field is 1 (correct) or 0 (wrong); we count how many are 1.
     */
    private fun computeScore(doc: com.google.firebase.firestore.DocumentSnapshot): Double? {
        doc.getDouble("percent")?.let { return it }
        doc.getLong("percent")?.let { return it.toDouble() }

        val data = doc.data ?: return null
        var correctTotal = 0
        var responseTotal = 0

        data.entries
            .filter { it.key.matches(Regex("q\\d+")) }
            .forEach { entry ->
                val question = entry.value as? Map<*, *> ?: return@forEach
                val tally = question["tally"] as? Map<*, *> ?: return@forEach
                val correctIndex = when (val correct = question["correct"]) {
                    is Long -> correct.toInt()
                    is Int -> correct
                    is Double -> correct.toInt()
                    else -> 0
                }
                val keys = tally.keys.toList()
                tally.values.forEach { value ->
                    responseTotal += when (value) {
                        is Long -> value.toInt()
                        is Int -> value
                        is Double -> value.toInt()
                        else -> 0
                    }
                }
                val correctKey = keys.getOrNull(correctIndex)
                correctTotal += when (val value = tally[correctKey]) {
                    is Long -> value.toInt()
                    is Int -> value
                    is Double -> value.toInt()
                    else -> 0
                }
            }

        if (responseTotal == 0) return null
        return (correctTotal.toDouble() / responseTotal) * 100.0
    }

    // ─── School spinner + chart ───────────────────────────────────────────────

    // FIXED: now built from the current user's own sessions instead of the
    // global "schools" collection, so a new account starts with an empty spinner
    // instead of showing every school any user has ever visited.
    private fun loadSchoolsForSpinner() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("sessions")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { docs ->
                schoolList.clear()
                schoolList.addAll(
                    docs.mapNotNull { it.getString("school") }
                        .distinct()
                        .sorted()
                )

                if (schoolList.isEmpty()) {
                    spinnerSchool.visibility = View.GONE
                    barChart.visibility = View.GONE
                    tvNoData.visibility = View.VISIBLE
                    tvNoData.text = "No sessions yet. Start a session to see your data here."
                    return@addOnSuccessListener
                }

                spinnerSchool.visibility = View.VISIBLE
                val adapter = ArrayAdapter(
                    this,
                    R.layout.spinner_school_item,
                    schoolList
                ).apply {
                    setDropDownViewResource(R.layout.spinner_school_dropdown_item)
                }
                spinnerSchool.adapter = adapter

                spinnerSchool.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>, view: View?,
                            position: Int, id: Long
                        ) { loadChartForSchool(schoolList[position]) }
                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }
            }
            .addOnFailureListener {
                spinnerSchool.visibility = View.GONE
                barChart.visibility = View.GONE
                tvNoData.visibility = View.VISIBLE
                tvNoData.text = "Could not load your schools."
            }
    }

    // FIXED: now filters by uid AND school, so selecting a school only shows
    // the current user's own sessions for that school, not every user's.
    private fun loadChartForSchool(school: String) {
        val uid = auth.currentUser?.uid ?: return
        tvNoData.visibility = View.GONE
        barChart.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

        db.collection("sessions")
            .whereEqualTo("uid", uid)
            .whereEqualTo("school", school)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { docs ->
                progressBar.visibility = View.GONE

                val sessions = docs.documents
                val preSessions  = sessions.filter { it.getString("type") == "pre" }
                val postSessions = sessions.filter { it.getString("type") == "post" }

                if (preSessions.isEmpty() && postSessions.isEmpty()) {
                    barChart.visibility = View.GONE
                    tvNoData.visibility = View.VISIBLE
                    tvNoData.text = "No sessions recorded for $school yet."
                    return@addOnSuccessListener
                }

                // Group by date for the X-axis
                val dates = (preSessions + postSessions)
                    .mapNotNull { it.getString("date") }
                    .distinct()
                    .sorted()

                val preEntries  = mutableListOf<BarEntry>()
                val postEntries = mutableListOf<BarEntry>()

                dates.forEachIndexed { i, date ->
                    val pre  = preSessions.filter  { it.getString("date") == date }
                    val post = postSessions.filter { it.getString("date") == date }

                    val preScore = averageScore(pre).toFloat()
                    val postScore = averageScore(post).toFloat()

                    preEntries.add(BarEntry(i.toFloat(), preScore))
                    postEntries.add(BarEntry(i.toFloat(), postScore))
                }

                renderChart(preEntries, postEntries, dates)
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Could not load chart data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderChart(
        preEntries: List<BarEntry>,
        postEntries: List<BarEntry>,
        labels: List<String>
    ) {
        val preSet = BarDataSet(preEntries, "Pre-Quiz").apply {
            color = android.graphics.Color.parseColor("#7F77DD")
            valueTextSize = 10f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    if (value == 0f) "" else "%.0f%%".format(value)
            }
        }
        val postSet = BarDataSet(postEntries, "Post-Quiz").apply {
            color = android.graphics.Color.parseColor("#1D9E75")
            valueTextSize = 10f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    if (value == 0f) "" else "%.0f%%".format(value)
            }
        }

        val groupSpace  = 0.3f
        val barSpace    = 0.05f
        val barWidth    = 0.3f

        val data = BarData(preSet, postSet).apply {
            this.barWidth = barWidth
        }

        barChart.apply {
            this.data = data
            description.isEnabled = false
            setFitBars(true)
            groupBars(0f, groupSpace, barSpace)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setCenterAxisLabels(true)
                setDrawGridLines(false)
                labelRotationAngle = -30f
                textSize = 10f
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 20f
            }
            axisRight.isEnabled = false
            legend.isEnabled = true
            animateY(600)
            invalidate()
        }
    }

    private fun averageScore(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>
    ): Double {
        val scores = docs.mapNotNull { computeScore(it) }
        return if (scores.isEmpty()) 0.0 else scores.average()
    }

    override fun onResume() {
        super.onResume()
        // Refresh stats when returning from a session
        loadSummaryStats()
        loadSchoolsForSpinner()
    }
}