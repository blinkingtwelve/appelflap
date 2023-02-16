package org.nontrivialpursuit.appelflap.webpushpoll

import android.app.job.JobParameters
import android.app.job.JobService
import org.nontrivialpursuit.appelflap.Logger

class PollService : JobService() {

    val log = Logger(this)

    override fun onStartJob(jobParameters: JobParameters): Boolean {
        log.i("Start webpush poll job")
        val appcontext = application.applicationContext
        @Suppress("DEPRECATION") PollTask(appcontext, jobParameters, this).execute()
        return true // we're scheduling work on a spin-off thread.
    }

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        log.i("Received signal to abort the job")
        // ignore; majority of the time spent on the job is in the network call which we can't easily interrupt anyways
        return false
    }
}