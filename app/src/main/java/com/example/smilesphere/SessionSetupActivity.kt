package com.example.smilesphere

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SessionSetupActivity : BaseActivity() {

    private lateinit var spinnerSchool:   Spinner
    private lateinit var etSchoolManual:  EditText
    private lateinit var tvManualLabel:   TextView
    private lateinit var tvDate:          TextView   // ← the actual date value display
    private lateinit var btnPickDate:     TextView   // ← TextView in your XML, not Button
    private lateinit var btnStartSession: Button
    private lateinit var progressBar:     ProgressBar

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val schoolList    = mutableListOf<String>()
    private val MANUAL_OPTION = "+ Type school name manually"
    private var selectedDate  = ""

    override fun getLayoutResourceId() = R.layout.activity_session_setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // IDs matched exactly to your activity_session_setup.xml
        spinnerSchool   = findViewById(R.id.spinnerSchool)
        etSchoolManual  = findViewById(R.id.etSchoolManual)
        tvManualLabel   = findViewById(R.id.tvManualLabel)
        tvDate          = findViewById(R.id.tvDate)          // date value TextView
        btnPickDate     = findViewById(R.id.btnPickDate)     // "📅 Change" TextView
        btnStartSession = findViewById(R.id.btnStartSession)
        progressBar     = findViewById(R.id.progressBar)

        // Auto-fill today's date
        selectedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        tvDate.text  = selectedDate

        btnPickDate.setOnClickListener { showDatePicker() }
        btnStartSession.setOnClickListener { validateAndStart() }
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        loadSchools()
    }

    // ─── Load schools from Firestore ──────────────────────────────────────────

    private fun loadSchools() {
        progressBar.visibility = View.VISIBLE

        db.collection("schools")
            .orderBy("name")
            .get()
            .addOnSuccessListener { docs ->
                progressBar.visibility = View.GONE

                schoolList.clear()
                schoolList.addAll(docs.mapNotNull { it.getString("name") })
                schoolList.add(MANUAL_OPTION)   // always last

                val adapter = createSchoolAdapter()
                spinnerSchool.adapter = adapter

                // Attach listener AFTER adapter is set
                spinnerSchool.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>, view: View?,
                            position: Int, id: Long
                        ) {
                            toggleManualInput(schoolList[position] == MANUAL_OPTION)
                        }
                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE

                // Firestore failed — fall back to manual entry only
                schoolList.clear()
                schoolList.add(MANUAL_OPTION)
                val adapter = createSchoolAdapter()
                spinnerSchool.adapter = adapter
                toggleManualInput(true)

                Toast.makeText(this, "Could not load schools — enter manually.", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Toggle manual input visibility ──────────────────────────────────────

    private fun toggleManualInput(show: Boolean) {
        tvManualLabel.visibility  = if (show) View.VISIBLE else View.GONE
        etSchoolManual.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) etSchoolManual.text?.clear()
    }

    private fun createSchoolAdapter(): ArrayAdapter<String> {
        return ArrayAdapter(
            this,
            R.layout.spinner_school_item,
            schoolList
        ).apply {
            setDropDownViewResource(R.layout.spinner_school_dropdown_item)
        }
    }

    // ─── Date picker ──────────────────────────────────────────────────────────

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day) }
                selectedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(picked.time)
                tvDate.text = selectedDate
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ─── Validate + start ─────────────────────────────────────────────────────

    private fun validateAndStart() {
        val position = spinnerSchool.selectedItemPosition
        val isManual = position >= 0 && schoolList.getOrNull(position) == MANUAL_OPTION

        val school = if (isManual) {
            etSchoolManual.text.toString().trim()
        } else {
            schoolList.getOrNull(position) ?: ""
        }

        if (school.isEmpty()) {
            Toast.makeText(this, "Please enter or select a school name.", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "Please select a date.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isManual) saveNewSchool(school)

        val sessionKey = "${auth.currentUser?.uid}_${school}_${selectedDate}_${System.currentTimeMillis()}"

        startActivity(
            Intent(this, QuizActivity::class.java).apply {
                putExtra("quizType",        "pre")
                putExtra("school",          school)
                putExtra("date",            selectedDate)
                putExtra("sessionKey",      sessionKey)
                putExtra("nextDestination", "SessionHome")
            }
        )
        finish()
    }

    private fun saveNewSchool(name: String) {
        db.collection("schools")
            .whereEqualTo("name", name)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    db.collection("schools").add(mapOf("name" to name, "zone" to ""))
                }
            }
    }
}
