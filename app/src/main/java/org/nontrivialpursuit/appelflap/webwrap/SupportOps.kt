package org.nontrivialpursuit.appelflap.webwrap

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import org.nontrivialpursuit.appelflap.Appelflap
import org.nontrivialpursuit.appelflap.R

class SupportOps : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support_ops)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.support_ops, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val app = Appelflap.get(applicationContext)
        when (item.itemId) {
            R.id.action_refresh -> {
                app.geckoRuntimeManager?.wrapper?.goHome() ?: Toast
                    .makeText(applicationContext, R.string.wrapper_not_running, Toast.LENGTH_LONG).show()
                return true
            }
            R.id.action_zap -> {
                createStateZapDialog(this).show()
                return true
            }
            R.id.action_shutdown -> {
                createShutdownDialog(this).show()
                return true
            }
            R.id.action_seed -> {
                app.geckoRuntimeManager?.wrapper?.also {
                    gen_QRpage(it)?.also { qrpage ->
                        it.loadHtml(qrpage)
                        startActivity(Intent(applicationContext, GeckoWrap::class.java))
                        return true
                    } ?: Toast.makeText(applicationContext, R.string.wifi_not_associated, Toast.LENGTH_SHORT).show().let { false }
                } ?: Toast.makeText(applicationContext, R.string.wrapper_not_running, Toast.LENGTH_LONG).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

}