package org.nontrivialpursuit.appelflap.webpushpoll

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.nontrivialpursuit.appelflap.BuildConfig
import org.nontrivialpursuit.appelflap.Logger

object PollScheduler {
    val log = Logger(this)
    const val WEBPUSH_POLL_JOBID = 100
    const val WEBPUSH_POLL_JOBID_ONESHOT = 101
    private const val ONE_QTR = 15 * 1000L

    fun schedule(app: Application, oneshot: Boolean) {
        val job_ref = ComponentName(app, PollService::class.java)
        val jobScheduler =
            app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val jobinfobuilder = JobInfo.Builder(
            if (oneshot) WEBPUSH_POLL_JOBID_ONESHOT else WEBPUSH_POLL_JOBID,
            job_ref
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        if (!oneshot) {
            jobinfobuilder.setPeriodic(BuildConfig.WEBPUSH_POLL_INTERVAL_QTR * ONE_QTR)
                .setPersisted(true)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            jobinfobuilder.setRequiresBatteryNotLow(true)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            jobinfobuilder.setEstimatedNetworkBytes(4096L, 1024L)
        }
        if (jobScheduler.schedule(jobinfobuilder.build()) == JobScheduler.RESULT_FAILURE) {
            log.e("PollScheduler: failed to schedule webpush polling job")
        }
    }
}