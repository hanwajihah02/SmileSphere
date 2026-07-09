package com.example.smilesphere

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

// NO abstract function here — just open class
open class BaseActivity : AppCompatActivity() {

    protected var drawerLayout: DrawerLayout?        = null
    protected var bottomNav: BottomNavigationView?   = null
    protected var navigationView: NavigationView?    = null

    open fun getLayoutResourceId(): Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layoutId = getLayoutResourceId()
        if (layoutId != 0) {
            setContentView(layoutId)
            setupNavigation()
        }
    }

    // Call this in every child activity AFTER setContentView()
    protected fun setupNavigation() {
        drawerLayout    = findViewById(R.id.drawerLayout)
        bottomNav       = findViewById(R.id.bottomNav)
        navigationView  = findViewById(R.id.navigationView)

        // Hamburger ☰ opens drawer
        findViewById<View>(R.id.btnHamburger)
            ?.setOnClickListener {
                drawerLayout?.openDrawer(GravityCompat.START)
            }

        // Side drawer item clicks
        navigationView?.setNavigationItemSelectedListener { item ->
            drawerLayout?.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_home    -> navigateTo(DashboardActivity::class.java)
                R.id.nav_learn   -> navigateTo(LearningActivity::class.java)
                R.id.nav_ar      -> navigateTo(ARActivity::class.java)
                R.id.nav_quiz    -> navigateTo(QuizActivity::class.java)
                R.id.nav_profile -> {
                    startActivity(
                        Intent(this, ProfileActivity::class.java))
                }
                R.id.nav_logout  -> {
                    FirebaseAuth.getInstance().signOut()
                    val i = Intent(this, LoginActivity::class.java)
                    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(i)
                }
            }
            true
        }

        // Bottom nav tab clicks
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bnav_home  -> navigateTo(DashboardActivity::class.java)
                R.id.bnav_learn -> navigateTo(LearningActivity::class.java)
                R.id.bnav_ar    -> navigateTo(ARActivity::class.java)
                R.id.bnav_quiz  -> navigateTo(QuizActivity::class.java)
            }
            true
        }

        highlightCurrentTab()
    }

    // Prevent reopening the same screen
    private fun navigateTo(target: Class<*>) {
        if (this::class.java != target) {
            startActivity(Intent(this, target).apply {
                copySessionExtras()
                if (target == QuizActivity::class.java &&
                    !hasExtra("quizType") &&
                    !hasExtra("type") &&
                    hasExtra("sessionKey")
                ) {
                    putExtra("quizType", "post")
                }
            })
        }
    }

    private fun Intent.copySessionExtras() {
        val source = this@BaseActivity.intent ?: return
        listOf("school", "date", "sessionKey", "quizType", "type", "lessonOrder").forEach { key ->
            when (val value = source.extras?.get(key)) {
                is String -> putExtra(key, value)
                is Int -> putExtra(key, value)
                is Boolean -> putExtra(key, value)
            }
        }
    }

    // Highlight correct bottom tab based on current screen
    private fun highlightCurrentTab() {
        val id = when (this) {
            is DashboardActivity,
            is SessionHomeActivity -> R.id.bnav_home
            is LearningActivity -> R.id.bnav_learn
            is ARActivity -> R.id.bnav_ar
            is QuizActivity,
            is ResultActivity -> R.id.bnav_quiz
            else -> return
        }
        bottomNav?.menu?.findItem(id)?.isChecked = true
    }
}
