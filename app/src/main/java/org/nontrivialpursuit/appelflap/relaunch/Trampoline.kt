package org.nontrivialpursuit.appelflap.relaunch

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Process
import android.view.WindowManager
import org.nontrivialpursuit.appelflap.*

const val INTENT_EXTRA_VICTIM_PID = "victim_pid"
val APPELFLAP_MAIN_PROCESS_REGEX = Regex("^${Regex.escape(BuildConfig.APPLICATION_ID)}$")


class Trampoline : AppCompatActivity() {

    val log = Logger(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)

        log.v("Launched for victim pid: ${intent.getIntExtra(INTENT_EXTRA_VICTIM_PID, -1)}")
        pgrep_first(APPELFLAP_MAIN_PROCESS_REGEX)?.also { main_pid ->
            log.v("Killing PID ${main_pid}")
            Process.killProcess(main_pid)
            log.v("Awaiting death of PID ${main_pid}")
            waitpid(main_pid)
        }
        log.v("Relaunching app")
        this.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)?.also {
            this.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finishAndRemoveTask()
    }
}