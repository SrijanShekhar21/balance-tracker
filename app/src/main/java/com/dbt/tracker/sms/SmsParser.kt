package com.dbt.tracker.sms

import com.dbt.tracker.data.Channel
import com.dbt.tracker.data.Txn
import java.util.Calendar
import java.util.Locale

/**
 * Turns a bank SMS into a [ParsedSms], or returns null if the message is not a completed
 * transaction.
 *
 * Written against the formats SBI actually sends, which are not consistent with each other:
 *
 *   Dear UPI user A/C X8420 debited by 150.0 on date 28Aug26 trf to ZOMATO Refno 5228394 -SBI
 *   Your A/c XX8420 is debited by Rs.1200.00 on 28-08-26 at BIGBAZAAR. Avl Bal Rs 15234.56
 *   Dear Customer, your a/c no. XX8420 is credited by Rs.5000.00 on 28/08/26 (IMPS Ref 9012)
 *   Rs.2000 withdrawn from A/c X8420 at SBI ATM on 28Aug26. Avl Bal Rs 13234.56
 *
 * The amount can sit before or after the direction word, the currency prefix is optional, and
 * only some messages carry a balance. Rather than enumerate templates, each field is extracted
 * independently and the result is accepted only if it is structurally complete -- see [parse].
 */
object SmsParser {

    data class ParsedSms(
        val amount: Double,
        val isCredit: Boolean,
        val merchant: String,
        val account: String,
        val balanceAfter: Double?,
        val refNo: String?,
        val channel: String,
        val ts: Long
    )

    private const val NUM = """\d[\d,]*(?:\.\d{1,2})?"""
    private const val CUR = """(?:rs\.?|inr|₹)"""

    private val CREDIT_WORDS = setOf("credited", "credit", "received", "deposit", "deposited")

    /**
     * Phrases that mean "no money actually moved". Deliberately narrow: broad terms like a bare
     * "offer" or any URL would also reject genuine alerts, since banks append helpline links and
     * merchant names contain arbitrary words. The structural checks in [parse] do the heavy work.
     */
    private val REJECT = listOf(
        "otp", "one time password", "do not share", "never share",
        "will be debited", "will be credited", "would be debited", "shall be debited",
        "is due", "due on", "due date", "amount due", "payment due", "bill is due",
        "has failed", "failed", "declined", "unsuccessful", "not processed", "reversed back",
        "has requested", "is requesting", "collect request", "payment request", "requesting money",
        "mandate", "autopay", "standing instruction",
        "pre-approved", "pre approved", "special offer", "exclusive offer", "apply now",
        "eligible for", "avail a loan", "loan offer",
        "insufficient", "minimum balance", "min bal",
        "cheque book", "kyc", "aadhaar", "nominee", "mini statement",
        "dear customer, greetings"
    )

    private const val DIR =
        """debited|credited|withdrawn|deducted|deposited|deposit|debit|credit|paid|spent|received"""

    /**
     * Anchored on the currency symbol, so a few filler words may sit between the verb and the
     * figure. This is what reads "has a credit by transfer of Rs.500" correctly.
     */
    private val RE_AMOUNT_CUR = Regex(
        """($DIR)\s*(?:\S+\s+){0,3}?$CUR\s*($NUM)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * For messages that omit the currency ("debited by 150.0"). No filler words are tolerated
     * here: without a currency anchor, skipping ahead would happily match the 28 in "on 28Aug26".
     */
    private val RE_AMOUNT_BARE = Regex(
        """($DIR)\s*(?:by|with|for|of|:|-)\s*($NUM)""",
        RegexOption.IGNORE_CASE
    )

    /** Amount printed ahead of the verb: "Rs.2000 withdrawn from A/c X8420". */
    private val RE_AMOUNT_BEFORE = Regex(
        """$CUR\s*($NUM)\s*(?:\S+\s+){0,3}?($DIR)""",
        RegexOption.IGNORE_CASE
    )

    private val RE_BALANCE = Regex(
        """(?:avl|avail|available|avbl|a/c|clear|closing|updated|total)?[\s.]*bal(?:ance)?\b[^\d]{0,14}($NUM)""",
        RegexOption.IGNORE_CASE
    )
    private val RE_ACCOUNT = Regex(
        """(?:a/?c|acct|account)\s*(?:no\.?|number)?\s*[:.\-]?\s*[xX*]*\s*(\d{3,})""",
        RegexOption.IGNORE_CASE
    )
    private val RE_REF = Regex(
        """(?:ref(?:erence)?\s*(?:no\.?|number|#)?|refno|rrn|utr|txn\s*id)\s*[:.\-]?\s*(\w*\d{6,}\w*)""",
        RegexOption.IGNORE_CASE
    )

    /** Ordered most-specific first; the first pattern that yields a usable name wins. */
    private val MERCHANT_PATTERNS = listOf(
        Regex("""tr(?:f|ansfer)\s+to\s+(.{2,45}?)\s+ref""", RegexOption.IGNORE_CASE),
        Regex("""tr(?:f|ansfer)\s+from\s+(.{2,45}?)\s+ref""", RegexOption.IGNORE_CASE),
        Regex("""tr(?:f|ansfer)\s+to\s+(.{2,45}?)(?:\.|,|\s+on\s)""", RegexOption.IGNORE_CASE),
        Regex("""tr(?:f|ansfer)\s+from\s+(.{2,45}?)(?:\.|,|\s+on\s)""", RegexOption.IGNORE_CASE),
        Regex("""(?:deposit|credited|debited)\s+by\s+(?!rs|inr|transfer)(.{2,45}?)(?:\.|,|\s+ref|\s+on\s)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+vpa\s+(\S{2,45})""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+(.{2,45}?)(?:\.|,|\s+on\s|\s+avl)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+([A-Za-z][^.,]{1,44}?)\s+(?:refno|ref|on)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([A-Za-z][^.,]{1,44}?)\s+(?:refno|ref|on)\b""", RegexOption.IGNORE_CASE)
    )

    private val RE_DATE_ALPHA = Regex("""(\d{1,2})[-/\s]?([A-Za-z]{3})[-/\s]?(\d{2,4})""")
    private val RE_DATE_NUM = Regex("""(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})""")
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Senders worth parsing at all. Bank and wallet short-codes, not people. */
    fun senderLooksFinancial(sender: String): Boolean {
        val s = sender.uppercase()
        if (s.any { it.isDigit() } && s.length >= 10) return false // a real phone number
        return listOf(
            "SBI", "SBIINB", "SBIUPI", "SBIPSG", "SBICRD", "ATMSBI",
            "HDFC", "ICICI", "AXIS", "KOTAK", "PNB", "BOB", "CANARA", "UNION", "IDFC", "YESBNK",
            "PHONEPE", "PHNPE", "GPAY", "GOOGLE", "PAYTM", "BHIM", "NPCI", "UPI", "AMZNPAY"
        ).any { s.contains(it) }
    }

    fun isSbi(sender: String): Boolean = sender.uppercase().contains("SBI")

    /**
     * @param body the SMS text
     * @param receivedAt delivery time, used as the timestamp and as the anchor for the
     *        date printed inside the message
     */
    fun parse(body: String, receivedAt: Long): ParsedSms? {
        if (body.isBlank()) return null
        val text = body.replace(Regex("""\s+"""), " ").trim()
        val lower = text.lowercase(Locale.ROOT)

        if (REJECT.any { lower.contains(it) }) return null

        // Balance is pulled out first and then blanked, so that "Avl Bal Rs 15234.56" can never
        // be mistaken for the transaction amount by the fallback patterns below.
        val balMatch = RE_BALANCE.find(text)
        val balance = balMatch?.groupValues?.get(1)?.let(::toAmount)
        val work = if (balMatch != null) text.removeRange(balMatch.range) else text

        val (amount, dirWord) = extractAmount(work) ?: return null
        if (amount <= 0.0 || amount > 100_000_000.0) return null

        val account = RE_ACCOUNT.find(text)?.groupValues?.get(1)?.takeLast(4) ?: ""
        val refNo = RE_REF.find(text)?.groupValues?.get(1)

        // A completed transaction always identifies itself: an account, a reference number, or a
        // resulting balance. Marketing messages quote amounts but carry none of these.
        if (account.isBlank() && refNo == null && balance == null) return null

        val isCredit = dirWord.lowercase(Locale.ROOT) in CREDIT_WORDS
        val channel = detectChannel(lower)
        // Never anchor the bank balance to a card's available limit.
        val usableBalance = if (channel == Channel.CREDIT_CARD) null else balance
        val merchant = extractMerchant(work, channel, isCredit)

        return ParsedSms(
            amount = amount,
            isCredit = isCredit,
            merchant = merchant,
            account = account,
            balanceAfter = usableBalance,
            refNo = refNo,
            channel = channel,
            ts = resolveTimestamp(text, receivedAt)
        )
    }

    fun toTxn(p: ParsedSms, category: String, raw: String): Txn = Txn(
        ts = p.ts,
        amount = p.amount,
        isCredit = p.isCredit,
        merchant = p.merchant,
        category = category,
        account = p.account,
        balanceAfter = p.balanceAfter,
        refNo = p.refNo,
        channel = p.channel,
        raw = raw
    )

    // ------------------------------------------------------------------ fields

    /**
     * @return amount paired with the direction word that justified it, most reliable
     *         pattern first.
     */
    private fun extractAmount(work: String): Pair<Double, String>? {
        for (re in listOf(RE_AMOUNT_CUR, RE_AMOUNT_BARE)) {
            re.find(work)?.let { m ->
                val amt = toAmount(m.groupValues[2])
                if (amt != null) return amt to m.groupValues[1]
            }
        }
        RE_AMOUNT_BEFORE.find(work)?.let { m ->
            val amt = toAmount(m.groupValues[1])
            if (amt != null) return amt to m.groupValues[2]
        }
        return null
    }

    private fun toAmount(s: String): Double? =
        s.replace(",", "").trim().toDoubleOrNull()

    /**
     * A credit card statement quotes an available *limit*, which is not money you hold.
     * Detecting these keeps card spending in the report while keeping it out of the balance.
     */
    private fun isCreditCard(lower: String): Boolean = listOf(
        "credit card", "sbi card", "card ending", "cc ending", "avl limit",
        "available limit", "credit limit"
    ).any { lower.contains(it) }

    private fun detectChannel(lower: String): String = when {
        isCreditCard(lower) -> Channel.CREDIT_CARD
        lower.contains("atm") || lower.contains("withdrawn") || lower.contains("cash wdl") -> Channel.ATM
        lower.contains("upi") || lower.contains("vpa") || lower.contains("@") -> Channel.UPI
        lower.contains("card") || lower.contains("pos ") || lower.contains("swipe") -> Channel.CARD
        lower.contains("neft") || lower.contains("imps") || lower.contains("rtgs") -> Channel.BANK
        else -> Channel.OTHER
    }

    private fun extractMerchant(work: String, channel: String, isCredit: Boolean): String {
        for (re in MERCHANT_PATTERNS) {
            val raw = re.find(work)?.groupValues?.get(1) ?: continue
            val cleaned = cleanMerchant(raw)
            if (cleaned.isNotBlank()) return cleaned
        }
        return when {
            channel == Channel.ATM -> "ATM Withdrawal"
            isCredit -> "Credit"
            else -> "Unknown"
        }
    }

    private fun cleanMerchant(input: String): String {
        var m = input.trim()

        // A UPI handle identifies the payee by its local part: "swiggy@ybl" -> "swiggy".
        if (m.contains("@")) m = m.substringBefore("@")

        m = m.replace(Regex("""(?i)\bref\s*(no\.?)?\s*\d+"""), " ")
            .replace(Regex("""(?i)\b(refno|rrn|utr|upi|txn|id)\b"""), " ")
            .replace(Regex("""(?i)-\s*sbi\b"""), " ")
            .replace(Regex("""\d{6,}"""), " ")
            .replace(Regex("""[*_/\\|]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('-', '.', ',', ':', ';')
            .trim()

        if (m.length < 2) return ""
        if (m.length > 38) m = m.take(38).trim()

        // Bank feeds shout; sentence-case anything fully capitalised so the UI reads calmly.
        if (m == m.uppercase(Locale.ROOT) && m.any { it.isLetter() }) {
            m = m.lowercase(Locale.ROOT).split(" ").joinToString(" ") { w ->
                if (w.isEmpty()) w else w.replaceFirstChar { it.uppercase() }
            }
        }
        return m
    }

    /**
     * Prefers the date printed in the message over the delivery time, which matters when a
     * late-night payment is delivered after midnight and would otherwise land on the wrong day.
     * Time-of-day comes from delivery since the text never carries one.
     */
    private fun resolveTimestamp(text: String, receivedAt: Long): Long {
        val parsed = parseDate(text) ?: return receivedAt
        val recv = Calendar.getInstance().apply { timeInMillis = receivedAt }

        val sameDay = parsed.get(Calendar.YEAR) == recv.get(Calendar.YEAR) &&
            parsed.get(Calendar.DAY_OF_YEAR) == recv.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return receivedAt

        val deltaDays = Math.abs(parsed.timeInMillis - receivedAt) / 86_400_000.0
        if (deltaDays > 30) return receivedAt // misparse; trust the delivery time

        // Keep it inside the printed day, at the same clock time where that is possible.
        parsed.set(Calendar.HOUR_OF_DAY, recv.get(Calendar.HOUR_OF_DAY))
        parsed.set(Calendar.MINUTE, recv.get(Calendar.MINUTE))
        return parsed.timeInMillis
    }

    private fun parseDate(text: String): Calendar? {
        RE_DATE_ALPHA.find(text)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[2].lowercase(Locale.ROOT))
            if (month >= 0) {
                val day = m.groupValues[1].toIntOrNull() ?: return@let
                val year = normaliseYear(m.groupValues[3].toIntOrNull() ?: return@let)
                return calendarOf(year, month, day)
            }
        }
        RE_DATE_NUM.find(text)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            val month = (m.groupValues[2].toIntOrNull() ?: return@let) - 1
            val year = normaliseYear(m.groupValues[3].toIntOrNull() ?: return@let)
            if (day in 1..31 && month in 0..11) return calendarOf(year, month, day)
        }
        return null
    }

    private fun normaliseYear(y: Int): Int = if (y < 100) 2000 + y else y

    private fun calendarOf(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
