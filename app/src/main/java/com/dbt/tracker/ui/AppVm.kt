package com.dbt.tracker.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dbt.tracker.data.Categories
import com.dbt.tracker.data.Channel
import com.dbt.tracker.data.DayReport
import com.dbt.tracker.data.Prefs
import com.dbt.tracker.data.Repo
import com.dbt.tracker.data.Source
import com.dbt.tracker.data.Txn
import com.dbt.tracker.report.Notifications
import com.dbt.tracker.report.ReportEngine
import com.dbt.tracker.report.ReportScheduler
import com.dbt.tracker.sms.Ingest
import com.dbt.tracker.util.Days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val monthlyBudget: Double?,
    val lowBalance: Double?,
    val largeTxn: Double?,
    val reportHour: Int,
    val reportMinute: Int,
    val backfillDays: Int,
    val includeAllSenders: Boolean,
    val liveAlerts: Boolean,
    val openingBalance: Double?
)

class AppVm(app: Application) : AndroidViewModel(app) {

    private val repo = Repo(app)
    private val prefs = Prefs(app)

    var report by mutableStateOf<DayReport?>(null)
        private set
    var viewDay by mutableStateOf(Days.todayStart())
        private set
    var allTxns by mutableStateOf<List<Txn>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var txnCount by mutableStateOf(0)
        private set
    var settings by mutableStateOf(readSettings())
        private set

    /** Spends the app could not place, awaiting a one-tap decision from the user. */
    var triage by mutableStateOf<List<Txn>>(emptyList())
        private set
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set

    private data class Loaded(
        val report: DayReport,
        val txns: List<Txn>,
        val count: Int,
        val triage: List<Txn>
    )

    fun refresh() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                Loaded(
                    report = ReportEngine.build(getApplication(), viewDay),
                    txns = repo.recent(500),
                    count = repo.count(),
                    triage = repo.needsTriage(Days.plusDays(Days.todayStart(), -TRIAGE_WINDOW_DAYS))
                )
            }
            report = data.report
            allTxns = data.txns
            txnCount = data.count
            triage = data.triage
            // Drop ticks for anything that has since been categorised elsewhere.
            selected = selected intersect data.triage.map { it.id }.toSet()
        }
    }

    // ------------------------------------------------------------------ triage

    fun toggleSelect(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll() {
        selected = triage.map { it.id }.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
    }

    /** Assigns one category to everything ticked. The fast path for a month of rider payments. */
    fun assignSelected(category: String) {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setCategoryBulk(ids, category) }
            selected = emptySet()
            message = "${ids.size} moved to $category"
            refresh()
        }
    }

    fun showDay(dayStart: Long) {
        // Never let the browser walk into days that have not happened yet.
        viewDay = minOf(dayStart, Days.todayStart())
        refresh()
    }

    fun shiftDay(days: Int) = showDay(Days.plusDays(viewDay, days))

    /**
     * Re-reads the SMS inbox. Safe to run repeatedly: already-recorded transactions are
     * rejected by de-duplication, so this doubles as the fix for anything missed while the
     * app was uninstalled or permissions were off.
     */
    fun rescan() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                Ingest.backfill(getApplication(), prefs.backfillDays)
            }
            prefs.backfillDone = true
            busy = false
            message = if (added > 0) "Imported $added new transactions" else "No new transactions found"
            refresh()
        }
    }

    fun setCategory(txn: Txn, category: String, remember: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setCategory(txn.id, category, remember) }
            message = if (remember) "All ${txn.merchant} payments moved to $category" else "Moved to $category"
            refresh()
        }
    }

    fun deleteTxn(txn: Txn) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(txn.id) }
            message = "Deleted"
            refresh()
        }
    }

    /** Cash spends never appear in SMS, so they have to be entered by hand. */
    fun addManual(amount: Double, merchant: String, category: String, isCredit: Boolean, ts: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.insert(
                    Txn(
                        ts = ts,
                        amount = amount,
                        isCredit = isCredit,
                        merchant = merchant.ifBlank { if (isCredit) "Cash in" else "Cash" },
                        category = category,
                        channel = Channel.CASH,
                        source = Source.MANUAL,
                        raw = "Added manually"
                    )
                )
            }
            message = "Added"
            refresh()
        }
    }

    fun previewTonightsReport() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                ReportEngine.build(getApplication(), Days.todayStart())
            }
            Notifications.showDailyReport(getApplication(), r)
            message = "Sent to your notification shade"
        }
    }

    // ------------------------------------------------------------------ settings

    private fun readSettings() = SettingsState(
        monthlyBudget = prefs.monthlyBudget,
        lowBalance = prefs.lowBalanceThreshold,
        largeTxn = prefs.largeTxnThreshold,
        reportHour = prefs.reportHour,
        reportMinute = prefs.reportMinute,
        backfillDays = prefs.backfillDays,
        includeAllSenders = prefs.includeAllSenders,
        liveAlerts = prefs.liveAlerts,
        openingBalance = prefs.openingBalance
    )

    fun updateSettings(block: Prefs.() -> Unit) {
        prefs.block()
        settings = readSettings()
        ReportScheduler.scheduleNext(getApplication())
        refresh()
    }

    fun setOpeningBalance(amount: Double) {
        prefs.setOpeningBalanceNow(amount)
        settings = readSettings()
        message = "Starting balance set"
        refresh()
    }

    val categories: List<String> get() = Categories.PICKABLE

    /** On the very first launch with permission granted, import history without being asked. */
    fun firstRunScanIfNeeded() {
        if (!prefs.backfillDone) rescan()
    }

    private companion object {
        /** Older spends are left alone; triage is about keeping recent data honest. */
        const val TRIAGE_WINDOW_DAYS = 45
    }
}
