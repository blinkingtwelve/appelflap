package org.nontrivialpursuit.appelflap.webpushpoll

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.nontrivialpursuit.appelflap.BuildConfig

class BootReceiver : BroadcastReceiver() {
    // (re-)adds the fauxwebpush poll job, if polling is defined
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.WEBPUSH_POLL_INTERVAL_QTR == 0 || BuildConfig.WEBPUSH_POLL_URL.length == 0) return
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            PollScheduler.schedule(context.applicationContext as Application, false)
        }
    }
}