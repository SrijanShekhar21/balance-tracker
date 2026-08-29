package com.dbt.tracker.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dbt.tracker.data.BalanceDiag
import com.dbt.tracker.data.Categories
import com.dbt.tracker.data.Channel
import com.dbt.tracker.data.DayReport
import com.dbt.tracker.data.Prefs
import com.dbt.tracker.data.Repo
import com.dbt.tracker.data.SpendPeriod
import com.dbt.tracker.data.SpendView
import com.dbt.tracker.data.Source
import com.dbt.tracker.data.Txn
import com.dbt.tracker.report.Notifications
import com.dbt.tracker.report.ReportEngine
import com.dbt.tracker.report.ReportScheduler
import com.dbt.tracker.report.SpendAnalysis
import com.dbt.tracker.statement.StatementImporter
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
    val liveAlerts: Boolean,
    val dailyReminder: Boolean,
    val nextReportAt: Long?
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
    var diagnostics by mutableStateOf<BalanceDiag?>(null)
        private set

    /** Last day any imported statement covers. Everything after it is simply unknown. */
    var coveredUntil by mutableStateOf<Long?>(null)
        private set

    /** Result of the most recent import, kept so warnings stay on screen to be read. */
    var lastImport by mutableStateOf<StatementImporter.Outcome?>(null)

    /** Set while waiting for the user to supply a password for [pendingUri]. */
    var askingPassword by mutableStateOf(false)
    var passwordFailed by mutableStateOf(false)
        private set
    private var pendingUri: Uri? = null

    // ------------------------------------------------------------------ spend tab

    var spendPeriod by mutableStateOf(SpendPeriod.THIS_MONTH)
        private set
    var spendView by mutableStateOf<SpendView?>(null)
        private set

    /** Only one category is open at a time, so the page stays a summary rather than a dump. */
    var expandedCategory by mutableStateOf<String?>(null)
        private set
    var expandedMerchant by mutableStateOf<String?>(null)
        private set
    var merchantTxns by mutableStateOf<List<Txn>>(emptyList())
        private set

    fun selectPeriod(p: SpendPeriod) {
        spendPeriod = p
        collapseDrilldown()
        loadSpend()
    }

    fun toggleCategory(category: String) {
        expandedCategory = if (expandedCategory == category) null else category
        expandedMerchant = null
        merchantTxns = emptyList()
    }

    fun showMerchant(category: String, merchant: String) {
        if (expandedMerchant == merchant) {
            expandedMerchant = null
            merchantTxns = emptyList()
            return
        }
        expandedMerchant = merchant
        val view = spendView ?: return
        viewModelScope.launch {
            merchantTxns = withContext(Dispatchers.IO) {
                repo.merchantTxns(view.fromTs, view.toTs, category, merchant)
            }
        }
    }

    private fun collapseDrilldown() {
        expandedCategory = null
        expandedMerchant = null
        merchantTxns = emptyList()
    }

    private fun loadSpend() {
        viewModelScope.launch {
            spendView = withContext(Dispatchers.IO) {
                SpendAnalysis.build(getApplication(), spendPeriod)
            }
        }
    }

    /** Set when an imported statement had no balance column and none is on record. */
    var askingBalance by mutableStateOf(false)
    private var balanceAnchorTs = 0L

    private data class Loaded(
        val report: DayReport,
        val txns: List<Txn>,
        val count: Int,
        val triage: List<Txn>,
        val diag: BalanceDiag,
        val covered: Long?
    )

    /**
     * First load. Opens on the most recent day the data actually covers rather than on today,
     * because a statement usually ends a day or two back and landing on an empty screen reads
     * as though nothing was imported.
     */
    fun start() {
        viewModelScope.launch {
            val covered = withContext(Dispatchers.IO) { repo.lastStatementDay() }
            if (covered != null) viewDay = minOf(Days.startOfDay(covered), Days.todayStart())
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                Loaded(
                    report = ReportEngine.build(getApplication(), viewDay),
                    txns = repo.recent(500),
                    count = repo.count(),
                    triage = repo.needsTriage(Days.plusDays(Days.todayStart(), -TRIAGE_WINDOW_DAYS)),
                    diag = repo.balanceDiagnostics(prefs),
                    covered = repo.lastStatementDay()
                )
            }
            report = data.report
            allTxns = data.txns
            txnCount = data.count
            triage = data.triage
            diagnostics = data.diag
            coveredUntil = data.covered
            selected = selected intersect data.triage.map { it.id }.toSet()
            loadSpend()
        }
    }

    // ------------------------------------------------------------------ import

    /**
     * Reads a statement the user picked. Overlapping periods are replaced rather than added to,
     * so importing the current month repeatedly through the month is the intended way to use it.
     */
    fun importStatement(uri: Uri, password: String? = null) {
        if (busy) return
        busy = true
        pendingUri = uri
        // A remembered password is tried first, so an encrypted file usually imports in one tap.
        val pw = password ?: prefs.statementPassword.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                StatementImporter.import(getApplication(), uri, pw)
            }
            busy = false
            lastImport = outcome

            if (outcome.needsPassword) {
                // Distinguish "we have not asked yet" from "the saved one is wrong".
                passwordFailed = pw != null
                askingPassword = true
                return@launch
            }

            askingPassword = false
            pendingUri = null
            message = when {
                !outcome.ok -> outcome.error ?: "Could not import that file"
                outcome.replaced > 0 ->
                    "${outcome.imported} transactions imported, replacing ${outcome.replaced} from before"
                else -> "${outcome.imported} transactions imported"
            }
            if (outcome.ok) {
                // Jump to the newest day the statement covers, so the screen is not blank when
                // the statement ends before today.
                viewDay = minOf(Days.startOfDay(outcome.toTs), Days.todayStart())
                if (outcome.needsBalance) {
                    balanceAnchorTs = outcome.toTs + 1000L
                    askingBalance = repo.currentBalance(prefs) == null
                }
            }
            refresh()
        }
    }

    fun submitPassword(password: String, remember: Boolean) {
        if (remember) prefs.statementPassword = password
        askingPassword = false
        pendingUri?.let { importStatement(it, password) }
    }

    fun cancelPassword() {
        askingPassword = false
        pendingUri = null
    }

    /** Records the balance as at the end of the imported statement. */
    fun submitBalance(amount: Double) {
        prefs.setOpeningBalanceAt(amount, balanceAnchorTs)
        askingBalance = false
        message = "Balance recorded"
        refresh()
    }

    val savedPassword: String get() = prefs.statementPassword

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

    // ------------------------------------------------------------------ browsing

    fun showDay(dayStart: Long) {
        viewDay = minOf(dayStart, Days.todayStart())
        refresh()
    }

    fun shiftDay(days: Int) = showDay(Days.plusDays(viewDay, days))

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

    /** Cash never reaches a bank statement, so it has to be entered by hand. */
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

    fun clearEverything() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearTransactions() }
            lastImport = null
            message = "All transactions removed"
            refresh()
        }
    }

    fun setPrimaryAccount(account: String) {
        prefs.primaryAccount = account
        message = if (account.isBlank()) "Tracking whichever account is most active"
        else "Balance now tracks account $account"
        refresh()
    }

    // ------------------------------------------------------------------ settings

    private fun readSettings() = SettingsState(
        monthlyBudget = prefs.monthlyBudget,
        lowBalance = prefs.lowBalanceThreshold,
        largeTxn = prefs.largeTxnThreshold,
        reportHour = prefs.reportHour,
        reportMinute = prefs.reportMinute,
        liveAlerts = prefs.liveAlerts,
        dailyReminder = prefs.dailyReminder,
        nextReportAt = ReportScheduler.nextFireAt(getApplication())
    )

    fun updateSettings(block: Prefs.() -> Unit) {
        prefs.block()
        settings = readSettings()
        ReportScheduler.scheduleNext(getApplication())
        refresh()
    }

    val categories: List<String> get() = Categories.PICKABLE

    private companion object {
        /** Older spends are left alone; triage is about keeping recent data honest. */
        const val TRIAGE_WINDOW_DAYS = 45
    }
}
