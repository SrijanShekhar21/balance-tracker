package com.dbt.tracker.report

import android.content.Context
import com.dbt.tracker.data.Bucket
import com.dbt.tracker.data.MerchantSlice
import com.dbt.tracker.data.Repo
import com.dbt.tracker.data.SpendPeriod
import com.dbt.tracker.data.SpendView
import com.dbt.tracker.util.Days
import java.util.Calendar

/**
 * Builds the Spend tab: money grouped into categories, each holding the payees inside it.
 *
 * Separate from [ReportEngine] because it answers a different question. The report asks what
 * happened on one day; this asks where money goes over a period, which is the view you act on.
 */
object SpendAnalysis {

    fun build(context: Context, period: SpendPeriod): SpendView {
        val repo = Repo(context)
        val (from, to) = range(period, repo)

        val txns = repo.between(from, to)
        val debits = txns.filter { !it.isCredit }
        val total = debits.sumOf { it.amount }
        val received = txns.filter { it.isCredit }.sumOf { it.amount }

        val buckets = debits
            .groupBy { it.category }
            .map { (category, list) ->
                Bucket(
                    category = category,
                    amount = list.sumOf { it.amount },
                    count = list.size,
                    share = if (total > 0) list.sumOf { it.amount } / total else 0.0,
                    // Payees inside a category, largest first: this is what makes a category
                    // actionable, since "Groceries 8,400" alone tells you nothing to change.
                    merchants = list
                        .groupBy { it.merchant.ifBlank { "Unknown" } }
                        .map { (m, l) -> MerchantSlice(m, l.sumOf { it.amount }, l.size) }
                        .sortedByDescending { it.amount }
                )
            }
            .sortedByDescending { it.amount }

        // Days actually covered, so the average is not diluted by a period that has not
        // finished yet.
        val spanDays = (((minOf(to, System.currentTimeMillis()) - from) / Days.MS) + 1)
            .toInt().coerceAtLeast(1)

        return SpendView(
            period = period,
            fromTs = from,
            toTs = to,
            total = total,
            received = received,
            txnCount = debits.size,
            dailyAverage = total / spanDays,
            buckets = buckets
        )
    }

    private fun range(period: SpendPeriod, repo: Repo): Pair<Long, Long> {
        val today = Days.todayStart()
        val tomorrow = Days.plusDays(today, 1)
        return when (period) {
            SpendPeriod.THIS_MONTH -> Days.startOfMonth(today) to tomorrow
            SpendPeriod.LAST_30 -> Days.plusDays(today, -29) to tomorrow
            SpendPeriod.LAST_MONTH -> {
                val startOfThis = Days.startOfMonth(today)
                val startOfLast = Calendar.getInstance().apply {
                    timeInMillis = startOfThis
                    add(Calendar.MONTH, -1)
                }.timeInMillis
                startOfLast to startOfThis
            }
            SpendPeriod.ALL -> (repo.firstTs() ?: today) to tomorrow
        }
    }
}
