package com.dbt.tracker

import android.app.Application
import com.dbt.tracker.report.Notifications
import com.dbt.tracker.report.ReportScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // Re-books the nightly report on every cold start, which also repairs the schedule
        // if the OS dropped the queued work.
        ReportScheduler.scheduleNext(this)
    }
}
