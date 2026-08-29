package com.dbt.tracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Money {

    /**
     * Formats with Indian digit grouping: the last three digits, then pairs.
     * 1234567.5 renders as 12,34,567.50, not 1,234,567.50.
     */
    fun rupees(amount: Double, decimals: Boolean = false): String {
        val negative = amount < 0
        val abs = abs(amount)
        val whole = abs.toLong()
        val paise = ((abs - whole) * 100).roundToLong()

        val digits = whole.toString()
        val grouped = if (digits.length <= 3) {
            digits
        } else {
            val last3 = digits.takeLast(3)
            val rest = digits.dropLast(3)
            val pairs = StringBuilder()
            var i = rest.length
            while (i > 0) {
                val start = maxOf(0, i - 2)
                if (pairs.isNotEmpty()) pairs.insert(0, ",")
                pairs.insert(0, rest.substring(start, i))
                i = start
            }
            "$pairs,$last3"
        }

        val sign = if (negative) "-" else ""
        return if (decimals) "$sign₹$grouped.${paise.toString().padStart(2, '0')}"
        else "$sign₹$grouped"
    }

    /** Compact form for tight spaces: ₹1.2L, ₹45.3k. */
    fun short(amount: Double): String {
        val abs = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 10_000_000 -> "$sign₹%.2fCr".format(abs / 10_000_000)
            abs >= 100_000 -> "$sign₹%.2fL".format(abs / 100_000)
            abs >= 1_000 -> "$sign₹%.1fk".format(abs / 1_000)
            else -> rupees(amount)
        }
    }

    fun signed(amount: Double): String =
        if (amount >= 0) "+${rupees(amount)}" else rupees(amount)
}

object Days {

    private val dayFmt = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)
    private val fullFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.ENGLISH)
    private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)

    const val MS = 86_400_000L

    fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun todayStart(): Long = startOfDay(System.currentTimeMillis())

    /** Uses the calendar rather than fixed millisecond arithmetic so DST cannot drift the day. */
    fun plusDays(dayStart: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    fun startOfMonth(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dayOfMonth(ts: Long): Int = field(ts, Calendar.DAY_OF_MONTH)

    fun daysInMonth(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }
        .getActualMaximum(Calendar.DAY_OF_MONTH)

    fun hourOf(ts: Long): Int = field(ts, Calendar.HOUR_OF_DAY)

    private fun field(ts: Long, f: Int): Int =
        Calendar.getInstance().apply { timeInMillis = ts }.get(f)

    fun label(ts: Long): String = when (startOfDay(ts)) {
        todayStart() -> "Today"
        plusDays(todayStart(), -1) -> "Yesterday"
        else -> dayFmt.format(ts)
    }

    fun full(ts: Long): String = fullFmt.format(ts)
    fun time(ts: Long): String = timeFmt.format(ts)
    fun month(ts: Long): String = monthFmt.format(ts)
}
