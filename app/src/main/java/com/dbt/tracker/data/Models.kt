package com.dbt.tracker.data

/** A single money movement. [amount] is always positive; direction lives in [isCredit]. */
data class Txn(
    val id: Long = 0L,
    val ts: Long,
    val amount: Double,
    val isCredit: Boolean,
    val merchant: String,
    val category: String,
    val account: String = "",
    /** Balance the bank reported in this SMS, when it included one. Anchors the running balance. */
    val balanceAfter: Double? = null,
    val refNo: String? = null,
    val channel: String = Channel.OTHER,
    val source: String = Source.STATEMENT,
    val raw: String = "",
    /**
     * Set when the category could not be read from the payee and was instead deduced from a
     * nearby app SMS, e.g. a Rapido ride OTP explaining a payment to a rider's personal QR.
     * Holds the app name so the UI can show its reasoning rather than assert it silently.
     */
    val inferredFrom: String? = null
) {
    val signed: Double get() = if (isCredit) amount else -amount
}

/** One account the app has seen in your messages, and how much it knows about it. */
data class AccountStat(
    val account: String,
    val channel: String,
    val txnCount: Int,
    val balanceSightings: Int
)

/** The workings behind the balance figure, surfaced so a wrong number can be explained. */
data class BalanceDiag(
    val primaryAccount: String,
    val accountExplicitlyChosen: Boolean,
    val anchorTs: Long?,
    val anchorBalance: Double?,
    val anchorAccount: String?,
    val txnsSinceAnchor: Int,
    val netSinceAnchor: Double,
    val accounts: List<AccountStat>
)

/** A non-monetary SMS that dates an activity: a ride starting, an order being placed. */
data class Signal(
    val ts: Long,
    val category: String,
    val label: String,
    val raw: String = ""
)

object Channel {
    const val UPI = "UPI"
    const val CARD = "Card"
    const val CREDIT_CARD = "Credit Card"
    const val ATM = "ATM"
    const val BANK = "Bank Transfer"
    const val CASH = "Cash"
    const val OTHER = "Other"
}

object Source {
    /** Imported from a downloaded bank statement. Authoritative. */
    const val STATEMENT = "Statement"

    /** Typed in by hand, for cash that never touched the account. */
    const val MANUAL = "Manual"
}

/** Everything the nightly report shows, computed fresh from the transaction table. */
data class DayReport(
    val dayStart: Long,
    val spent: Double,
    val credited: Double,
    val netFlow: Double,
    val txnCount: Int,
    val balance: Double?,
    val balanceIsEstimated: Boolean,
    val avg7: Double,
    val avg30: Double,
    val vsAvg30Pct: Double?,
    val byCategory: List<CategorySlice>,
    val topMerchants: List<MerchantSlice>,
    val largest: Txn?,
    val mtdSpent: Double,
    val mtdCredited: Double,
    /**
     * The SIP portion of [mtdSpent]. Counted in the total, as intended, but reported
     * separately so a month can be read as consumption plus saving rather than one figure.
     */
    val sipMtd: Double = 0.0,
    val projectedMonthEnd: Double,
    val monthlyBudget: Double?,
    val budgetUsedPct: Double?,
    val runwayDays: Double?,
    val noSpendDaysThisMonth: Int,
    val daysElapsedInMonth: Int,
    val daysInMonth: Int,
    val flags: List<Flag>,
    val txns: List<Txn>,
    /** Daily spend for the 30 days ending on this report's day. */
    val trend: List<DayPoint> = emptyList(),
    /** Daily spend for each elapsed day of this month, for the cumulative view. */
    val monthSeries: List<DayPoint> = emptyList()
)

/** One day on the spend timeline. Days with no spending are present with zero. */
data class DayPoint(val dayStart: Long, val spent: Double)

data class CategorySlice(val category: String, val amount: Double, val count: Int, val share: Double)

data class MerchantSlice(val merchant: String, val amount: Double, val count: Int)

/** A category with the payees inside it, for the drill-down on the Spend tab. */
data class Bucket(
    val category: String,
    val amount: Double,
    val count: Int,
    val share: Double,
    val merchants: List<MerchantSlice>
)

/** Windows the Spend tab can be viewed over. */
enum class SpendPeriod(val label: String) {
    THIS_MONTH("This month"),
    LAST_30("Last 30 days"),
    LAST_MONTH("Last month"),
    ALL("All time")
}

data class SpendView(
    val period: SpendPeriod,
    val fromTs: Long,
    val toTs: Long,
    val total: Double,
    val received: Double,
    val txnCount: Int,
    val dailyAverage: Double,
    val buckets: List<Bucket>
)

enum class Severity { INFO, WARN, CRITICAL }

data class Flag(val severity: Severity, val title: String, val detail: String)
