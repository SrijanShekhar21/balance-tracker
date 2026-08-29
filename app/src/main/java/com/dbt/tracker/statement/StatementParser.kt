package com.dbt.tracker.statement

import java.util.Calendar
import java.util.Locale

/**
 * Reads a downloaded bank statement into transactions.
 *
 * Deliberately does not hard-code SBI's layout. Banks reorder columns, rename headers between
 * "Withdrawal" and "Debit", pad the top of the file with account details, and sometimes ship a
 * file called .xls that is really tab-separated text. So the parser locates the header row by
 * looking for columns that must exist in any statement, maps them by name, and reads what
 * follows -- which also means it works on statements from other banks without changes.
 *
 * Both the text formats and .xlsx funnel into [parseGrid], so there is one set of rules.
 */
object StatementParser {

    data class Row(
        val date: Long,
        val description: String,
        val ref: String,
        val debit: Double?,
        val credit: Double?,
        val balance: Double?
    )

    data class Result(
        val rows: List<Row>,
        val accountHint: String,
        val warnings: List<String>
    ) {
        val isEmpty get() = rows.isEmpty()
    }

    // Header synonyms, widest-known first. Matching is by substring on the lowercased cell.
    private val DATE_PRIMARY = listOf("txn date", "transaction date", "tran date", "post date")
    private val DATE_ANY = listOf("date")
    private val DESC = listOf("description", "narration", "particulars", "remarks", "transaction remarks", "details")
    private val REF = listOf("ref no", "ref.no", "reference", "cheque", "chq")
    private val DEBIT = listOf("withdrawal", "debit", "dr amount", "dr")
    private val CREDIT = listOf("deposit", "credit", "cr amount", "cr")
    private val BALANCE = listOf("closing balance", "balance", "running balance")
    private val AMOUNT = listOf("amount", "transaction amount")
    private val DRCR = listOf("dr / cr", "dr/cr", "type", "indicator", "cr/dr")

    fun parseText(text: String): Result {
        val lines = text.split("\n").map { it.trimEnd('\r') }
        val delimiter = sniffDelimiter(lines)
        val grid = lines.map { splitLine(it, delimiter) }
        return parseGrid(grid)
    }

    /**
     * @param grid every cell of the sheet, already unescaped
     * @return the transactions found, plus warnings for anything skipped
     */
    fun parseGrid(grid: List<List<String>>): Result {
        val warnings = mutableListOf<String>()

        val headerIdx = grid.indexOfFirst { looksLikeHeader(it) }
        if (headerIdx < 0) {
            return Result(
                emptyList(),
                accountHint(grid, grid.size),
                listOf(
                    "Could not find a header row. A statement needs columns for the date, " +
                        "the amount withdrawn and the amount deposited."
                )
            )
        }

        val header = grid[headerIdx].map { it.lowercase(Locale.ROOT).trim() }
        val cDate = pick(header, DATE_PRIMARY) ?: pick(header, DATE_ANY) ?: -1
        val cDesc = pick(header, DESC) ?: -1
        val cRef = pick(header, REF) ?: -1
        val cDebit = pick(header, DEBIT) ?: -1
        val cCredit = pick(header, CREDIT) ?: -1
        val cBalance = pick(header, BALANCE) ?: -1
        val cAmount = pick(header, AMOUNT) ?: -1
        val cDrCr = pick(header, DRCR) ?: -1

        if (cDate < 0) return Result(emptyList(), accountHint(grid, headerIdx), listOf("No date column found."))
        if (cDebit < 0 && cCredit < 0 && cAmount < 0) {
            return Result(emptyList(), accountHint(grid, headerIdx), listOf("No amount column found."))
        }

        val rows = mutableListOf<Row>()
        var skipped = 0

        for (i in (headerIdx + 1) until grid.size) {
            val cells = grid[i]
            if (cells.all { it.isBlank() }) continue

            val date = parseDate(cells.getOrNull(cDate).orEmpty())
            if (date == null) {
                // Footers, totals and the blank runs between sections all land here.
                if (cells.any { it.isNotBlank() }) skipped++
                continue
            }

            var debit = amount(cells.getOrNull(cDebit))
            var credit = amount(cells.getOrNull(cCredit))

            // Some banks use one amount column plus a Dr/Cr marker instead of two columns.
            if (debit == null && credit == null && cAmount >= 0) {
                val amt = amount(cells.getOrNull(cAmount))
                val marker = cells.getOrNull(cDrCr).orEmpty().lowercase(Locale.ROOT)
                if (amt != null) {
                    if (marker.startsWith("c") || marker.contains("credit")) credit = amt else debit = amt
                }
            }

            if (debit == null && credit == null) {
                skipped++
                continue
            }

            rows.add(
                Row(
                    date = date,
                    description = cells.getOrNull(cDesc).orEmpty().trim(),
                    ref = cells.getOrNull(cRef).orEmpty().trim(),
                    debit = debit,
                    credit = credit,
                    balance = amount(cells.getOrNull(cBalance))
                )
            )
        }

        if (rows.isEmpty()) warnings.add("Found the header but no transaction rows under it.")
        if (skipped > 0) warnings.add("$skipped line(s) skipped: no readable date or amount.")
        if (cBalance < 0) warnings.add("No balance column, so the balance will be derived rather than read.")

        return Result(rows.sortedBy { it.date }, accountHint(grid, headerIdx), warnings)
    }

    // ------------------------------------------------------------------ header

    /** A statement header always names a date and both directions of money. */
    private fun looksLikeHeader(cells: List<String>): Boolean {
        val low = cells.map { it.lowercase(Locale.ROOT) }
        val hasDate = low.any { c -> DATE_PRIMARY.any { c.contains(it) } || c.trim() == "date" || c.contains("date") }
        val hasOut = low.any { c -> DEBIT.any { c.contains(it) } }
        val hasIn = low.any { c -> CREDIT.any { c.contains(it) } }
        val hasAmount = low.any { c -> AMOUNT.any { c.contains(it) } }
        return hasDate && (hasOut || hasIn || hasAmount)
    }

    private fun pick(header: List<String>, keys: List<String>): Int? {
        for (key in keys) {
            val i = header.indexOfFirst { it.contains(key) }
            if (i >= 0) return i
        }
        return null
    }

    /** Account number is printed above the table; only the last four digits are kept. */
    private fun accountHint(grid: List<List<String>>, before: Int): String {
        val re = Regex("""(?i)acc(?:oun)?t\s*(?:no|number)?\s*[:.\-]?\s*[_xX*]*(\d{4,})""")
        for (i in 0 until minOf(before, grid.size)) {
            val line = grid[i].joinToString(" ")
            re.find(line)?.let { return it.groupValues[1].takeLast(4) }
        }
        return ""
    }

    // ------------------------------------------------------------------ cells

    /** Picks whichever delimiter appears most consistently across the file. */
    private fun sniffDelimiter(lines: List<String>): Char {
        val candidates = listOf('\t', ',', ';', '|')
        val sample = lines.filter { it.isNotBlank() }.take(80)
        return candidates.maxByOrNull { d -> sample.sumOf { line -> line.count { it == d } } }
            ?.takeIf { d -> sample.any { it.contains(d) } }
            ?: ','
    }

    /** Splits one line, honouring double-quoted fields that may themselves contain the delimiter. */
    private fun splitLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    out.add(sb.toString().trim()); sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }

    /**
     * Reads a money cell. Handles thousands separators, a trailing Dr/Cr marker, accounting
     * parentheses for negatives, and the various blank placeholders banks use.
     */
    fun amount(raw: String?): Double? {
        var s = raw?.trim() ?: return null
        if (s.isEmpty() || s == "-" || s == "--" || s.equals("nil", true)) return null

        val negative = s.startsWith("(") && s.endsWith(")")
        s = s.trim('(', ')')
        s = s.replace(Regex("""(?i)\s*(dr|cr)\.?\s*$"""), "")
        s = s.replace(",", "").replace("₹", "").replace(Regex("""(?i)\bINR\b"""), "").replace(" ", "")

        val v = s.toDoubleOrNull() ?: return null
        if (v == 0.0) return null // an empty side of the ledger, not a zero-rupee transaction
        return if (negative) -v else v
    }

    // ------------------------------------------------------------------ dates

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val D_ALPHA = Regex("""(\d{1,2})[\s\-/]*([A-Za-z]{3,})[\s\-/]*(\d{2,4})""")
    private val D_NUM = Regex("""(\d{1,2})[\-/.](\d{1,2})[\-/.](\d{2,4})""")
    private val D_ISO = Regex("""(\d{4})[\-/.](\d{1,2})[\-/.](\d{1,2})""")

    /**
     * Accepts the shapes statements actually use: "1 Aug 2026", "01-08-2026", "01/08/26",
     * "2026-08-01". Day-first is assumed for the ambiguous numeric form, which is correct for
     * Indian statements.
     *
     * @return midnight on that day, or null if the cell is not a date
     */
    fun parseDate(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        D_ISO.find(s)?.let { m ->
            return build(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt())
        }
        D_ALPHA.find(s)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[2].take(3).lowercase(Locale.ROOT))
            if (month >= 0) {
                val day = m.groupValues[1].toInt()
                if (day in 1..31) return build(year(m.groupValues[3].toInt()), month, day)
            }
        }
        D_NUM.find(s)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt() - 1
            if (day in 1..31 && month in 0..11) return build(year(m.groupValues[3].toInt()), month, day)
        }
        return excelSerial(s)
    }

    /**
     * Spreadsheets store dates as a day count, not text, so a date cell read straight out of an
     * .xlsx arrives as something like "46234". Only values inside a plausible calendar range are
     * accepted, so a reference number in the date column is not silently turned into a date.
     */
    private fun excelSerial(s: String): Long? {
        val days = s.toDoubleOrNull() ?: return null
        if (days < 20000 || days > 60000) return null // roughly 1954 to 2064

        // Excel's epoch is 30 Dec 1899, which absorbs its deliberate 1900-leap-year bug.
        val epoch = Calendar.getInstance().apply {
            clear()
            set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
        }
        epoch.add(Calendar.DAY_OF_YEAR, days.toInt())
        return epoch.timeInMillis
    }

    private fun year(y: Int): Int = when {
        y >= 1000 -> y
        y >= 70 -> 1900 + y
        else -> 2000 + y
    }

    private fun build(year: Int, month: Int, day: Int): Long? {
        if (year < 1990 || year > 2100) return null
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis
    }
}
