package com.dbt.tracker.report

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dbt.tracker.data.Prefs
import com.dbt.tracker.util.Days
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Fires once at the configured hour, posts the summary, then books itself for tomorrow.
 *
 * A self-rescheduling one-shot is used rather than PeriodicWorkRequest because periodic work
 * anchors to whenever it was first enqueued and drifts from a wall-clock time; this keeps the
 * report pinned to the hour the user chose, and picks up a changed hour on the next run.
 */
class DailyReportWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val report = ReportEngine.build(applicationContext, Days.todayStart())
            Notifications.showDailyReport(applicationContext, report)
            Result.success()
        } catch (_: Exception) {
            Result.success() // never retry-loop a report; the next day's run supersedes it
        } finally {
            ReportScheduler.scheduleNext(applicationContext)
        }
    }
}

object ReportScheduler {

    private const val WORK_NAME = "daily_report"

    fun scheduleNext(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.dailyReminder) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = millisUntil(prefs.reportHour, prefs.reportMinute)

        val request = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** When the next report will fire, so the setting can state it rather than imply it. */
    fun nextFireAt(context: Context): Long? {
        val prefs = Prefs(context)
        if (!prefs.dailyReminder) return null
        return System.currentTimeMillis() + millisUntil(prefs.reportHour, prefs.reportMinute)
    }

    /** Milliseconds from now until the next occurrence of [hour]:[minute]. */
    private fun millisUntil(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now
    }
}

/** Work queues do not survive a reboot, so the next report is booked again on boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReportScheduler.scheduleNext(context.applicationContext)
    }
}
