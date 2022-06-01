package org.nontrivialpursuit.appelflap.webpushpoll

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.AsyncTask
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.nontrivialpursuit.appelflap.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class PollTask(
        private val context: Context, private val jobparms: JobParameters, private val caller: JobService) : AsyncTask<String?, Int?, JSONObject>() {

    val log = Logger(this)

    override fun doInBackground(vararg params: String?): JSONObject {
        log.i("PollTask started")
        val pushRegs = PushRegistrations(context)
        val messages_per_scope = JSONObject()
        for ((scope, value) in pushRegs.getRegs()) {
            val regid = value[0]
            val token = value[1]
            val cursor_pos = pushRegs.getCursorPos(regid)
            val res = pollOne(regid, token, cursor_pos)
            res?.also {
                pushRegs.storeCursorPos(regid, it.new_cursor_pos)
                if (it.pushmessages.length() > 0) {
                    try {
                        messages_per_scope.put(scope, it.pushmessages)
                    } catch (e: JSONException) {
                        log.e(e.toString())
                    }
                }
            }
        }
        return messages_per_scope
    }

    protected fun pollOne(
            regid: String?, token: String?, cursor_pos: Long?): OnePollResult? {
        // TODO: use kohttp
        var pushmessages: JSONArray? = null
        var poll_conn: HttpURLConnection? = null
        var new_cursor_pos: Long? = null
        try {
            val poll_url = URL(
                String.format(
                    "%s/%s/%d", BuildConfig.WEBPUSH_POLL_URL, regid, cursor_pos
                )
            )
            poll_conn = poll_url.openConnection() as HttpURLConnection
            poll_conn.connectTimeout = TCP_CONNECT_TIMEOUT
            poll_conn.readTimeout = TCP_READ_TIMEOUT
            poll_conn.setRequestProperty("Accept", "application/json")
            poll_conn.setRequestProperty("Appelflap-Letterbox-Token", token)
            poll_conn.requestMethod = "POST"
            poll_conn.doInput = true
            poll_conn.doOutput = false
            val thejson_stream = poll_conn.inputStream
            if (poll_conn.responseCode != 200) return null
            val lastmessage_id = poll_conn.getHeaderField("X-Appelflap-Last-Message-ID") ?: return null
            new_cursor_pos = lastmessage_id.toLong()
            pushmessages = JSONArray(streamToString(thejson_stream))
        } catch (e: MalformedURLException) {
            log.e("Malformed URL: $e")
        } catch (e: IOException) {
            log.e("Could not connect/fetch: $e")
        } catch (e: JSONException) {
            log.e("Could not parse JSON: $e")
        } finally {
            poll_conn?.disconnect()
        }
        return if (new_cursor_pos != null && pushmessages != null) OnePollResult(
            new_cursor_pos, pushmessages
        ) else null
    }

    override fun onPostExecute(messages_per_scope: JSONObject) {
        val scopes = messages_per_scope.keys()
        if (!scopes.hasNext()) return

        // now we know there are messages, it's time to boot/dereference Gecko and deliver them
        val grm = GeckoRuntimeManager.getInstance(Appelflap.get(context), null)
        val pusher = grm.geckoRuntime.webPushController
        while (scopes.hasNext()) {
            val scope = scopes.next()
            val messages = messages_per_scope.optJSONArray(scope)
            messages?.also {
                for (i in 0 until it.length()) {
                    val message = it.getJSONObject(i)
                    pusher.onPushEvent(
                        scope, message.toString().toByteArray(Charsets.UTF_8)
                    )
                }
            }
        }
        caller.jobFinished(jobparms, false)
    }

    inner class OnePollResult(
            var new_cursor_pos: Long, var pushmessages: JSONArray)
}