package org.nontrivialpursuit.appelflap.p2pmonitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.R

class P2PMonitor : AppCompatActivity() {

    val log = Logger(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_p2pmonitor)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        navView.setItemIconTintList(null)

        val navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_prefs, R.id.navigation_status, R.id.navigation_logs
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }
}