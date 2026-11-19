package com.nomi.app.integration.logging

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.nomi.app.NomiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Process-death-resilient trigger for offline-queued food logs.
 *
 * [PendingLoggingManager]'s in-process [android.net.ConnectivityManager.NetworkCallback] drains
 * queued meals immediately while Nomi is alive. If Android terminates the process while the device
 * is offline, this persisted [JobService] wakes Nomi as soon as any network returns and drains the
 * remaining queue.
 */
class PendingQueueJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var drainJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as? NomiApplication ?: return false
        drainJob?.cancel()
        drainJob = serviceScope.launch {
            var needsReschedule = false
            try {
                app.container.pendingLoggingManager.drainQueue()
                val remaining = app.container.repository.pendingFoodLogsByStatuses(
                    listOf(PendingLoggingStatus.QUEUED_OFFLINE, PendingLoggingStatus.PROCESSING),
                )
                needsReschedule = remaining.isNotEmpty()
            } finally {
                jobFinished(params, needsReschedule)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        drainJob?.cancel()
        return true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 4210

        fun scheduleWhenConnected(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return
            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context.applicationContext, PendingQueueJobService::class.java),
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            runCatching { scheduler.schedule(jobInfo) }
        }

        fun cancel(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return
            runCatching { scheduler.cancel(JOB_ID) }
        }
    }
}
