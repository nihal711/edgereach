package dev.nihal.edgereach.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.nihal.edgereach.data.ServiceGuard

/** Every 15 minutes, checks the accessibility service is still on and turns it back on if not. */
class GuardJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        ServiceGuard.check(this, "periodic check")
        return false
    }

    override fun onStopJob(params: JobParameters?) = false

    companion object {
        private const val ID = 7101

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(ID) != null) return
            runCatching {
                js.schedule(
                    JobInfo.Builder(ID, ComponentName(ctx, GuardJob::class.java))
                        .setPeriodic(15 * 60 * 1000L)
                        .setPersisted(true)
                        .build(),
                )
            }
        }
    }
}

/** Runs after a reboot or an app update; starting the process runs the checks in App.onCreate. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ServiceGuard.check(context, intent?.action?.substringAfterLast('.')?.lowercase() ?: "boot")
        GuardJob.schedule(context)
    }
}
