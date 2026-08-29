package com.dbt.tracker.report

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dbt.tracker.MainActivity
import com.dbt.tracker.R
import com.dbt.tracker.data.DayReport
import com.dbt.tracker.data.Severity
import com.dbt.tracker.data.Txn
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

object Notifications {

    private const val CH_REPORT = "daily_report"
    private const val CH_LIVE = "live_txn"
    private const val ID_REPORT = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        mgr.createNotificationChannel(
            NotificationChannel(CH_REPORT, "Daily report", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Your end-of-day spending summary" }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Transaction alerts", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "A quiet ping as each payment is recorded" }
        )
    }

    fun showDailyReport(context: Context, r: DayReport) {
        ensureChannels(context)

        val balancePart = r.balance?.let { " · ${Money.rupees(it)} left" } ?: ""
        val title = "${Money.rupees(r.spent)} spent today$balancePart"

        val n = NotificationCompat.Builder(context, CH_REPORT)
            .setSmallIcon(R.drawable.ic_stat_report)
            .setContentTitle(title)
            .setContentText(oneLiner(r))
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullSummary(r)))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        notify(context, ID_REPORT, n)
    }

    fun showLiveTxn(context: Context, txn: Txn) {
        ensureChannels(context)
        val verb = if (txn.isCredit) "Received" else "Paid"
        val n = NotificationCompat.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_report)
            .setContentTitle("$verb ${Money.rupees(txn.amount)}")
            .setContentText("${txn.merchant} · ${txn.category}")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // Distinct id per transaction so alerts stack instead of overwriting one another.
        notify(context, (txn.id % 100_000).toInt() + 2000, n)
    }

    private fun notify(context: Context, id: Int, n: Notification) {
        // Silently skipped when the user has not granted POST_NOTIFICATIONS; the report is
        // still available inside the app.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // ------------------------------------------------------------------ copy

    private fun oneLiner(r: DayReport): String {
        val vs = r.vsAvg30Pct
        return when {
            r.txnCount == 0 -> "No transactions today"
            vs == null -> "${r.txnCount} transactions"
            vs > 5 -> "${r.txnCount} txns · ${vs.roundToInt()}% above your daily average"
            vs < -5 -> "${r.txnCount} txns · ${abs(vs).roundToInt()}% below your daily average"
            else -> "${r.txnCount} txns · right on your daily average"
        }
    }

    /** The expanded notification body. Kept scannable: numbers first, then what needs attention. */
    fun fullSummary(r: DayReport): String {
        val sb = StringBuilder()
        sb.append(Days.full(r.dayStart)).append('\n').append('\n')

        sb.append("Spent      ${Money.rupees(r.spent)}  (${r.txnCount} txns)\n")
        sb.append("Received   ${Money.rupees(r.credited)}\n")
        sb.append("Net        ${Money.signed(r.netFlow)}\n")
        r.balance?.let {
            sb.append("Balance    ${Money.rupees(it)}")
            if (r.balanceIsEstimated) sb.append(" (est.)")
            sb.append('\n')
        }

        if (r.avg30 > 0) {
            sb.append("\nDaily average ${Money.rupees(r.avg30)} over 30 days")
            r.vsAvg30Pct?.let { pct ->
                val word = if (pct >= 0) "above" else "below"
                sb.append(" · today is ${abs(pct).roundToInt()}% $word")
            }
            sb.append('\n')
        }

        if (r.byCategory.isNotEmpty()) {
            sb.append("\nWhere it went\n")
            r.byCategory.take(5).forEach {
                sb.append("  ${it.category}  ${Money.rupees(it.amount)}  (${(it.share * 100).roundToInt()}%)\n")
            }
        }

        sb.append("\nThis month ${Money.rupees(r.mtdSpent)} over ${r.daysElapsedInMonth} days")
        r.monthlyBudget?.let { b ->
            if (b > 0) sb.append(" · ${(r.mtdSpent / b * 100).roundToInt()}% of budget")
        }
        sb.append("\nProjected month end ${Money.rupees(r.projectedMonthEnd)}\n")

        r.runwayDays?.let { sb.append("Runway about ${it.roundToInt()} days at this pace\n") }

        val flags = r.flags.filter { it.severity != Severity.INFO }
        if (flags.isNotEmpty()) {
            sb.append("\nNeeds attention\n")
            flags.take(5).forEach { sb.append("  • ${it.title} — ${it.detail}\n") }
        }

        return sb.toString().trimEnd()
    }
}
