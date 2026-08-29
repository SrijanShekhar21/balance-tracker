package com.dbt.tracker.report

import android.content.Context
import com.dbt.tracker.data.Categories
import com.dbt.tracker.data.CategorySlice
import com.dbt.tracker.data.DayReport
import com.dbt.tracker.data.Flag
import com.dbt.tracker.data.MerchantSlice
import com.dbt.tracker.data.Prefs
import com.dbt.tracker.data.Repo
import com.dbt.tracker.data.Severity
import com.dbt.tracker.data.Txn
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Builds the end-of-day picture from stored transactions. Everything is derived on demand
 * rather than snapshotted, so corrections to a category or a deleted duplicate are reflected
 * in past reports too.
 */
object ReportEngine {

    /** Below this, day-to-day noise is not worth flagging. */
    private const val NOISE_FLOOR = 200.0

    fun build(context: Context, dayStart: Long = Days.todayStart()): DayReport {
        val repo = Repo(context)
        val prefs = Prefs(context)

        val dayEnd = Days.plusDays(dayStart, 1)
        val today = repo.between(dayStart, dayEnd)

        val spent = today.filter { !it.isCredit }.sumOf { it.amount }
        val credited = today.filter { it.isCredit }.sumOf { it.amount }

        val balancePair = repo.currentBalance(prefs)

        // --- historical baselines -------------------------------------------------
        val firstTs = repo.firstTs()
        val avg30 = averageDailySpend(repo, dayStart, 30, firstTs)
        val avg7 = averageDailySpend(repo, dayStart, 7, firstTs)

        // --- month to date --------------------------------------------------------
        val monthStart = Days.startOfMonth(dayStart)
        val mtd = repo.between(monthStart, dayEnd)
        val mtdSpent = mtd.filter { !it.isCredit }.sumOf { it.amount }
        val mtdCredited = mtd.filter { it.isCredit }.sumOf { it.amount }

        val daysElapsed = Days.dayOfMonth(dayStart)
        val daysInMonth = Days.daysInMonth(dayStart)
        val projected = if (daysElapsed > 0) mtdSpent / daysElapsed * daysInMonth else 0.0

        val spendDaysThisMonth = mtd.filter { !it.isCredit }
            .map { Days.startOfDay(it.ts) }.distinct().size
        val noSpendDays = (daysElapsed - spendDaysThisMonth).coerceAtLeast(0)

        // --- breakdowns -----------------------------------------------------------
        val debits = today.filter { !it.isCredit }
        val byCategory = debits.groupBy { it.category }
            .map { (cat, list) ->
                val amt = list.sumOf { it.amount }
                CategorySlice(cat, amt, list.size, if (spent > 0) amt / spent else 0.0)
            }
            .sortedByDescending { it.amount }

        val topMerchants = debits.groupBy { it.merchant.ifBlank { "Unknown" } }
            .map { (m, list) -> MerchantSlice(m, list.sumOf { it.amount }, list.size) }
            .sortedByDescending { it.amount }
            .take(5)

        val runway = balancePair?.first?.let { bal ->
            val burn = if (avg30 > 0) avg30 else avg7
            if (burn > 0 && bal > 0) bal / burn else null
        }

        val report = DayReport(
            dayStart = dayStart,
            spent = spent,
            credited = credited,
            netFlow = credited - spent,
            txnCount = today.size,
            balance = balancePair?.first,
            balanceIsEstimated = balancePair?.second ?: true,
            avg7 = avg7,
            avg30 = avg30,
            vsAvg30Pct = if (avg30 > 0) (spent - avg30) / avg30 * 100 else null,
            byCategory = byCategory,
            topMerchants = topMerchants,
            largest = debits.maxByOrNull { it.amount },
            mtdSpent = mtdSpent,
            mtdCredited = mtdCredited,
            projectedMonthEnd = projected,
            monthlyBudget = prefs.monthlyBudget,
            budgetUsedPct = prefs.monthlyBudget?.let { if (it > 0) mtdSpent / it * 100 else null },
            runwayDays = runway,
            noSpendDaysThisMonth = noSpendDays,
            daysElapsedInMonth = daysElapsed,
            daysInMonth = daysInMonth,
            flags = emptyList(),
            txns = today
        )

        return report.copy(flags = detectFlags(repo, prefs, report, debits, dayStart))
    }

    /**
     * Mean spend per calendar day over the trailing [window] days, excluding [dayStart] itself.
     *
     * Days with no spending are counted as zero rather than skipped, so the figure answers
     * "what does a typical day cost me" instead of "what does a typical spending day cost me".
     * The divisor shrinks to the amount of history actually available, otherwise a three-day-old
     * install would compare today against a 30-day mean that is mostly padding.
     */
    private fun averageDailySpend(repo: Repo, dayStart: Long, window: Int, firstTs: Long?): Double {
        val from = Days.plusDays(dayStart, -window)
        val total = repo.between(from, dayStart).filter { !it.isCredit }.sumOf { it.amount }

        val trackedSince = firstTs?.let { Days.startOfDay(it) } ?: return 0.0
        val availableDays = ((dayStart - trackedSince) / Days.MS).toInt()
        val divisor = availableDays.coerceIn(1, window)
        return total / divisor
    }

    // ------------------------------------------------------------------ red flags

    private fun detectFlags(
        repo: Repo,
        prefs: Prefs,
        r: DayReport,
        debits: List<Txn>,
        dayStart: Long
    ): List<Flag> {
        val flags = mutableListOf<Flag>()

        prefs.lowBalanceThreshold?.let { threshold ->
            val bal = r.balance
            if (bal != null && bal < threshold) {
                flags += Flag(
                    Severity.CRITICAL,
                    "Balance below your floor",
                    "${Money.rupees(bal)} left, under the ${Money.rupees(threshold)} you set."
                )
            }
        }

        r.runwayDays?.let { days ->
            if (days < 7) {
                flags += Flag(
                    Severity.CRITICAL,
                    "About ${days.roundToInt()} days of runway",
                    "At your recent burn of ${Money.rupees(r.avg30)}/day, the balance runs low before month end."
                )
            } else if (days < 14) {
                flags += Flag(
                    Severity.WARN,
                    "Roughly ${days.roundToInt()} days of runway",
                    "Balance covers about two weeks at the current pace."
                )
            }
        }

        if (r.spent > NOISE_FLOOR && r.avg30 > 0 && r.spent > r.avg30 * 2) {
            val x = r.spent / r.avg30
            flags += Flag(
                Severity.WARN,
                "Today cost %.1fx your average".format(x),
                "${Money.rupees(r.spent)} against a ${Money.rupees(r.avg30)} daily norm."
            )
        }

        prefs.largeTxnThreshold?.let { limit ->
            debits.filter { it.amount >= limit }.forEach {
                flags += Flag(
                    Severity.WARN,
                    "Large payment: ${Money.rupees(it.amount)}",
                    "${it.merchant} at ${Days.time(it.ts)}."
                )
            }
        }

        r.monthlyBudget?.let { budget ->
            if (budget > 0) {
                if (r.mtdSpent > budget) {
                    flags += Flag(
                        Severity.CRITICAL,
                        "Monthly budget exceeded",
                        "${Money.rupees(r.mtdSpent)} spent of ${Money.rupees(budget)}, with ${r.daysInMonth - r.daysElapsedInMonth} days still to go."
                    )
                } else if (r.projectedMonthEnd > budget * 1.05) {
                    flags += Flag(
                        Severity.WARN,
                        "On pace to overshoot budget",
                        "Projecting ${Money.rupees(r.projectedMonthEnd)} against a ${Money.rupees(budget)} budget."
                    )
                }
            }
        }

        detectDuplicates(debits).forEach { flags += it }

        // Payments between midnight and 5am are worth a second look: they are either
        // impulse spending or someone else using the account.
        val lateNight = debits.filter { Days.hourOf(it.ts) < 5 }
        if (lateNight.isNotEmpty()) {
            flags += Flag(
                Severity.WARN,
                "${lateNight.size} late-night payment${if (lateNight.size > 1) "s" else ""}",
                lateNight.joinToString(", ") { "${it.merchant} ${Money.rupees(it.amount)}" } +
                    " between midnight and 5am."
            )
        }

        detectCategorySpike(repo, r, dayStart)?.let { flags += it }
        detectNewMerchants(repo, debits, dayStart).forEach { flags += it }

        if (r.netFlow < 0 && r.credited == 0.0 && r.spent > r.avg30 * 1.5 && r.spent > NOISE_FLOOR) {
            flags += Flag(
                Severity.INFO,
                "Outflow only today",
                "${Money.rupees(r.spent)} out, nothing in."
            )
        }

        if (r.spent == 0.0 && r.txnCount == 0) {
            flags += Flag(
                Severity.INFO,
                "No-spend day",
                "Nothing left the account today. That is ${r.noSpendDaysThisMonth} this month."
            )
        }

        return flags.sortedBy { it.severity.ordinal * -1 }
    }

    /** Same merchant, same amount, minutes apart: usually a double charge worth checking. */
    private fun detectDuplicates(debits: List<Txn>): List<Flag> {
        val out = mutableListOf<Flag>()
        debits.groupBy { it.merchant.lowercase() to it.amount }
            .filter { it.value.size > 1 }
            .forEach { (key, list) ->
                val sorted = list.sortedBy { it.ts }
                val close = sorted.zipWithNext().any { (a, b) -> abs(b.ts - a.ts) < 15 * 60_000L }
                if (close) {
                    out += Flag(
                        Severity.WARN,
                        "Possible double charge",
                        "${list.size} payments of ${Money.rupees(key.second)} to ${sorted[0].merchant} within minutes."
                    )
                }
            }
        return out
    }

    /** A single category blowing past its own norm explains an unusual day better than a total. */
    private fun detectCategorySpike(repo: Repo, r: DayReport, dayStart: Long): Flag? {
        if (r.byCategory.isEmpty()) return null
        val from = Days.plusDays(dayStart, -30)
        val history = repo.between(from, dayStart).filter { !it.isCredit }
        if (history.isEmpty()) return null

        val baseline = history.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } / 30.0 }

        val spike = r.byCategory.firstOrNull { slice ->
            val norm = baseline[slice.category] ?: 0.0
            slice.category != Categories.INVEST &&
                slice.amount > 300 && norm > 0 && slice.amount > norm * 3
        } ?: return null

        val norm = baseline[spike.category] ?: 0.0
        return Flag(
            Severity.WARN,
            "${spike.category} spiked",
            "${Money.rupees(spike.amount)} today against a ${Money.rupees(norm)} daily norm."
        )
    }

    /** First-time payees receiving real money are the classic signature of a mistake or a scam. */
    private fun detectNewMerchants(repo: Repo, debits: List<Txn>, dayStart: Long): List<Flag> =
        debits.filter { it.amount >= 2000 }
            .filter { txn ->
                val first = repo.merchantFirstSeen(txn.merchant)
                first != null && first >= dayStart
            }
            .distinctBy { it.merchant }
            .map {
                Flag(
                    Severity.INFO,
                    "First payment to ${it.merchant}",
                    "${Money.rupees(it.amount)} to a payee not seen before."
                )
            }
}
