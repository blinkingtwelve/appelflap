package org.nontrivialpursuit.appelflap

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import com.yariksoffice.lingver.Lingver
import com.yariksoffice.lingver.store.PreferenceLocaleStore
import org.nontrivialpursuit.appelflap.peddlenet.Conductor
import org.nontrivialpursuit.appelflap.relaunch.INTENT_EXTRA_VICTIM_PID
import org.nontrivialpursuit.appelflap.relaunch.Trampoline
import org.nontrivialpursuit.eekhoorn.KoekoeksNest
import org.nontrivialpursuit.ingeblikt.KOEKOEK_SUBDIR
import org.nontrivialpursuit.ingeblikt.pathjoin
import java.io.File
import java.util.*

const val PID_NO_PID = 0
const val PID_SELF = -1

class Appelflap : Application() {
    lateinit var localeStore: PreferenceLocaleStore
    var geckoRuntimeManager: GeckoRuntimeManager? = null
    lateinit var conductor: Conductor
    lateinit var koekoeksNest: KoekoeksNest


    override fun onCreate() {
        super.onCreate()
        AppUpdate.getInstance(this).getUpgradeIntent()?.also {
            startActivity(it)
        }
        localeStore = PreferenceLocaleStore(this, defaultLocale = Locale(BuildConfig.DEFAULT_LANGUAGE))
        Lingver.init(this, localeStore)
        enrich_the_sentry(applicationContext)
        koekoeksNest = KoekoeksNest(dbOps = AndroidCacheDBOps(),
                                    profile_dir = File(filesDir, pathjoin("mozilla", "default_profile")).apply { mkdirs() },
                                    pkiOps = AndroidPKIOps(context = this, devcertCommonName = get_friendly_nodeID(this)),
                                    bundle_dir = File(
                                        this.filesDir, KOEKOEK_SUBDIR
                                    ).apply { mkdir() })
        conductor = Conductor.getInstance(this, koekoeksNest)
    }


    fun relaunchViaTrampoline() {
        applicationContext.also { context ->
            context.startActivity(getRelaunchIntent())
        }
    }


    fun getRelaunchIntent(pid: Int = PID_SELF): Intent {
        return applicationContext.let { context ->
            Intent(context, Trampoline::class.java).also {
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NEW_TASK)
                when (pid) {
                    PID_SELF -> it.putExtra(INTENT_EXTRA_VICTIM_PID, Process.myPid())
                    PID_NO_PID -> {
                    }
                    else -> it.putExtra(INTENT_EXTRA_VICTIM_PID, pid)
                }
            }
        }
    }

    fun getRelaunchPendingIntent(pid: Int = PID_SELF): PendingIntent {
        return applicationContext.let { context ->
            PendingIntent.getActivity(
                context, 0, getRelaunchIntent(pid = pid), PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }


    companion object {
        fun get(context: Context): Appelflap {
            return (context.applicationContext as Appelflap)
        }
    }
}