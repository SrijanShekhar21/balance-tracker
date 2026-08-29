package com.dbt.tracker.data

import android.content.Context

/** Small typed wrapper over SharedPreferences. Null means "not configured yet". */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("dbt", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var backfillDone: Boolean
        get() = sp.getBoolean("backfill_done", false)
        set(v) = sp.edit().putBoolean("backfill_done", v).apply()

    var monthlyBudget: Double?
        get() = sp.getFloat("monthly_budget", -1f).takeIf { it >= 0f }?.toDouble()
        set(v) = sp.edit().putFloat("monthly_budget", v?.toFloat() ?: -1f).apply()

    var lowBalanceThreshold: Double?
        get() = sp.getFloat("low_balance", -1f).takeIf { it >= 0f }?.toDouble()
        set(v) = sp.edit().putFloat("low_balance", v?.toFloat() ?: -1f).apply()

    /** Single transaction at or above this amount raises a flag. */
    var largeTxnThreshold: Double?
        get() = sp.getFloat("large_txn", -1f).takeIf { it >= 0f }?.toDouble()
        set(v) = sp.edit().putFloat("large_txn", v?.toFloat() ?: -1f).apply()

    /** Manual starting balance, used only until the bank stamps a real one on an SMS. */
    var openingBalance: Double?
        get() = sp.getFloat("opening_balance", -1f).takeIf { it >= 0f }?.toDouble()
        set(v) = sp.edit().putFloat("opening_balance", v?.toFloat() ?: -1f).apply()

    var openingBalanceTs: Long
        get() = sp.getLong("opening_balance_ts", 0L)
        set(v) = sp.edit().putLong("opening_balance_ts", v).apply()

    var reportHour: Int
        get() = sp.getInt("report_hour", 22)
        set(v) = sp.edit().putInt("report_hour", v).apply()

    var reportMinute: Int
        get() = sp.getInt("report_minute", 0)
        set(v) = sp.edit().putInt("report_minute", v).apply()

    /** How far back the first inbox scan reaches, in days. */
    var backfillDays: Int
        get() = sp.getInt("backfill_days", 120)
        set(v) = sp.edit().putInt("backfill_days", v).apply()

    /** Also parse alerts from non-SBI senders (wallets, other banks). */
    var includeAllSenders: Boolean
        get() = sp.getBoolean("all_senders", true)
        set(v) = sp.edit().putBoolean("all_senders", v).apply()

    /** Notify on every transaction as it lands, not just the nightly summary. */
    var liveAlerts: Boolean
        get() = sp.getBoolean("live_alerts", false)
        set(v) = sp.edit().putBoolean("live_alerts", v).apply()

    fun setOpeningBalanceNow(amount: Double) {
        openingBalance = amount
        openingBalanceTs = System.currentTimeMillis()
    }
}
