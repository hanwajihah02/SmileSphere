package com.example.smilesphere

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AnalysisActivity : BaseActivity() {

    private lateinit var barChart: BarChart
    private lateinit var spinnerSchoolFilter: Spinner
    private val db = FirebaseFirestore.getInstance()

    private val preEntries  = mutableListOf<BarEntry>()
    private val postEntries = mutableListOf<BarEntry>()
    private val labels      = mutableListOf<String>()
    private val schoolNames = mutableListOf<String>()

    // School passed from ResultActivity
    private var currentSchool = ""

    override fun getLayoutResourceId() = R.layout.activity_analysis

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barChart           = findViewById(R.id.barChart)
        spinnerSchoolFilter = findViewById(R.id.spinnerSchoolFilter)

        // Get school passed from ResultActivity
        currentSchool = intent.getStringExtra("school") ?: ""

        findViewById<Button>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }

        // Load school list for dropdown
        loadSchoolList()
    }

    private fun loadSchoolList() {
        db.collection("schools")
            .orderBy("name")
            .get()
            .addOnSuccessListener { result ->
                schoolNames.clear()
                schoolNames.add("-- Select school --")
                for (doc in result) {
                    val name = doc.getString("name") ?: continue
                    schoolNames.add(name)
                }

                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    schoolNames
                )
                adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item)
                spinnerSchoolFilter.adapter = adapter

                // Auto-select the school passed from ResultActivity
                if (currentSchool.isNotEmpty()) {
                    val idx = schoolNames.indexOf(currentSchool)
                    if (idx >= 0) {
                        spinnerSchoolFilter.setSelection(idx)
                        loadSessionData(currentSchool)
                    }
                }

                // When officer manually picks a different school
                spinnerSchoolFilter.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: android.view.View?,
                            position: Int,
                            id: Long
                        ) {
                            val selected = schoolNames[position]
                            if (selected != "-- Select school --") {
                                loadSessionData(selected)
                            }
                        }
                        override fun onNothingSelected(
                            parent: AdapterView<*>?) {}
                    }
            }
    }

    private fun loadSessionData(school: String) {
        // Clear previous chart data
        preEntries.clear()
        postEntries.clear()
        labels.clear()

        db.collection("sessions")
            .whereEqualTo("school", school)   // ← filter by school
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { result ->
                val preDoc  = result.documents
                    .firstOrNull { it.getString("type") == "pre" }
                val postDoc = result.documents
                    .firstOrNull { it.getString("type") == "post" }

                if (preDoc == null || postDoc == null) {
                    Toast.makeText(this,
                        "No complete session data for $school yet.",
                        Toast.LENGTH_LONG).show()
                    barChart.clear()
                    return@addOnSuccessListener
                }

                var qi = 1
                while (preDoc.contains("q$qi")) {
                    val preQ  = preDoc.get("q$qi")  as? Map<*,*>
                    val postQ = postDoc.get("q$qi") as? Map<*,*>
                    if (preQ == null || postQ == null) break

                    val correctIdx = when (val c = preQ["correct"]) {
                        is Long   -> c.toInt()
                        is Int    -> c
                        is Double -> c.toInt()
                        else      -> 0
                    }

                    fun getCount(tally: Map<*,*>?, idx: Int): Float {
                        if (tally == null) return 0f
                        val keys = tally.keys.toList()
                        if (idx >= keys.size) return 0f
                        return when (val v = tally[keys[idx]]) {
                            is Long   -> v.toFloat()
                            is Int    -> v.toFloat()
                            is Double -> v.toFloat()
                            else      -> 0f
                        }
                    }

                    val preTally  = preQ["tally"]  as? Map<*,*>
                    val postTally = postQ["tally"] as? Map<*,*>

                    preEntries.add(BarEntry(
                        (qi-1).toFloat(),
                        getCount(preTally,  correctIdx)))
                    postEntries.add(BarEntry(
                        (qi-1).toFloat(),
                        getCount(postTally, correctIdx)))
                    labels.add("Q$qi")
                    qi++
                }

                if (labels.isEmpty()) {
                    Toast.makeText(this,
                        "No quiz data found for $school",
                        Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                drawChart(school)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,
                    "Failed: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun drawChart(school: String) {
        val barWidth   = 0.35f
        val barSpace   = 0.05f
        val groupSpace = 0.2f

        val preSet = BarDataSet(preEntries, "Pre-Quiz").apply {
            color = Color.parseColor("#2563EB")
            valueTextSize = 12f
        }
        val postSet = BarDataSet(postEntries, "Post-Quiz").apply {
            color = Color.parseColor("#1D9E75")
            valueTextSize = 12f
        }

        val data = BarData(preSet, postSet).apply {
            this.barWidth = barWidth
        }

        barChart.apply {
            this.data  = data
            description.text = school  // show school name
            description.isEnabled = true
            description.textSize = 11f
            setFitBars(true)

            xAxis.apply {
                valueFormatter    = IndexAxisValueFormatter(labels)
                position          = XAxis.XAxisPosition.BOTTOM
                granularity       = 1f
                setCenterAxisLabels(true)
                axisMinimum       = 0f
            }
            axisLeft.axisMinimum  = 0f
            axisRight.isEnabled   = false
            legend.apply {
                verticalAlignment =
                    Legend.LegendVerticalAlignment.TOP
                horizontalAlignment =
                    Legend.LegendHorizontalAlignment.RIGHT
            }
            groupBars(0f, groupSpace, barSpace)
            invalidate()
            animateY(800)
        }
    }
}
