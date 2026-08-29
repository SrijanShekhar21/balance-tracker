package com.dbt.tracker.data

import android.content.Context

/** Small typed wrapper over SharedPreferences. Null means "not configured yet". */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("dbt", Context.MODE_PRIVATE)

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

    /**
     * Manual starting balance. Only consulted when no imported statement has supplied a real
     * closing balance, which after the first successful import is never.
     */
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

    /** Post a notification per transaction as it is imported, not just the nightly summary. */
    var liveAlerts: Boolean
        get() = sp.getBoolean("live_alerts", false)
        set(v) = sp.edit().putBoolean("live_alerts", v).apply()

    /** Last four digits of the account the balance should track. Blank means auto-detect. */
    var primaryAccount: String
        get() = sp.getString("primary_account", "") ?: ""
        set(v) = sp.edit().putString("primary_account", v).apply()

    fun setOpeningBalanceNow(amount: Double) {
        openingBalance = amount
        openingBalanceTs = System.currentTimeMillis()
    }
}
