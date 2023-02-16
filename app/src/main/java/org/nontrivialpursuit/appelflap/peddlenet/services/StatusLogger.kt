package org.nontrivialpursuit.appelflap.peddlenet.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.json.JSONObject
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.getLocalBroadcastManager
import org.nontrivialpursuit.appelflap.peddlenet.*
import java.io.BufferedWriter
import java.io.File
import java.util.zip.GZIPOutputStream

class StatusLogger(val conductor: Conductor) : ServiceHandler {

    override val log = Logger(this)
    override var is_running = false
    val lobroman = getLocalBroadcastManager(conductor.context)
    var outwriter: BufferedWriter? = null

    val logreceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra("status")?.also {
                (outwriter ?: gen_outputwriter()).also {
                    outwriter = it
                }.also { writer ->
                    writer.write(JSONObject(it).toString() + '\n')
                    writer.flush()
                }
            }
        }
    }

    fun gen_outputwriter(): BufferedWriter {
        return GZIPOutputStream(
            File(
                File(conductor.context.cacheDir, LOG_DIR).also { it.mkdir() }, "${System.currentTimeMillis()}$LOG_FILE_EXTENSION"
            ).outputStream(), 4096, true
        ).bufferedWriter(Charsets.UTF_8)
    }

    override fun start(): Boolean {
        is_running = true
        lobroman.registerReceiver(logreceiver, IntentFilter(STATUS_PUSH_ACTION))
        return true
    }

    override fun stop() {
        lobroman.unregisterReceiver(logreceiver)
        outwriter = outwriter?.let {
            it.flush()
            it.close()
            null
        }
        is_running = false
    }

    override fun restart() {
        stop()
        start()
    }
}