package org.nontrivialpursuit.appelflap.peddlenet.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.getLocalBroadcastManager
import org.nontrivialpursuit.appelflap.peddlenet.Conductor
import org.nontrivialpursuit.appelflap.peddlenet.STATUS_INTERVAL_ACTION
import org.nontrivialpursuit.appelflap.peddlenet.STATUS_PUSH_ACTION
import org.nontrivialpursuit.appelflap.peddlenet.ServiceHandler
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class StatusBroadcaster(val conductor: Conductor) : ServiceHandler {

    override val log = Logger(this)
    override var is_running = false
    val lobroman = getLocalBroadcastManager(conductor.context)
    private var statseq = 0
    private val schedxecutor = Executors.newScheduledThreadPool(1)
    private var broadcaster: ScheduledFuture<*>? = null

    val interval_update_receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getLongExtra("status_interval", 0).also {
                setStatusInterval(it)
            }
        }
    }

    fun stats_broadcast() {
        conductor.get_state().also {
            it.put("statsequence", statseq).toString(2).also { statemsg ->
                lobroman.sendBroadcast(Intent(STATUS_PUSH_ACTION).also {
                    it.putExtra("status", statemsg)
                })
            }
            statseq++
        }
    }

    fun setStatusInterval(interval: Long) {
        broadcaster?.cancel(true)
        if (interval > 0) {
            broadcaster = schedxecutor.scheduleAtFixedRate(
                { stats_broadcast() }, 0, interval, TimeUnit.SECONDS
            )
        }
    }

    override fun start(): Boolean {
        is_running = true
        lobroman.registerReceiver(interval_update_receiver, IntentFilter(STATUS_INTERVAL_ACTION))
        return true
    }

    override fun stop() {
        lobroman.unregisterReceiver(interval_update_receiver)
        broadcaster?.cancel(true)
        is_running = false
    }

    override fun restart() {
        stop()
        start()
    }
}